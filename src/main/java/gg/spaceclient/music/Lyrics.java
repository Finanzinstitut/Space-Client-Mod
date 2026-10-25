package gg.spaceclient.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The line of the song that is playing right now.
 *
 * Lyrics come from LRCLIB, which is free and needs no account. They are
 * contributed by users rather than licensed from publishers, which is worth
 * knowing before switching this on - hence the warning on the setting.
 *
 * No lyrics pass through the Space Client backend at all. Each client fetches
 * the words for whatever the people around it are playing and works out the
 * current line itself, from a playback position. That is what keeps the line
 * in step: it is computed locally every frame instead of arriving on a poll,
 * and it also means the backend never stores anybody else's text.
 */
public final class Lyrics {

    /** Longer than this and it would not fit over a head anyway. */
    private static final int MAX_LINE = 64;

    private static final String USER_AGENT =
            "SpaceClient/1.0 (https://github.com/Finanzinstitut)";

    /** How many tracks are kept. Enough for everyone in sight, not unbounded. */
    private static final int MAX_TRACKS = 24;

    /**
     * One timed line, and where in it each word starts - by character and by
     * time. Word times come from the lyrics when they carry them (enhanced
     * LRC, a <mm:ss.xx> before each word) and are estimated from the words'
     * lengths when they do not, which is most of the time.
     */
    private record Line(double at, String text, int[] wordStart, int[] wordEnd, double[] wordAt) {
        Line(double at, String text) { this(at, text, null, null, null); }
    }

    /**
     * What is being sung right now: the line, and which part of it is the
     * word being sung (character positions, end exclusive), or -1 when no
     * word is - an instrumental gap, or before the line's first word.
     */
    public record Moment(String text, int wordStart, int wordEnd) {
        public static final Moment NONE = new Moment("", -1, -1);
        public boolean isEmpty() { return text.isEmpty(); }
        public boolean hasWord() { return wordStart >= 0 && wordEnd > wordStart; }
    }

    /**
     * A lookup result. A failed lookup - the network, not "no lyrics" - is
     * kept only briefly: it used to be cached for good, so one dropped request
     * meant that song never showed a line again for the rest of the session.
     */
    private record Track(List<Line> lines, String status, long expires) {
        Track(List<Line> lines, String status) { this(lines, status, Long.MAX_VALUE); }
    }

    private static final long RETRY_FAILED_MS = 20_000;

    /**
     * Lyrics per track, not just for the local one.
     *
     * Every player in sight may be playing something different, and each of
     * their lines is worked out here rather than sent over the wire - so this
     * holds several songs at once.
     */
    private static final Map<String, Track> cache = new ConcurrentHashMap<>();

    /** Tracks currently being fetched, so a miss does not start ten requests. */
    private static final Set<String> pending = ConcurrentHashMap.newKeySet();

    private static volatile String status = "off";

    public static String status() { return status; }

    /**
     * The line for the current moment, or empty.
     *
     * Deliberately blank rather than falling back to the previous line when the
     * position runs past the end - a song that has finished should show nothing
     * rather than freeze on its last words.
     */
    public static String line(String artist, String title, double position) {
        return moment(artist, title, position).text();
    }

    /** The line for the current moment and the word in it, or Moment.NONE. */
    public static Moment moment(String artist, String title, double position) {
        if (title == null || title.isEmpty() || position < 0) return Moment.NONE;

        String key = key(artist, title);
        Track track = cache.get(key);

        if (track == null || System.currentTimeMillis() > track.expires()) {
            request(artist, title, key);
            return track == null ? Moment.NONE : momentAt(track, position);
        }
        return momentAt(track, position);
    }

    private static Moment momentAt(Track track, double position) {
        Line found = null;
        for (Line line : track.lines()) {
            if (line.at() > position) break;
            found = line;
        }
        if (found == null || found.text().isEmpty()) return Moment.NONE;
        if (found.wordAt() == null || found.wordAt().length == 0) return new Moment(found.text(), -1, -1);

        // The last word that has started; the final one stays lit until the
        // next line takes over
        int word = -1;
        for (int i = 0; i < found.wordAt().length; i++) {
            if (found.wordAt()[i] <= position) word = i;
            else break;
        }
        if (word < 0) return new Moment(found.text(), -1, -1);
        return new Moment(found.text(), found.wordStart()[word], found.wordEnd()[word]);
    }

    /** Drops everything, for when the setting goes off. */
    public static void clear() {
        cache.clear();
        pending.clear();
        status = "off";
    }

    private static String key(String artist, String title) {
        return (artist == null ? "" : artist) + "\u0000" + title;
    }

    /** Starts a lookup for a track nobody has fetched yet. */
    private static void request(String artist, String title, String key) {
        if (!pending.add(key)) return;

        // A negative result is cached like any other, so a track with no
        // lyrics is asked about once rather than on every single frame
        if (cache.size() >= MAX_TRACKS) cache.clear();

        status = "looking up...";

        CompletableFuture.runAsync(() -> {
            try {
                fetch(artist, title, key);
            } catch (Throwable t) {
                cache.put(key, new Track(List.of(), "lookup failed",
                        System.currentTimeMillis() + RETRY_FAILED_MS));
                status = "lookup failed: " + t.getMessage();
            } finally {
                pending.remove(key);
            }
        });
    }

    // ---------------- fetching ----------------

    private static void fetch(String artist, String title, String key) throws Exception {
        String url = "https://lrclib.net/api/get"
                + "?artist_name=" + encode(artist)
                + "&track_name=" + encode(title);

        String body = get(url);

        // The exact match endpoint is strict about spelling, so a miss falls
        // back to a search - which is what makes live and remastered versions
        // resolve at all
        if (body == null) {
            body = searchFor(artist, title);
            if (body == null) {
                cache.put(key, new Track(List.of(), "no lyrics found"));
                status = "no lyrics found";
                return;
            }
        }

        JsonObject json = JsonParser.parseString(body).getAsJsonObject();

        if (!json.has("syncedLyrics") || json.get("syncedLyrics").isJsonNull()) {
            // Plain lyrics exist for many tracks but carry no timestamps, and
            // an unsynced block cannot be shown one line at a time
            cache.put(key, new Track(List.of(), "found, but not synced"));
            status = "found, but not synced";
            return;
        }

        List<Line> parsed = parse(json.get("syncedLyrics").getAsString());
        cache.put(key, new Track(parsed, parsed.size() + " lines"));
        status = parsed.isEmpty() ? "synced lyrics were empty" : parsed.size() + " lines";
    }

    private static String searchFor(String artist, String title) throws Exception {
        String url = "https://lrclib.net/api/search"
                + "?q=" + encode(artist + " " + title);

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

        // 404 is an answer - LRCLIB has nothing for this spelling. Anything
        // else that is not a 200 is the service having a bad moment, and is
        // thrown so the lookup counts as failed and is tried again, rather
        // than cached as "no lyrics" for the rest of the session.
        if (response.statusCode() == 200) return response.body();
        if (response.statusCode() == 404) return null;
        throw new java.io.IOException("LRCLIB answered " + response.statusCode());
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
    static List<Line> parse(String lrc) {
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

            // Word stamps, where the lyrics have them: taken out of the text,
            // remembered by where they stood in it
            StringBuilder clean = new StringBuilder();
            List<int[]> marks = new ArrayList<>();
            List<Double> markTimes = new ArrayList<>();
            int i = 0;
            String body = rest.trim();
            while (i < body.length()) {
                char c = body.charAt(i);
                int close = c == '<' ? body.indexOf('>', i) : -1;
                Double at = close > i ? seconds(body.substring(i + 1, close)) : null;
                if (at != null) {
                    marks.add(new int[]{clean.length()});
                    markTimes.add(at);
                    i = close + 1;
                } else {
                    clean.append(c);
                    i++;
                }
            }
            String text = clean.toString().replaceAll("\\s+", " ").trim();
            if (text.length() > MAX_LINE) text = text.substring(0, MAX_LINE);

            for (double at : stamps) out.add(withWords(at, text, clean.toString(), marks, markTimes));
        }

        out.sort((a, b) -> Double.compare(a.at(), b.at()));

        // Lines without word stamps get them estimated, which needs to know
        // when the next line starts
        List<Line> timed = new ArrayList<>(out.size());
        for (int n = 0; n < out.size(); n++) {
            Line line = out.get(n);
            if (line.wordStart() != null && line.wordAt() == null) {
                double next = line.at() + 5.0;
                for (int m = n + 1; m < out.size(); m++) {
                    if (out.get(m).at() > line.at()) { next = out.get(m).at(); break; }
                }
                line = new Line(line.at(), line.text(), line.wordStart(), line.wordEnd(),
                        estimate(line.at(), next, line.text(), line.wordStart(), line.wordEnd()));
            }
            timed.add(line);
        }
        return timed;
    }

    /**
     * Splits a line into words and, when the lyrics stamped them, gives each
     * its time. Without stamps the times stay null for estimate() to fill in.
     */
    private static Line withWords(double at, String text, String raw,
                                  List<int[]> marks, List<Double> markTimes) {
        if (text.isEmpty()) return new Line(at, text);

        List<int[]> spans = new ArrayList<>();
        int start = -1;
        for (int c = 0; c <= text.length(); c++) {
            boolean space = c == text.length() || Character.isWhitespace(text.charAt(c));
            if (!space && start < 0) start = c;
            if (space && start >= 0) { spans.add(new int[]{start, c}); start = -1; }
        }
        int[] wordStart = new int[spans.size()];
        int[] wordEnd = new int[spans.size()];
        for (int w = 0; w < spans.size(); w++) { wordStart[w] = spans.get(w)[0]; wordEnd[w] = spans.get(w)[1]; }

        // Stamped lines: the stamp in front of each word is its time. Only
        // used when every word has one; otherwise the estimate is better than
        // half a line of guesses
        if (!marks.isEmpty() && raw.trim().equals(text)) {
            double[] wordAt = new double[spans.size()];
            int leading = raw.length() - raw.stripLeading().length();
            boolean all = true;
            for (int w = 0; w < spans.size() && all; w++) {
                double found = Double.NaN;
                for (int m = 0; m < marks.size(); m++) {
                    int pos = marks.get(m)[0] - leading;
                    if (pos <= wordStart[w] && (w == 0 || pos > wordEnd[w - 1] - 1)) found = markTimes.get(m);
                }
                if (Double.isNaN(found)) all = false; else wordAt[w] = found;
            }
            if (all) return new Line(at, text, wordStart, wordEnd, wordAt);
        }
        return new Line(at, text, wordStart, wordEnd, null);
    }

    /**
     * Word times for a line that has none: the line is assumed to be sung at
     * about thirteen characters a second, from its start, and never past the
     * next line; each word gets a share of that time by its length.
     */
    static double[] estimate(double at, double next, String text, int[] wordStart, int[] wordEnd) {
        double gap = Math.max(0.2, next - at);
        double sung = Math.min(gap * 0.92, Math.max(0.6, 0.3 + text.length() * 0.075));
        double total = 0;
        for (int w = 0; w < wordStart.length; w++) total += (wordEnd[w] - wordStart[w]) + 1;
        double[] times = new double[wordStart.length];
        double t = at;
        for (int w = 0; w < wordStart.length; w++) {
            times[w] = t;
            t += sung * ((wordEnd[w] - wordStart[w]) + 1) / total;
        }
        return times;
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
