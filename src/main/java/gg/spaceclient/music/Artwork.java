package gg.spaceclient.music;

import gg.spaceclient.ui.TextureLoader;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Album covers looked up by artist and title, for anyone's song.
 *
 * The local player's cover comes from Windows (see Cover), but that only works
 * for music playing on this machine. The songs over other people's heads, and
 * a local player that hands Windows no picture, need another source - and the
 * public music catalogues answer "artist, title" with a cover and no account.
 * Deezer is asked first because it also knows the slowed and sped up uploads
 * that fill so many playlists; Apple's catalogue is the fallback.
 *
 * Like the lyrics, each client looks the cover up itself from the song's name.
 * Nothing passes through the Space Client backend.
 *
 * Fetching and decoding happen off the render thread; the finished picture
 * waits in the entry until a frame registers it as a texture.
 */
public final class Artwork {

    private static final int MAX = 24;
    private static final String USER_AGENT = "SpaceClient/1.0 (https://github.com/Finanzinstitut)";
    private static final long RETRY_FAILED_MS = 60_000;

    private static final class Entry {
        volatile byte[] png;
        volatile boolean done;
        volatile long failedAt;
        Identifier texture;
        final int number;
        Entry(int number) { this.number = number; }
    }

    private static int counter = 0;

    /** Oldest first, so the eldest is the one given up when it is full. */
    private static final Map<String, Entry> entries = new LinkedHashMap<>(32, 0.75f, true);

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private Artwork() {}

    /**
     * The cover for a song, or null while it is being fetched or when there is
     * none. Must be called on the render thread: it may register a texture.
     */
    public static synchronized Identifier texture(String artist, String title) {
        if (title == null || title.isBlank()) return null;
        String key = (artist == null ? "" : artist) + "\u0000" + title;

        Entry entry = entries.get(key);
        if (entry != null && !entry.done && entry.failedAt > 0
                && System.currentTimeMillis() - entry.failedAt > RETRY_FAILED_MS) {
            entries.remove(key);
            entry = null;
        }
        if (entry == null) {
            if (entries.size() >= MAX) evictOldest();
            entry = new Entry(counter++);
            entries.put(key, entry);
            fetch(artist == null ? "" : artist, title, entry);
            return null;
        }

        if (entry.texture == null && entry.png != null) {
            Identifier id = Identifier.fromNamespaceAndPath("spaceclient", "music/art_" + entry.number);
            entry.texture = TextureLoader.register(entry.png, id);
            entry.png = null;
        }
        return entry.texture;
    }

    private static void evictOldest() {
        var it = entries.entrySet().iterator();
        if (!it.hasNext()) return;
        Entry oldest = it.next().getValue();
        it.remove();
        if (oldest.texture != null) {
            try {
                Minecraft.getInstance().getTextureManager().release(oldest.texture);
            } catch (Throwable ignored) {
                // A texture that outlives its entry is a small leak, not a fault
            }
        }
    }

    private static void fetch(String artist, String title, Entry entry) {
        CompletableFuture.runAsync(() -> {
            try {
                String query = (artist + " " + clean(title)).trim();
                String url = deezer(query);
                if (url == null) url = itunes(query);
                if (url == null && !artist.isBlank()) url = deezer(clean(title));

                if (url != null) {
                    byte[] raw = bytes(url);
                    if (raw != null) entry.png = Cover.toPng(raw);
                }
                entry.done = true;
            } catch (Throwable t) {
                entry.failedAt = System.currentTimeMillis();
            }
        });
    }

    /**
     * The title without the decorations catalogues do not share: "(Official
     * Video)", "[Remastered 2011]", "- slowed". Keeping them usually finds
     * nothing; the cover is the same album either way.
     */
    static String clean(String title) {
        String cleaned = title.replaceAll("[\\(\\[][^\\)\\]]*[\\)\\]]", " ");
        int dash = cleaned.indexOf(" - ");
        if (dash > 0) cleaned = cleaned.substring(0, dash);
        return cleaned.replaceAll("\\s+", " ").trim();
    }

    private static String deezer(String query) throws Exception {
        String body = text("https://api.deezer.com/search?limit=1&q=" + encode(query));
        if (body == null) return null;
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        if (!json.has("data")) return null;
        JsonArray data = json.getAsJsonArray("data");
        if (data.isEmpty()) return null;
        JsonObject album = data.get(0).getAsJsonObject().getAsJsonObject("album");
        if (album == null || !album.has("cover_medium") || album.get("cover_medium").isJsonNull()) return null;
        return album.get("cover_medium").getAsString();
    }

    private static String itunes(String query) throws Exception {
        String body = text("https://itunes.apple.com/search?entity=song&limit=1&term=" + encode(query));
        if (body == null) return null;
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        JsonArray results = json.has("results") ? json.getAsJsonArray("results") : null;
        if (results == null || results.isEmpty()) return null;
        JsonObject first = results.get(0).getAsJsonObject();
        if (!first.has("artworkUrl100")) return null;
        return first.get("artworkUrl100").getAsString().replace("100x100bb", "200x200bb");
    }

    private static String text(String url) throws Exception {
        HttpResponse<String> response = HTTP.send(request(url), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) return response.body();
        if (response.statusCode() == 404) return null;
        throw new java.io.IOException("answered " + response.statusCode());
    }

    private static byte[] bytes(String url) throws Exception {
        HttpResponse<byte[]> response = HTTP.send(request(url), HttpResponse.BodyHandlers.ofByteArray());
        return response.statusCode() == 200 ? response.body() : null;
    }

    private static HttpRequest request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(8))
                .GET().build();
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }
}
