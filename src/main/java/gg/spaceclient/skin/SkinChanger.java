package gg.spaceclient.skin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.spaceclient.SpaceClient;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Changes the skin from inside the game.
 *
 * The upload is the launcher's: the image goes to Mojang with the session's
 * own access token, the one the game signed in with. What only the game can
 * add is seeing it at once. Mojang's answer changes the account, but the
 * game keeps the skin it was given when it joined until it joins again - so
 * after an upload the player's own figure is shown with the new skin and
 * model right away. Everybody else sees it the next time they see the
 * player join, which is how skins have always worked.
 */
public final class SkinChanger {

    private static final String SKINS_URL = "https://api.minecraftservices.com/minecraft/profile/skins";

    /** The skin to show on the player in place of the one from the server. */
    public record Shown(UUID player, Identifier texture, boolean slim) {}

    private static volatile Shown shown = null;
    private static volatile String status = "";
    private static volatile boolean busy = false;

    private SkinChanger() {}

    public static Shown shown() { return shown; }
    public static String status() { return status; }
    public static boolean busy() { return busy; }

    private static HttpClient http() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    /** Puts a library skin on the account. */
    public static CompletableFuture<Boolean> apply(SkinLibrary.Entry entry) {
        Minecraft mc = Minecraft.getInstance();
        String token;
        UUID player;
        try {
            token = mc.getUser().getAccessToken();
            player = mc.getUser().getProfileId();
        } catch (Throwable t) {
            status = "Could not read the game session.";
            return CompletableFuture.completedFuture(false);
        }
        // An offline profile has a placeholder token Mojang never accepts
        if (token == null || token.length() < 20) {
            status = "Skins need a Microsoft account - sign in with one in the launcher.";
            return CompletableFuture.completedFuture(false);
        }

        busy = true;
        status = "Putting on " + entry.name() + "…";
        return CompletableFuture.supplyAsync(() -> {
            try {
                byte[] png = Files.readAllBytes(entry.path());
                upload(token, png, entry.slim());
                // Seen at once, on the render thread where textures live
                mc.execute(() -> {
                    Identifier texture = SkinImage.texture(entry.id(), png);
                    if (texture != null) shown = new Shown(player, texture, entry.slim());
                });
                status = entry.name() + " is on. Others see it once you rejoin.";
                return true;
            } catch (Exception e) {
                status = e.getMessage() == null ? e.toString() : e.getMessage();
                SpaceClient.LOGGER.warn("Skin upload failed: {}", status);
                return false;
            } finally {
                busy = false;
            }
        });
    }

    /** The multipart upload Mojang's skin call takes: the model and the file. */
    private static void upload(String token, byte[] png, boolean slim) throws Exception {
        String boundary = "----spaceclient" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        String head = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"variant\"\r\n\r\n"
                + (slim ? "slim" : "classic") + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n";
        body.write(head.getBytes(StandardCharsets.UTF_8));
        body.write(png);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(URI.create(SKINS_URL))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();

        HttpResponse<String> response;
        try {
            response = http().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.http.HttpTimeoutException e) {
            throw new Exception("Mojang's skin server did not answer in time. Try again in a moment.");
        } catch (java.io.IOException e) {
            // Once more: a dropped connection is usually gone on the second try
            try {
                response = http().send(request, HttpResponse.BodyHandlers.ofString());
            } catch (java.io.IOException again) {
                throw new Exception("Mojang's skin server could not be reached. Check the connection.");
            }
        }

        int code = response.statusCode();
        if (code == 429) throw new Exception("Mojang only allows a few skin changes per minute. Wait a moment.");
        if (code == 401) throw new Exception("The game's sign-in has expired. Restart it from the launcher.");
        if (code / 100 != 2) {
            String detail = response.body();
            throw new Exception("Mojang rejected the skin (" + code + ")"
                    + (detail == null || detail.isBlank() ? "." : ": " + detail));
        }
    }

    /** Another player's skin, by name, into the library - model included. */
    public static CompletableFuture<SkinLibrary.Entry> importPlayer(String name) {
        String player = name.trim();
        if (!player.matches("[A-Za-z0-9_]{1,16}")) {
            status = "That is not a Minecraft name.";
            return CompletableFuture.completedFuture(null);
        }
        busy = true;
        status = "Fetching " + player + "'s skin…";
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpClient client = http();
                HttpResponse<String> lookup = client.send(HttpRequest.newBuilder(
                        URI.create("https://api.mojang.com/users/profiles/minecraft/" + player))
                        .timeout(Duration.ofSeconds(20)).build(), HttpResponse.BodyHandlers.ofString());
                if (lookup.statusCode() == 404 || lookup.statusCode() == 204 || lookup.body().isBlank()) {
                    status = "There is no player called " + player + ".";
                    return null;
                }
                JsonObject found = JsonParser.parseString(lookup.body()).getAsJsonObject();
                String id = found.get("id").getAsString();
                String exact = found.has("name") ? found.get("name").getAsString() : player;

                HttpResponse<String> profile = client.send(HttpRequest.newBuilder(
                        URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + id))
                        .timeout(Duration.ofSeconds(20)).build(), HttpResponse.BodyHandlers.ofString());
                String encoded = null;
                for (var property : JsonParser.parseString(profile.body()).getAsJsonObject().getAsJsonArray("properties")) {
                    JsonObject p = property.getAsJsonObject();
                    if ("textures".equals(p.get("name").getAsString())) encoded = p.get("value").getAsString();
                }
                if (encoded == null) throw new Exception("No skin data for " + exact + ".");
                JsonObject textures = JsonParser.parseString(
                        new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8)).getAsJsonObject()
                        .getAsJsonObject("textures");
                if (textures == null || !textures.has("SKIN")) {
                    status = exact + " uses the default skin, there is nothing to import.";
                    return null;
                }
                JsonObject skin = textures.getAsJsonObject("SKIN");
                boolean slim = skin.has("metadata") && "slim".equals(
                        skin.getAsJsonObject("metadata").get("model").getAsString());
                String url = skin.get("url").getAsString().replaceFirst("^http://", "https://");

                HttpResponse<byte[]> image = client.send(HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(20)).build(), HttpResponse.BodyHandlers.ofByteArray());
                if (image.statusCode() / 100 != 2) throw new Exception("Could not download the skin (" + image.statusCode() + ").");

                SkinLibrary.Entry entry = SkinLibrary.store(image.body(), exact, slim);
                status = exact + "'s skin is in the library.";
                return entry;
            } catch (Exception e) {
                status = e.getMessage() == null ? e.toString() : e.getMessage();
                return null;
            } finally {
                busy = false;
            }
        });
    }

    /** For messages that are not about a request. */
    public static void say(String message) {
        status = message;
    }
}
