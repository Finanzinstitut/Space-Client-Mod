package gg.spaceclient.cosmetics;

import gg.spaceclient.SpaceClient;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Knows what every visible player is wearing.
 *
 * The shop endpoint answers for one account at a time and needs a session
 * handshake; this needs the opposite - many accounts at once, for players who
 * are not us and whose sessions we could never prove. /shop/worn exists for
 * exactly that and is deliberately unauthenticated, because what someone wears
 * is public the moment they walk into view anyway.
 *
 * Lookups are batched and cached rather than made per player per frame. A
 * render path that blocks on the network is a render path that stutters, so
 * drawing only ever reads the cache and never waits for it.
 */
public final class CosmeticsManager {

    private static final String BASE =
            "https://spaceclient-badges.spaceclient-finanzinstitut.workers.dev";

    /** Long enough that changing a cape is noticed, short enough to be quiet. */
    private static final long REFRESH_INTERVAL_MS = 30_000L;

    /** Nothing is retried this soon after a failure, so an outage stays cheap. */
    private static final long BACKOFF_MS = 60_000L;

    /** uuid -> slot -> item id, as last told by the server. */
    private static final Map<UUID, Map<String, String>> WORN = new ConcurrentHashMap<>();

    /** Cape textures, resolved once each rather than per frame. */
    private static final Map<String, Identifier> CAPE_TEXTURES = new ConcurrentHashMap<>();

    /** Capes that exist as a numbered set of frames rather than one image. */
    private static final java.util.Set<String> ANIMATED = java.util.Set.of(
            "cape_aurora", "cape_pulsar", "cape_rainbow", "cape_matrix",
            "cape_lava", "cape_tide", "cape_comet", "cape_ember",
            "cape_neon", "cape_scan", "cape_snowfall", "cape_spark",
            "wings_phoenix", "wings_void", "wings_prism", "wings_frost");

    /** How many frames each animated cape ships, and how long each is held. */
    private static final int ANIMATION_FRAMES = 12;
    private static final long FRAME_MS = 80L;

    private static volatile long lastRefresh = 0L;
    private static volatile long failedUntil = 0L;
    private static volatile boolean inFlight = false;

    private static HttpClient http;

    private CosmeticsManager() {}

    private static synchronized HttpClient http() {
        if (http == null) {
            http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
        }
        return http;
    }

    /**
     * The texture for a cape item, or null if the item is not a known cape.
     *
     * Unknown ids return null rather than a placeholder: a cape the client has
     * no texture for should simply not draw, so an older client meeting a newer
     * catalogue degrades quietly instead of showing everyone a missing texture.
     */
    public static Identifier capeTexture(String itemId) {
        return cosmeticTexture(itemId, "cape_");
    }

    /** The wings texture for an item, or null if it is not a known wing. */
    public static Identifier wingsTexture(String itemId) {
        return cosmeticTexture(itemId, "wings_");
    }

    /** The wings this player is wearing, or null. Cache only - never blocks. */
    public static Identifier wingsFor(UUID uuid) {
        if (uuid == null) return null;
        Map<String, String> worn = WORN.get(uuid);
        if (worn == null) return null;
        return wingsTexture(worn.get("wings"));
    }

    private static Identifier cosmeticTexture(String itemId, String prefix) {
        if (itemId == null || itemId.isEmpty()) return null;
        if (!itemId.startsWith(prefix)) return null;

        // Animated capes are many files cycled here rather than one file with
        // an animation marker. Vanilla only animates atlas sprites, and a cape
        // is a standalone texture, so the marker is simply ignored - the whole
        // frame strip ends up stretched across the cloth.
        String path = ANIMATED.contains(itemId)
                ? itemId + "_" + frameIndex()
                : itemId;

        // Bare id on purpose: ClientAsset.ResourceTexture puts "textures/" in
        // front and ".png" behind it, so spelling those here would double them.
        return CAPE_TEXTURES.computeIfAbsent(path, key ->
                Identifier.fromNamespaceAndPath("spaceclient", "cosmetics/" + key));
    }

    /** Which frame the animated capes are on, shared so everyone stays in step. */
    private static int frameIndex() {
        return (int) ((System.currentTimeMillis() / FRAME_MS) % ANIMATION_FRAMES);
    }

    /** The cape this player is wearing, or null. Cache only - never blocks. */
    public static Identifier capeFor(UUID uuid) {
        if (uuid == null) return null;
        Map<String, String> worn = WORN.get(uuid);
        if (worn == null) return null;
        return capeTexture(worn.get("cape"));
    }

    /** Everything this player is wearing, keyed by slot. Never null. */
    public static Map<String, String> wornBy(UUID uuid) {
        Map<String, String> worn = uuid == null ? null : WORN.get(uuid);
        return worn == null ? Map.of() : worn;
    }

    /**
     * Called every client tick. Refreshes on its own schedule.
     *
     * The tick thread must not wait on a socket, so the request runs elsewhere
     * and this returns immediately.
     */
    public static void tick() {
        long now = System.currentTimeMillis();
        if (inFlight || now < failedUntil || now - lastRefresh < REFRESH_INTERVAL_MS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        List<UUID> ids = new ArrayList<>();
        try {
            // entitiesForRendering is what the hitbox renderer already walks on
            // this version, so it is known to exist here; ClientLevel.players()
            // is not, and a wrong guess costs a build.
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (!(entity instanceof Player)) continue;
                UUID id = entity.getUUID();
                if (id != null) ids.add(id);
            }
        } catch (Throwable t) {
            // A world in an odd state must not stop the game
            return;
        }

        if (ids.isEmpty()) return;

        lastRefresh = now;
        inFlight = true;
        Thread.ofVirtual().name("spaceclient-cosmetics").start(() -> fetch(ids));
    }

    /**
     * Marks the cache due, so the next tick refetches instead of waiting out
     * the interval. Called after equipping: the change is the player's own and
     * they are looking straight at it, so half a minute of nothing reads as a
     * bug rather than as a cache.
     */
    public static void refreshSoon() {
        lastRefresh = 0L;
        failedUntil = 0L;
    }

    /** Drops everything on disconnect, so a rejoin does not show stale capes. */
    public static void clear() {
        WORN.clear();
        lastRefresh = 0L;
    }

    private static void fetch(List<UUID> ids) {
        try {
            StringBuilder body = new StringBuilder("{\"uuids\":[");
            for (int i = 0; i < ids.size(); i++) {
                if (i > 0) body.append(',');
                body.append('"').append(ids.get(i)).append('"');
            }
            body.append("]}");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/shop/worn"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response =
                    http().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                SpaceClient.LOGGER.warn("Cosmetics lookup failed ({})", response.statusCode());
                failedUntil = System.currentTimeMillis() + BACKOFF_MS;
                return;
            }

            apply(parse(response.body()));

        } catch (Throwable t) {
            SpaceClient.LOGGER.warn("Cosmetics lookup failed", t);
            failedUntil = System.currentTimeMillis() + BACKOFF_MS;
        } finally {
            inFlight = false;
        }
    }

    /**
     * Replaces the cache wholesale rather than merging.
     *
     * A player who took a cape off appears in the response with nothing, or not
     * at all. Merging would leave the old cape in place forever, so absence has
     * to mean absence.
     */
    private static void apply(Map<UUID, Map<String, String>> fresh) {
        WORN.keySet().retainAll(fresh.keySet());
        WORN.putAll(fresh);
    }

    /**
     * Reads {"worn":{"<uuid>":{"cape":"cape_nebula"}}} without a JSON library.
     *
     * The mod has no JSON dependency and the shape here is fixed and small, so
     * a scanner is cheaper than adding one. Anything unexpected yields an empty
     * map, which simply means nobody is wearing anything.
     */
    private static Map<UUID, Map<String, String>> parse(String json) {
        Map<UUID, Map<String, String>> out = new HashMap<>();
        if (json == null) return out;

        int wornAt = json.indexOf("\"worn\"");
        if (wornAt < 0) return out;

        int i = json.indexOf('{', wornAt);
        if (i < 0) return out;
        i++;

        while (i < json.length()) {
            int keyStart = json.indexOf('"', i);
            if (keyStart < 0) break;
            int keyEnd = json.indexOf('"', keyStart + 1);
            if (keyEnd < 0) break;

            String rawUuid = json.substring(keyStart + 1, keyEnd);

            int objStart = json.indexOf('{', keyEnd);
            if (objStart < 0) break;
            int objEnd = json.indexOf('}', objStart);
            if (objEnd < 0) break;

            Map<String, String> slots = new HashMap<>();
            for (String pair : json.substring(objStart + 1, objEnd).split(",")) {
                String[] parts = pair.split(":");
                if (parts.length != 2) continue;
                slots.put(unquote(parts[0]), unquote(parts[1]));
            }

            try {
                if (!slots.isEmpty()) out.put(UUID.fromString(rawUuid), slots);
            } catch (IllegalArgumentException ignored) {
                // Not a uuid, so not a player we can draw on
            }

            i = objEnd + 1;
            // A closing brace right after this one ends the worn object
            int next = json.indexOf('"', i);
            int close = json.indexOf('}', i);
            if (next < 0 || (close >= 0 && close < next)) break;
        }

        return out;
    }

    private static String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }
}
