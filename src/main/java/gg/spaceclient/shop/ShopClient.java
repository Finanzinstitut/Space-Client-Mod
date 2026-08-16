package gg.spaceclient.shop;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.session.SessionManager;

import net.minecraft.client.Minecraft;

import java.net.URI;
import java.security.SecureRandom;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Talks to the Galaxy Points service.
 *
 * Nothing about the balance or the catalogue is decided here. The client asks,
 * the server answers, and a purchase is a request that the server may refuse.
 * That split matters because these points are meant to cost money: a balance
 * kept in a config file is a number the owner can edit.
 *
 * Identity is proved with Mojang's server handshake: the game authenticates to
 * Mojang directly and the shop server merely asks Mojang to confirm it. The
 * session token never leaves this machine, and the server still knows exactly
 * who is asking rather than trusting a uuid in the body.
 */
public final class ShopClient {
    private static final String BASE = "https://spaceclient-badges.spaceclient-finanzinstitut.workers.dev";

    private static volatile int balance = 0;
    private static volatile List<ShopItem> catalogue = List.of();
    private static volatile Map<String, String> equipped = new HashMap<>();
    private static volatile String status = "not loaded";
    private static volatile boolean busy = false;

    /**
     * When a session refresh was last triggered from here.
     *
     * A rejected token used to start one on every attempt, so a screen that
     * retried in a loop hammered the login chain along with it. One attempt per
     * minute is plenty: if the first refresh did not help, the second will not
     * either.
     */
    private static volatile long lastSessionRetry = 0;

    /** One generator, reseeded by the platform, rather than one per request. */
    private static final SecureRandom RANDOM = new SecureRandom();

    public static int balance() { return balance; }
    public static List<ShopItem> catalogue() { return catalogue; }
    public static Map<String, String> equipped() { return equipped; }
    public static String status() { return status; }
    public static boolean isBusy() { return busy; }

    private static HttpClient http() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    }

    /** The token the game holds. It is proof for Mojang, and never leaves this machine. */
    private static String token() {
        try {
            var user = Minecraft.getInstance().getUser();
            for (String accessor : new String[]{"getAccessToken", "accessToken"}) {
                try {
                    Object value = user.getClass().getMethod(accessor).invoke(user);
                    if (value instanceof String text && !text.isEmpty()) return text;
                } catch (Exception ignored) {
                    // Try the next accessor
                }
            }
        } catch (Throwable ignored) {
            // Falls through to null
        }
        return null;
    }

    /** The profile id the session belongs to, undashed, as Mojang wants it. */
    private static String profileId() {
        try {
            var user = Minecraft.getInstance().getUser();
            for (String accessor : new String[]{"getProfileId", "getUuid", "getUUID"}) {
                try {
                    Object value = user.getClass().getMethod(accessor).invoke(user);
                    if (value instanceof UUID id) return id.toString().replace("-", "");
                    if (value instanceof String text && !text.isEmpty()) {
                        return text.replace("-", "");
                    }
                } catch (Exception ignored) {
                    // Try the next accessor
                }
            }
        } catch (Throwable ignored) {
            // Falls through to null
        }
        return null;
    }

    /**
     * Proves to Mojang who we are, and hands back the id the server can check.
     *
     * The shop used to send the session token itself, and the server asked
     * Mojang whose it was. Mojang serves that account API an Akamai block page
     * to anything running in a datacentre, which a Cloudflare worker always is,
     * so the answer was a 403 that had nothing to do with the token.
     *
     * This is the handshake Mojang built for servers instead. The game tells
     * Mojang "I am about to join a server called <id>", and the worker later
     * asks "did you just see that?". The token stays here, which is where it
     * belongs - the shop server never had any business holding it.
     *
     * The id is single use and short lived, so a fresh one is minted per call.
     */
    private static String handshake() {
        String token = token();
        String profile = profileId();
        if (token == null || profile == null) return null;

        byte[] noise = new byte[16];
        RANDOM.nextBytes(noise);
        StringBuilder serverId = new StringBuilder(32);
        for (byte b : noise) serverId.append(String.format("%02x", b));

        String body = "{\"accessToken\":\"" + token + "\","
                + "\"selectedProfile\":\"" + profile + "\","
                + "\"serverId\":\"" + serverId + "\"}";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://sessionserver.mojang.com/session/minecraft/join"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response =
                    http().send(request, HttpResponse.BodyHandlers.ofString());

            // Mojang answers a good handshake with 204 and nothing else
            if (response.statusCode() == 204 || response.statusCode() == 200) {
                return serverId.toString();
            }

            SpaceClient.LOGGER.warn("Mojang refused the handshake ({}): {}",
                    response.statusCode(), response.body());
            return null;

        } catch (Throwable t) {
            SpaceClient.LOGGER.warn("Could not reach Mojang for the handshake", t);
            return null;
        }
    }

    /** The name the game plays as, which the handshake is tied to. */
    private static String playerName() {
        try {
            return Minecraft.getInstance().getUser().getName();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static CompletableFuture<Void> refresh() {
        return CompletableFuture.runAsync(() -> {
            String name = playerName();
            if (token() == null || name == null) {
                status = "sign in with Microsoft to use the shop";
                return;
            }
            busy = true;
            try {
                String serverId = handshake();
                if (serverId == null) {
                    status = "Mojang would not confirm this session - is the game online?";
                    return;
                }

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE + "/shop"))
                        .header("X-Space-Name", name)
                        .header("X-Space-Server", serverId)
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

                HttpResponse<String> response =
                        http().send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 401) {
                    // The session may simply have gone stale while the game ran
                    long now = System.currentTimeMillis();
                    if (now - lastSessionRetry > 60_000) {
                        lastSessionRetry = now;
                        status = "session rejected - refreshing, try again in a moment";
                        SessionManager.refreshCurrent();
                    } else {
                        // The server now says why, and that reason is far more
                        // use than the fact that it said no
                        String reason = response.body();
                        try {
                            JsonObject error = JsonParser.parseString(reason).getAsJsonObject();
                            if (error.has("error")) reason = error.get("error").getAsString();
                        } catch (Throwable ignored) {
                            // Keep the raw body if it was not JSON
                        }
                        status = reason + "  (playing as "
                                + Minecraft.getInstance().getUser().getName() + ")";
                    }
                    return;
                }
                if (response.statusCode() != 200) {
                    // The server's own words beat a bare status code
                    String body = response.body();
                    status = "shop unavailable (" + response.statusCode() + ")"
                            + (body != null && !body.isBlank()
                                    ? ": " + body.substring(0, Math.min(120, body.length()))
                                    : "");
                    return;
                }

                parse(JsonParser.parseString(response.body()).getAsJsonObject());
                status = "";

            } catch (Throwable t) {
                status = "shop unreachable: " + t.getMessage();
                SpaceClient.LOGGER.warn("Shop request failed", t);
            } finally {
                busy = false;
            }
        });
    }

    private static void parse(JsonObject root) {
        balance = root.has("balance") ? root.get("balance").getAsInt() : 0;

        List<ShopItem> items = new ArrayList<>();
        JsonArray array = root.getAsJsonArray("catalogue");
        if (array != null) {
            for (var element : array) {
                JsonObject item = element.getAsJsonObject();
                items.add(new ShopItem(
                        item.get("id").getAsString(),
                        item.get("name").getAsString(),
                        item.get("type").getAsString(),
                        item.get("price").getAsInt(),
                        item.has("owned") && item.get("owned").getAsBoolean()));
            }
        }
        catalogue = items;

        Map<String, String> worn = new HashMap<>();
        JsonObject equippedJson = root.getAsJsonObject("equipped");
        if (equippedJson != null) {
            for (String slot : equippedJson.keySet()) {
                worn.put(slot, equippedJson.get(slot).getAsString());
            }
        }
        equipped = worn;
    }

    public static CompletableFuture<Void> buy(String itemId) {
        return post("/shop/buy", "{\"item\":\"" + itemId + "\"}", "Bought");
    }

    public static CompletableFuture<Void> equip(String itemId) {
        return post("/shop/equip", "{\"item\":\"" + itemId + "\"}", "Equipped");
    }

    private static CompletableFuture<Void> post(String path, String body, String verb) {
        return CompletableFuture.runAsync(() -> {
            String name = playerName();
            if (token() == null || name == null) {
                status = "sign in with Microsoft to use the shop";
                return;
            }
            busy = true;
            try {
                String serverId = handshake();
                if (serverId == null) {
                    status = "Mojang would not confirm this session - is the game online?";
                    return;
                }

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE + path))
                        .header("X-Space-Name", name)
                        .header("X-Space-Server", serverId)
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpResponse<String> response =
                        http().send(request, HttpResponse.BodyHandlers.ofString());

                JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();

                if (response.statusCode() != 200) {
                    // The server's own words are more useful than a status code
                    status = result.has("error")
                            ? result.get("error").getAsString()
                            : "refused (" + response.statusCode() + ")";
                    return;
                }
                status = verb;

            } catch (Throwable t) {
                status = "request failed: " + t.getMessage();
            } finally {
                busy = false;
            }
        }).thenCompose(ignored -> refresh());
    }

    private ShopClient() {}
}
