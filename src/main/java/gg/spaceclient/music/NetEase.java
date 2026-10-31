package gg.spaceclient.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Locale;

/**
 * Timed lyrics from NetEase Cloud Music, for when LRCLIB has none or is
 * down. Its web player's public endpoints, no account: a search for the
 * song, then that song's LRC. Unofficial, so anything unexpected is treated
 * as "nothing found" rather than an error.
 *
 * A search hit is only taken when its title and one of its artists match
 * the track that is playing, so a cover or a song of the same name by
 * somebody else does not put the wrong words on screen.
 */
final class NetEase {

    private static final String BASE = "https://music.163.com/api";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private NetEase() {}

    /** The song's LRC with timestamps, or null when NetEase has none that matches. */
    static String synced(String artist, String title) throws Exception {
        String search = get(BASE + "/search/get?type=1&limit=10&s=" + Lyrics.encode(artist + " " + title));
        if (search == null) return null;
        JsonObject result = JsonParser.parseString(search).getAsJsonObject().getAsJsonObject("result");
        if (result == null || !result.has("songs")) return null;

        for (JsonElement element : result.getAsJsonArray("songs")) {
            JsonObject song = element.getAsJsonObject();
            if (!same(song.get("name").getAsString(), title)) continue;
            if (!artistMatches(song.getAsJsonArray("artists"), artist)) continue;

            String body = get(BASE + "/song/lyric?lv=1&kv=1&tv=-1&id=" + song.get("id").getAsLong());
            if (body == null) return null;
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (!json.has("lrc") || !json.getAsJsonObject("lrc").has("lyric")) return null;
            String lrc = clean(json.getAsJsonObject("lrc").get("lyric").getAsString());
            return lrc.contains("[") ? lrc : null;
        }
        return null;
    }

    /**
     * NetEase opens its lyrics with credit lines ("作词 : ...", "作曲 : ...",
     * "Lyrics by: ..."), stamped like any other line; they are taken out so
     * the first words on screen are the song's.
     */
    static String clean(String lrc) {
        StringBuilder out = new StringBuilder();
        for (String line : lrc.split("\n")) {
            int close = line.lastIndexOf(']');
            String text = close >= 0 ? line.substring(close + 1).trim() : line.trim();
            if (isCredit(text)) continue;
            out.append(line).append('\n');
        }
        return out.toString();
    }

    private static boolean isCredit(String text) {
        int colon = Math.max(text.indexOf(':'), text.indexOf('：'));
        if (colon < 0 || colon > 16) return false;
        String head = text.substring(0, colon).trim().toLowerCase(Locale.ROOT);
        return head.matches(".*(作词|作曲|编曲|制作|混音|和声|监制|录音|母带|吉他|贝斯|鼓|键盘|弦乐|出品|发行"
                + "|lyrics|lyricist|composer|composed|written|producer|produced|arranger|arranged|mix|master).*");
    }

    private static boolean artistMatches(JsonArray artists, String wanted) {
        if (wanted == null || wanted.isBlank()) return true;
        String want = norm(wanted);
        for (JsonElement a : artists) {
            String name = norm(a.getAsJsonObject().get("name").getAsString());
            if (!name.isEmpty() && (want.contains(name) || name.contains(want))) return true;
        }
        return false;
    }

    /** Titles match without case, accents, punctuation or a "(Remastered 2011)" tail. */
    static boolean same(String a, String b) {
        return norm(stripTail(a)).equals(norm(stripTail(b)));
    }

    private static String stripTail(String title) {
        return title.replaceAll("\\s*[(\\[].*?[)\\]]\\s*", " ")
                .replaceAll("\\s+-\\s+.*$", "").trim();
    }

    private static String norm(String text) {
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return plain.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static String get(String url) throws Exception {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", "Mozilla/5.0 (SpaceClient)")
                        .header("Referer", "https://music.163.com/")
                        .timeout(Duration.ofSeconds(8))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 200 ? response.body() : null;
    }
}
