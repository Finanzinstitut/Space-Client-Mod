package gg.spaceclient.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import gg.spaceclient.SpaceClient;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The line of the song that is playing right now.
 *
 * Lyrics come from LRCLIB, which is free and needs no account. They are
 * contributed by users rather than licensed from publishers, which is worth
 * knowing before switching this on - hence the warning on the setting.
 *
 * Only one line ever leaves this class. Whole lyrics are fetched once per
 * track and stay in memory here; what gets reported and drawn is the single
 * line matching the current position. That keeps the amount of somebody else's
 * text passing through the worker down to a fragment.
 */
public final class Lyrics {

    /** Longer than this and it would not fit over a head anyway. */
    private static final int MAX_LINE = 64;

    private static final String USER_AGENT =
            "SpaceClient/1.0 (https://github.com/Finanzinstitut)";

    private record Line(double at, String text) {}

    private static volatile List<Line> lines = List.of();
    private static volatile String loadedKey = "";
    private static volatile String status = "off";
    private static volatile boolean loading = false;

    public static String status() { return status; }

    /**
     * The line for the current moment, or empty.
     *
     * Deliberately blank rather than falling back to the previous line when the
     * position runs past the end - a song that has finished should show nothing
     * rather than freeze on its last words.
     */
    public static String line(double position) {
        List<Line> current = lines;
        if (current.isEmpty() || position < 0) return "";

        String found = "";
        for (Line line : current) {
            if (line.at() > position) break;
            found = line.text();
        }
        return found;
    }

    /** Clears everything, for when the setting goes off. */
    public static void clear() {
        lines = List.of();
        loadedKey = "";
        status = "off";
    }

    /**
     * Makes sure the lyrics in memory belong to the track that is playing.
     *
     * Keyed on artist and title, so a repeat of the same song does not fetch
     * again, and a different song always does.
     */
    public static void update(NowPlaying track) {
        if (track == null || track.isEmpty()) {
            if (!loadedKey.isEmpty()) clear();
            return;
        }

        String key = track.artist() + "\u0000" + track.title();
        if (key.equals(loadedKey) || loading) return;

        loadedKey = key;
        lines = List.of();
        loading = true;
        status = "looking up...";

        CompletableFuture.runAsync(() -> {
            try {
                fetch(track);
            } catch (Throwable t) {
                status = "lookup failed: " + t.getMessage();
            } finally {
                loading = false;
            }
        });
    }

    // ---------------- fetching ----------------

    private static void fetch(NowPlaying track) throws Exception {
        String url = "https://lrclib.net/api/get"
                + "?artist_name=" + encode(track.artist())
                + "&track_name=" + encode(track.title());

        String body = get(url);

        // The exact match endpoint is strict about spelling, so a miss falls
        // back to a search - which is what makes live and remastered versions
        // resolve at all
        if (body == null) {
            body = searchFor(track);
            if (body == null) {
                status = "no lyrics found";
                return;
            }
        }

        JsonObject json = JsonParser.parseString(body).getAsJsonObject();

        if (!json.has("syncedLyrics") || json.get("syncedLyrics").isJsonNull()) {
            // Plain lyrics exist for many tracks but carry no timestamps, and
            // an unsynced block cannot be shown one line at a time
            status = "found, but not synced";
            return;
        }

        List<Line> parsed = parse(json.get("syncedLyrics").getAsString());
        if (parsed.isEmpty()) {
            status = "synced lyrics were empty";
            return;
        }

        lines = parsed;
        status = parsed.size() + " lines";
    }

    private static String searchFor(NowPlaying track) throws Exception {
        String url = "https://lrclib.net/api/search"
                + "?q=" + encode(track.artist() + " " + track.title());

        String body = get(url);
        if (body == null) return null;

        JsonArray results = JsonParser.parseString(body).getAsJsonArray();

        for (int i = 0; i < results.size(); i++) {
            JsonObject candidate = results.get(i).getAsJsonObject();
            if (candidate.has("syncedLyrics") && !candidate.get("syncedLyrics").isJsonNull()) {
                return candidate.toString();
            }
        }
        return null;
    }

    private static String get(String url) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", USER_AGENT)
                        .timeout(Duration.ofSeconds(8))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        return response.statusCode() == 200 ? response.body() : null;
    }

    private static String encode(String text) {
        return URLEncoder.encode(text == null ? "" : text, StandardCharsets.UTF_8);
    }

    // ---------------- LRC format ----------------

    /**
     * Turns an LRC block into timed lines.
     *
     * A line can carry several timestamps when the same words repeat, so each
     * one becomes its own entry. Entries with no text are kept: those are the
     * instrumental gaps, and dropping them would leave the previous line
     * hanging on screen through the whole solo.
     */
    private static List<Line> parse(String lrc) {
        List<Line> out = new ArrayList<>();

        for (String raw : lrc.split("\n")) {
            String rest = raw;
            List<Double> stamps = new ArrayList<>();

            while (rest.startsWith("[")) {
                int close = rest.indexOf(']');
                if (close < 0) break;

                String stamp = rest.substring(1, close);
                rest = rest.substring(close + 1);

                Double seconds = seconds(stamp);
                if (seconds != null) stamps.add(seconds);
            }

            if (stamps.isEmpty()) continue;

            String text = rest.trim();
            if (text.length() > MAX_LINE) text = text.substring(0, MAX_LINE);

            for (double at : stamps) out.add(new Line(at, text));
        }

        out.sort((a, b) -> Double.compare(a.at(), b.at()));
        return out;
    }

    /** Reads mm:ss.xx, or null when the brackets held metadata instead. */
    private static Double seconds(String stamp) {
        try {
            int colon = stamp.indexOf(':');
            if (colon < 0) return null;

            int minutes = Integer.parseInt(stamp.substring(0, colon).trim());
            double rest = Double.parseDouble(stamp.substring(colon + 1).trim());
            return minutes * 60.0 + rest;

        } catch (Throwable ignored) {
            // [ar: ...] and friends land here, which is expected
            return null;
        }
    }

    private Lyrics() {}
}
