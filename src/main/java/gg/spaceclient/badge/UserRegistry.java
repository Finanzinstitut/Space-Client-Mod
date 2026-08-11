package gg.spaceclient.badge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import gg.spaceclient.SpaceClient;

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
 * Who gets the Jupiter badge.
 *
 * IMPORTANT LIMITATION: a client cannot find out on its own which *other*
 * players are running Space Client. Nothing about another player's client is
 * visible over vanilla protocol, and servers do not relay that. So this reads a
 * published list of UUIDs instead.
 *
 * That means:
 *  - You always see your own badge.
 *  - You see another player's badge only if their UUID is on the list AND you
 *    are running the mod. Players without the mod see nothing at all.
 *
 * Making it automatic needs a small backend the mod registers with on startup.
 * Until then the list is a JSON file in the mod's repository.
 */
public class UserRegistry {
    private static final String LIST_URL =
            "https://raw.githubusercontent.com/Finanzinstitut/Space-Client-Mod/main/users.json";

    private static final Set<UUID> knownUsers = new HashSet<>();
    private static boolean loaded = false;

    /** Fetched once on startup; a failure just means only your own badge shows. */
    public static void refresh() {
        CompletableFuture.runAsync(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(LIST_URL))
                        .header("User-Agent", "SpaceClient-Mod")
                        .timeout(Duration.ofSeconds(8))
                        .GET()
                        .build();

                HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) return;

                JsonElement root = JsonParser.parseString(response.body());
                JsonArray array = root.isJsonArray()
                        ? root.getAsJsonArray()
                        : root.getAsJsonObject().getAsJsonArray("users");

                Set<UUID> parsed = new HashSet<>();
                for (JsonElement element : array) {
                    try {
                        parsed.add(UUID.fromString(element.getAsString().trim()));
                    } catch (IllegalArgumentException ignored) {
                        // A malformed entry should not discard the whole list
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
        });
    }

    public static boolean hasBadge(UUID uuid) {
        if (uuid == null) return false;

        // You are running the client, so you always get the badge
        var client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.player != null && uuid.equals(client.player.getUuid())) return true;

        synchronized (knownUsers) {
            return loaded && knownUsers.contains(uuid);
        }
    }
}
