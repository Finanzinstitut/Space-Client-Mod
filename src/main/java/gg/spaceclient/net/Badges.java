package gg.spaceclient.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which mark a particular player wears in front of their name.
 *
 * The client already drew one badge for everybody on the presence roster. This
 * is the other half: a short, curated list saying that these specific people
 * get a different one. Presence still decides whether any badge is drawn at
 * all - the rank only decides which.
 *
 * Kept as a file in the repository rather than on the worker, because the
 * worker answers one question ("who is playing right now") and this is a
 * different one that changes at a completely different rate: membership of this
 * list changes a few times a year, and the answer is identical for everybody.
 * A file on a branch is exactly the right shape for that, and it costs no
 * storage operations at all.
 *
 * Read twice: once from inside the jar, so a first launch with no network still
 * has an answer, and then from the branch, so a change takes a commit rather
 * than a release.
 */
public final class Badges {

    /** The four marks the badge font carries, at their private use code points. */
    public enum Rank {
        STANDARD("\uE000"),
        DEV("\uE001"),
        OWNER("\uE002"),
        VIP("\uE003");

        private final String glyph;

        Rank(String glyph) { this.glyph = glyph; }

        public String glyph() { return glyph; }

        static Rank parse(String raw) {
            if (raw == null) return null;
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "dev", "developer" -> DEV;
                case "owner" -> OWNER;
                case "vip" -> VIP;
                case "standard", "default", "" -> STANDARD;
                default -> null;
            };
        }
    }

    /** Where the living copy of the list is read from. */
    private static final String SOURCE =
            "https://raw.githubusercontent.com/Finanzinstitut/Space-Client-Mod/main/"
            + "src/main/resources/assets/spaceclient/badges.json";

    /**
     * How often the list is re-read.
     *
     * Five minutes, which is the floor worth using: the file is served from a
     * cache that holds it for about that long, so asking more often mostly
     * re-fetches the same bytes. It was twenty, and twenty is the wrong number
     * for somebody who has just changed the list and wants to see it.
     */
    private static final long REFRESH_MS = 5 * 60 * 1000L;

    private static final Map<UUID, Rank> byUuid = new ConcurrentHashMap<>();
    private static final Map<String, Rank> byName = new ConcurrentHashMap<>();

    private static volatile long nextRefresh = 0L;
    private static volatile boolean bundledLoaded = false;
    private static volatile String status = "not started";

    private Badges() {}

    /** What the list is doing, for the diagnostics page. */
    public static String status() {
        return byUuid.size() + " by id, " + byName.size() + " by name (" + status + ")";
    }

    /**
     * The mark this player wears, or null for the ordinary one.
     *
     * The uuid is asked first and the name only after, because a name is
     * something anybody can take. Somebody who renames themselves to a name on
     * this list gets that mark only while the entry has no uuid against it.
     */
    public static Rank rankFor(UUID uuid, String name) {
        if (uuid != null) {
            Rank byId = byUuid.get(uuid);
            if (byId != null) return byId;
        }
        if (name != null && !name.isEmpty()) {
            return byName.get(name.toLowerCase(Locale.ROOT));
        }
        return null;
    }

    /**
     * Called from the client tick. Loads the bundled copy once, then refreshes
     * from the branch on its own schedule.
     */
    /**
     * Fetches again on the next tick, whatever the clock says.
     *
     * Called on joining a world: that is the moment the list is about to be
     * used, and waiting out the rest of an interval there would mean playing
     * for minutes with yesterday's answer.
     */
    public static void refreshSoon() {
        nextRefresh = 0L;
    }

    public static void tick() {
        if (!bundledLoaded) {
            bundledLoaded = true;
            loadBundled();
        }

        long now = System.currentTimeMillis();
        if (now < nextRefresh) return;
        // Set before the call rather than after, so a request that takes a
        // while does not start a second one behind it on the next tick.
        nextRefresh = now + REFRESH_MS;

        CompletableFuture.runAsync(Badges::refresh);
    }

    private static void loadBundled() {
        try (InputStream in = Badges.class.getResourceAsStream(
                "/assets/spaceclient/badges.json")) {
            if (in == null) {
                status = "no bundled copy";
                return;
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            int read = apply(text);
            status = read < 0 ? "bundled copy unreadable" : "bundled (" + read + ")";

        } catch (Throwable t) {
            status = "bundled copy failed: " + t.getMessage();
        }
    }

    private static void refresh() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SOURCE))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                // Keep whatever is already loaded. A list that vanishes because
                // one request failed is worse than a list that is a day old.
                status = "refused (" + response.statusCode() + ")";
                return;
            }

            int read = apply(response.body());
            status = read < 0 ? "branch copy unreadable" : "live (" + read + ")";

        } catch (Throwable t) {
            status = "offline: " + t.getMessage();
        }
    }

    /**
     * Replaces the list with what this document says.
     *
     * @return how many entries were taken, or -1 when the document made no sense.
     */
    private static int apply(String text) {
        try {
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            if (!root.has("badges")) return -1;

            JsonArray entries = root.getAsJsonArray("badges");

            // Built to one side and swapped in, so a reader during a refresh
            // sees either the old list or the new one and never half of each.
            Map<UUID, Rank> ids = new ConcurrentHashMap<>();
            Map<String, Rank> names = new ConcurrentHashMap<>();

            for (int i = 0; i < entries.size(); i++) {
                try {
                    JsonObject entry = entries.get(i).getAsJsonObject();

                    Rank rank = Rank.parse(entry.has("rank")
                            ? entry.get("rank").getAsString() : null);
                    if (rank == null) continue;

                    if (entry.has("uuid")) {
                        String raw = entry.get("uuid").getAsString().trim();
                        if (!raw.isEmpty()) ids.put(UUID.fromString(dashed(raw)), rank);
                    }
                    if (entry.has("name")) {
                        String raw = entry.get("name").getAsString().trim();
                        if (!raw.isEmpty()) names.put(raw.toLowerCase(Locale.ROOT), rank);
                    }

                } catch (Throwable ignored) {
                    // One malformed entry must not cost the rest of the list
                }
            }

            byUuid.clear();
            byUuid.putAll(ids);
            byName.clear();
            byName.putAll(names);

            return ids.size() + names.size();

        } catch (Throwable t) {
            return -1;
        }
    }

    /** Accepts a uuid with or without its dashes, since both get typed. */
    private static String dashed(String raw) {
        if (raw.length() != 32) return raw;
        return raw.substring(0, 8) + "-" + raw.substring(8, 12) + "-"
                + raw.substring(12, 16) + "-" + raw.substring(16, 20) + "-"
                + raw.substring(20);
    }
}
