package gg.spaceclient.badge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.spaceclient.SpaceClient;
import net.minecraft.client.MinecraftClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Talks to the Space Client badge service.
 *
 * On startup the mod registers the signed-in account, then pulls the list of
 * everyone else who has registered. That is the only way a client can know
 * which other players run this client - Minecraft itself never reveals it.
 *
 * The badge is drawn locally: players without the mod see nothing.
 */
public class UserRegistry {
    /** The deployed Cloudflare Worker backing the badge list. */
    private static final String API_BASE = "https://spaceclient-finanzinstitut.workers.dev";

    private static final Set<UUID> knownUsers = new HashSet<>();
    private static boolean loaded = false;

    private static HttpClient client() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    /** Registers this account, then refreshes the list. Failures are silent. */
    public static void registerAndRefresh() {
        CompletableFuture.runAsync(() -> {
            register();
            refresh();
        });
    }

    private static void register() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.getSession() == null) return;

            String uuid = mc.getSession().getUuidOrNull() != null
                    ? mc.getSession().getUuidOrNull().toString()
                    : null;
            String name = mc.getSession().getUsername();
            if (uuid == null || name == null) return;

            JsonObject body = new JsonObject();
            body.addProperty("uuid", uuid);
            body.addProperty("name", name);
            body.addProperty("version", SpaceClient.VERSION);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE + "/register"))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "SpaceClient-Mod")
                    .timeout(Duration.ofSeconds(8))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response =
                    client().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                SpaceClient.LOGGER.info("Registered with the badge service");
            } else {
                SpaceClient.LOGGER.warn("Badge registration returned {}", response.statusCode());
            }
        } catch (Exception e) {
            // No badge is a cosmetic loss; never let it disrupt startup
            SpaceClient.LOGGER.warn("Could not register for a badge: {}", e.getMessage());
        }
    }

    private static void refresh() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE + "/users"))
                    .header("User-Agent", "SpaceClient-Mod")
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    client().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return;

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonArray array = root.getAsJsonArray("users");

            Set<UUID> parsed = new HashSet<>();
            for (var element : array) {
                try {
                    parsed.add(UUID.fromString(element.getAsString().trim()));
                } catch (IllegalArgumentException ignored) {
                    // One malformed entry should not discard the whole list
                }
            }

            synchronized (knownUsers) {
                knownUsers.clear();
                knownUsers.addAll(parsed);
                loaded = true;
            }
            SpaceClient.LOGGER.info("Loaded {} Space Client users", parsed.size());

        } catch (Exception e) {
            SpaceClient.LOGGER.warn("Could not load the user list: {}", e.getMessage());
        }
    }

    public static boolean hasBadge(UUID uuid) {
        if (uuid == null) return false;

        // You are running the client, so your own badge never depends on the service
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && uuid.equals(mc.player.getUuid())) return true;

        synchronized (knownUsers) {
            return loaded && knownUsers.contains(uuid);
        }
    }

    public static int getUserCount() {
        synchronized (knownUsers) {
            return knownUsers.size();
        }
    }
}
