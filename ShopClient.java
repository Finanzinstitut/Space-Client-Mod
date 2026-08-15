package gg.spaceclient.shop;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.session.SessionManager;

import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Talks to the Galaxy Points service.
 *
 * Nothing about the balance or the catalogue is decided here. The client asks,
 * the server answers, and a purchase is a request that the server may refuse.
 * That split matters because these points are meant to cost money: a balance
 * kept in a config file is a number the owner can edit.
 *
 * Requests carry the Minecraft session token, so the server can confirm who is
 * asking rather than trusting a uuid in the body.
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

    public static int balance() { return balance; }
    public static List<ShopItem> catalogue() { return catalogue; }
    public static Map<String, String> equipped() { return equipped; }
    public static String status() { return status; }
    public static boolean isBusy() { return busy; }

    private static HttpClient http() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    }

    /** The token the server uses to work out who is asking. */
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

    public static CompletableFuture<Void> refresh() {
        return CompletableFuture.runAsync(() -> {
            String token = token();
            if (token == null) {
                status = "sign in with Microsoft to use the shop";
                return;
            }
            busy = true;
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE + "/shop"))
                        .header("Authorization", "Bearer " + token)
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
            String token = token();
            if (token == null) {
                status = "sign in with Microsoft to use the shop";
                return;
            }
            busy = true;
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE + path))
                        .header("Authorization", "Bearer " + token)
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
