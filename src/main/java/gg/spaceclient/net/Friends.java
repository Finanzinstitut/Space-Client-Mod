package gg.spaceclient.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import gg.spaceclient.SpaceClient;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Friends, requests and messages.
 *
 * <h2>Where the work happens</h2>
 *
 * Everything in this class that touches the network runs on one background
 * thread and writes into fields the screens read. No screen ever waits on a
 * socket: a menu that freezes for two seconds because somebody's connection is
 * slow is worse than one that says "asking..." for two seconds.
 *
 * <h2>Instant, and what that means here</h2>
 *
 * The poll holds its request open at the other end rather than asking again
 * and again - it comes back the moment there is something and otherwise waits
 * about half a minute. A message therefore lands within roughly a second of
 * being sent, while an idle client makes two or three calls a minute instead
 * of sixty.
 *
 * <h2>Who you are</h2>
 *
 * The same proof the rest of this client uses: the game tells Mojang it is
 * joining a server with a random id, the worker asks Mojang whether that
 * happened, and only then is there a token. The account's own secret never
 * goes anywhere near the worker.
 */
public final class Friends {

    /**
     * The friends worker.
     *
     * Its own deployment, not the badge one. Deploy worker/friends with
     * wrangler; if the name it prints differs from this, this line is the only
     * thing that has to change.
     *
     * Read from a property first, so a different address - a test server, a
     * second deployment, somebody running their own - needs a launch argument
     * rather than a new build of the mod.
     */
    public static final String BASE = System.getProperty(
            "spaceclient.friends",
            "https://spaceclient-friends.spaceclient-finanzinstitut.workers.dev");

    /** One person, as the list shows them. */
    public record Person(String uuid, String name) {}

    /** One line in a conversation. */
    public record Message(long id, String from, String fromName, String text,
                          long sent, boolean mine) {}

    /** A group chat and who is in it. */
    public record Group(long id, String name, String owner, List<Person> members) {
        /** The key its conversation is filed under, beside the friends' uuids. */
        public String key() { return groupKey(id); }
    }

    /** Conversations are filed by uuid for a friend and by this for a group. */
    public static String groupKey(long id) { return "group:" + id; }

    private static final List<Person> friends = new ArrayList<>();
    private static final List<Person> incoming = new ArrayList<>();
    private static final List<Person> outgoing = new ArrayList<>();
    private static final List<Group> groups = new ArrayList<>();

    /**
     * Raised whenever anything a screen shows has changed.
     *
     * The screens used to rebuild once, right after a button was pressed -
     * before the server had answered - and never again. A request that went
     * through, a friend who accepted, a new message: none of it appeared until
     * the screen was closed and opened. That is what "you cannot add anybody"
     * looked like from the outside. A screen now compares this number on every
     * tick and rebuilds when it moves.
     */
    private static volatile long revision = 0;

    /** Why the last send to a conversation failed, by conversation key. */
    private static final Map<String, String> sendErrors = new ConcurrentHashMap<>();

    /**
     * When this session started listening. History that was already there is
     * loaded on the first poll, and counting it as unread lit up every
     * conversation as new each time the game started.
     */
    private static volatile long listeningSince = 0;

    /** Conversations by the other person's uuid, oldest line first. */
    private static final Map<String, List<Message>> threads = new ConcurrentHashMap<>();

    /** How many lines have arrived in a thread since it was last looked at. */
    private static final Map<String, Integer> unread = new ConcurrentHashMap<>();

    private static volatile String token = "";
    private static volatile long tokenExpires = 0;
    private static volatile String myUuid = "";
    private static volatile String myName = "";

    private static volatile String status = "not started";
    private static volatile boolean busy = false;
    private static volatile long cursor = 0;
    private static volatile long groupCursor = 0;
    private static volatile long version = -1;

    private static volatile boolean pollRunning = false;
    private static volatile long lastRefresh = 0;

    /**
     * The earliest the next sign-in may be attempted.
     *
     * This exists because of a bug worth writing down. Signing in means asking
     * Mojang to vouch for the account, and Mojang counts those: ask too often
     * and it stops answering for a while. The friends screen asked on every
     * build - and a screen is rebuilt on every tab, every button, every
     * rebuildWidgets - so while a sign-in was failing, each click was another
     * request. Mojang's limiter is exactly the right answer to that, and the
     * error it sends back is the one this field prevents.
     *
     * After a failure the wait doubles, up to five minutes. After a success it
     * is gone: a good token lasts an hour and nothing asks again until it ends.
     */
    private static volatile long nextSignInAttempt = 0;
    private static volatile int signInFailures = 0;

    public static String status() { return status; }
    public static boolean isBusy() { return busy; }
    public static String myName() { return myName; }
    public static boolean signedIn() { return !token.isEmpty(); }

    public static synchronized List<Person> friends() { return List.copyOf(friends); }
    public static synchronized List<Person> incoming() { return List.copyOf(incoming); }
    public static synchronized List<Person> outgoing() { return List.copyOf(outgoing); }
    public static synchronized List<Group> groups() { return List.copyOf(groups); }

    public static synchronized Group group(long id) {
        for (Group group : groups) if (group.id() == id) return group;
        return null;
    }

    public static long revision() { return revision; }

    private static void changed() { revision++; }

    /** The last failed send in a conversation, or empty. */
    public static String sendError(String key) { return sendErrors.getOrDefault(key, ""); }

    public static List<Message> thread(String key) {
        List<Message> thread = threads.get(key);
        if (thread == null) return List.of();
        synchronized (thread) { return List.copyOf(thread); }
    }

    public static int unreadFor(String uuid) { return unread.getOrDefault(uuid, 0); }

    public static int unreadTotal() {
        int total = 0;
        for (int count : unread.values()) total += count;
        return total;
    }

    /** How many things are waiting for an answer, for the badge on the menu. */
    public static int waitingCount() {
        synchronized (Friends.class) {
            return incoming.size() + unreadTotal();
        }
    }

    public static void markRead(String uuid) {
        if (unread.remove(uuid) != null) changed();
    }

    // ---------------------------------------------------------------- threads

    private static void background(String what, Runnable work) {
        Thread worker = new Thread(() -> {
            try {
                work.run();
            } catch (Throwable t) {
                status = what + " failed: " + t.getMessage();
                SpaceClient.LOGGER.warn("Friends: {} failed", what, t);
            } finally {
                changed();
            }
        }, "space-client-friends-" + what);
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Opens the screen's worth of work: sign in, load the list, start waiting.
     *
     * Safe to call as often as a screen opens. The sign-in is skipped while the
     * token is good and the poll refuses to start twice.
     */
    public static void wake() {
        background("refresh", () -> {
            if (!ensureToken()) return;
            loadList();
            startPolling();
        });
    }

    /**
     * Reloads the list, but not more than once every few seconds.
     *
     * Counted from the attempt and not from the last success. Counting
     * successes meant that while something was wrong the counter never moved,
     * so the throttle did nothing at exactly the moment it was needed most.
     */
    public static void refreshSoon() {
        long now = System.currentTimeMillis();
        if (now - lastRefresh < 3000) return;
        lastRefresh = now;
        wake();
    }

    private static synchronized boolean ensureToken() {
        long now = System.currentTimeMillis();
        if (!token.isEmpty() && now < tokenExpires - 60_000) return true;

        if (now < nextSignInAttempt) {
            long seconds = Math.max(1, (nextSignInAttempt - now + 999) / 1000);
            status = waitingNote + " - trying again in " + seconds + "s";
            return false;
        }

        String name = SpaceApi.accountName();
        if (name.isEmpty()) {
            backOff("no account in the running game");
            return false;
        }

        String serverId = SpaceApi.handshake();
        if (serverId.isEmpty()) {
            String why = SpaceApi.status();
            backOff(why.contains("RateLimiter")
                    // Said in words rather than as a stack trace, because the
                    // only useful thing to know is that waiting fixes it
                    ? "Mojang is refusing sign-ins for a moment"
                    : "Mojang handshake failed: " + why);
            return false;
        }

        JsonObject body = post("/session", "{}", request -> request
                .header("X-Space-Name", name)
                .header("X-Space-Server", serverId), false);

        if (body == null || !body.has("token")) {
            backOff(status.isEmpty() ? "the friends server refused the sign-in" : status);
            return false;
        }

        token = body.get("token").getAsString();
        myUuid = text(body, "uuid");
        myName = text(body, "name");
        tokenExpires = System.currentTimeMillis()
                + (body.has("expiresIn") ? body.get("expiresIn").getAsLong() : 3600) * 1000L;

        signInFailures = 0;
        nextSignInAttempt = 0;
        status = "signed in as " + myName;
        return true;
    }

    /** What the last failure was, kept for the waiting message. */
    private static volatile String waitingNote = "not signed in";

    /**
     * Holds off the next attempt, for longer each time.
     *
     * Fifteen seconds, then thirty, then a minute, up to five. Long enough
     * that a broken account cannot hammer Mojang, short enough that somebody
     * who has just fixed their connection does not sit there wondering.
     */
    private static void backOff(String note) {
        signInFailures = Math.min(signInFailures + 1, 5);
        long wait = Math.min(300_000L, 15_000L * (1L << (signInFailures - 1)));
        nextSignInAttempt = System.currentTimeMillis() + wait;
        waitingNote = note;
        status = note + " - trying again in " + (wait / 1000) + "s";
    }

    /** Forgets the waiting period, for a button that says "try now". */
    public static void tryAgainNow() {
        nextSignInAttempt = 0;
        signInFailures = 0;
        lastRefresh = 0;
        wake();
    }

    private static void loadList() {
        JsonObject body = get("/friends");
        if (body == null) return;

        synchronized (Friends.class) {
            friends.clear();
            incoming.clear();
            outgoing.clear();
            fill(friends, body, "friends");
            fill(incoming, body, "incoming");
            fill(outgoing, body, "outgoing");
            groups.clear();
            if (body.has("groups") && body.get("groups").isJsonArray()) {
                for (JsonElement element : body.getAsJsonArray("groups")) {
                    JsonObject entry = element.getAsJsonObject();
                    List<Person> members = new ArrayList<>();
                    fill(members, entry, "members");
                    groups.add(new Group(entry.get("id").getAsLong(), text(entry, "name"),
                            text(entry, "owner"), List.copyOf(members)));
                }
            }
        }
        if (body.has("version")) version = body.get("version").getAsLong();
        lastRefresh = System.currentTimeMillis();
        // The count only when nothing more useful was said a moment ago: a
        // "group created" or "no player by that name" must stay readable
        // rather than be replaced by the reload that follows it
        if (System.currentTimeMillis() - noteAt > 8000) status = friends.size() + " friend(s)";
        changed();
    }

    /** When an action last left a note for the player in status. */
    private static volatile long noteAt = 0;

    private static void note(String text) {
        status = text;
        noteAt = System.currentTimeMillis();
    }

    private static void fill(List<Person> into, JsonObject body, String key) {
        if (!body.has(key) || !body.get(key).isJsonArray()) return;
        for (JsonElement element : body.getAsJsonArray(key)) {
            JsonObject entry = element.getAsJsonObject();
            into.add(new Person(text(entry, "uuid"), text(entry, "name")));
        }
    }

    // ---------------------------------------------------------------- doing things

    public static void add(String name) {
        busy = true;
        background("add", () -> {
            try {
                if (!ensureToken()) return;
                JsonObject body = post("/friends/request",
                        "{\"name\":" + quote(name) + "}", null, true);
                // What the server said is what the player wants to read - "asked",
                // or why not - so it is kept over the list's own count
                String said = body != null ? text(body, "note") : status;
                loadList();
                note(body != null ? name + ": " + said : said);
            } finally {
                busy = false;
            }
        });
    }

    public static void accept(String uuid) {
        busy = true;
        background("accept", () -> {
            try {
                if (!ensureToken()) return;
                post("/friends/accept", "{\"uuid\":" + quote(uuid) + "}", null, true);
                loadList();
            } finally {
                busy = false;
            }
        });
    }

    public static void remove(String uuid) {
        busy = true;
        background("remove", () -> {
            try {
                if (!ensureToken()) return;
                post("/friends/remove", "{\"uuid\":" + quote(uuid) + "}", null, true);
                threads.remove(uuid);
                unread.remove(uuid);
                loadList();
            } finally {
                busy = false;
            }
        });
    }

    /**
     * Sends a line, and shows it at once.
     *
     * The copy added here is replaced when the same line comes back from the
     * poll - both sides of a conversation are served from the same feed, so
     * there is exactly one source of truth and the local copy is only there so
     * the message does not appear to vanish for a second after Enter.
     */
    public static void send(String uuid, String text) {
        deliver(uuid, text, "/messages", "{\"to\":" + quote(uuid) + ",\"text\":");
    }

    /** A line to a group, shown at once and confirmed by the poll like any other. */
    public static void sendToGroup(long groupId, String text) {
        deliver(groupKey(groupId), text, "/groups/message", "{\"group\":" + groupId + ",\"text\":");
    }

    private static void deliver(String key, String text, String path, String bodyStart) {
        String clean = text.trim();
        if (clean.isEmpty()) return;

        Message pending = new Message(-1, myUuid, myName, clean, System.currentTimeMillis(), true);
        List<Message> thread = threads.computeIfAbsent(key, k -> new ArrayList<>());
        synchronized (thread) { thread.add(pending); }
        sendErrors.remove(key);
        changed();

        background("send", () -> {
            JsonObject answer = ensureToken()
                    ? post(path, bodyStart + quote(clean) + "}", null, true)
                    : null;
            if (answer == null) {
                // Taken back out, with the reason beside the box, rather than
                // left sitting there looking sent
                synchronized (thread) { thread.remove(pending); }
                sendErrors.put(key, "Not sent: " + status);
            }
        });
    }

    // ---------------------------------------------------------------- groups

    public static void createGroup(String name, List<String> members) {
        busy = true;
        background("group", () -> {
            try {
                if (!ensureToken()) return;
                JsonObject body = post("/groups/create",
                        "{\"name\":" + quote(name) + ",\"members\":" + quoteAll(members) + "}",
                        null, true);
                String said = body != null ? text(body, "note") : status;
                loadList();
                note(said);
            } finally {
                busy = false;
            }
        });
    }

    public static void invite(long groupId, List<String> members) {
        busy = true;
        background("invite", () -> {
            try {
                if (!ensureToken()) return;
                JsonObject body = post("/groups/invite",
                        "{\"group\":" + groupId + ",\"members\":" + quoteAll(members) + "}",
                        null, true);
                String said = body != null ? text(body, "note") : status;
                loadList();
                note(said);
            } finally {
                busy = false;
            }
        });
    }

    public static void leave(long groupId) {
        busy = true;
        background("leave", () -> {
            try {
                if (!ensureToken()) return;
                post("/groups/leave", "{\"group\":" + groupId + "}", null, true);
                threads.remove(groupKey(groupId));
                unread.remove(groupKey(groupId));
                loadList();
            } finally {
                busy = false;
            }
        });
    }

    private static String quoteAll(List<String> values) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append(',');
            out.append(quote(values.get(i)));
        }
        return out.append(']').toString();
    }

    // ---------------------------------------------------------------- the poll

    private static void startPolling() {
        if (pollRunning) return;
        pollRunning = true;

        background("poll", () -> {
            try {
                if (listeningSince == 0) listeningSince = System.currentTimeMillis();
                while (signedIn()) {
                    JsonObject body = get("/poll?since=" + cursor + "&gsince=" + groupCursor
                            + "&version=" + version);
                    if (body == null) {
                        // A failed poll must not become a tight loop against a
                        // worker that is down
                        Thread.sleep(5000);
                        continue;
                    }

                    long newVersion = body.has("version") ? body.get("version").getAsLong() : version;
                    if (newVersion != version) loadList();
                    version = newVersion;

                    if (body.has("cursor")) cursor = body.get("cursor").getAsLong();
                    if (body.has("gcursor")) groupCursor = body.get("gcursor").getAsLong();
                    take(body);
                    takeGroups(body);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                pollRunning = false;
            }
        });
    }

    private static void take(JsonObject body) {
        if (!body.has("messages") || !body.get("messages").isJsonArray()) return;

        for (JsonElement element : body.getAsJsonArray("messages")) {
            JsonObject line = element.getAsJsonObject();
            boolean mine = line.has("mine") && line.get("mine").getAsBoolean();
            String other = mine ? text(line, "to") : text(line, "from");
            file(other, line, mine);
        }
    }

    private static void takeGroups(JsonObject body) {
        if (!body.has("groupMessages") || !body.get("groupMessages").isJsonArray()) return;

        for (JsonElement element : body.getAsJsonArray("groupMessages")) {
            JsonObject line = element.getAsJsonObject();
            boolean mine = line.has("mine") && line.get("mine").getAsBoolean();
            file(groupKey(line.get("group").getAsLong()), line, mine);
        }
    }

    /** Puts one line into its conversation, replacing the optimistic copy of it. */
    private static void file(String key, JsonObject line, boolean mine) {
        Message message = new Message(
                line.get("id").getAsLong(), text(line, "from"), text(line, "fromName"),
                text(line, "text"),
                line.has("sent") ? line.get("sent").getAsLong() : 0L,
                mine);

        List<Message> thread = threads.computeIfAbsent(key, k -> new ArrayList<>());
        boolean added;
        synchronized (thread) {
            if (mine) thread.removeIf(existing -> existing.id() < 0
                    && existing.text().equals(message.text()));
            added = thread.stream().noneMatch(existing -> existing.id() == message.id());
            if (added) thread.add(message);
        }

        // Only what arrived while listening is news
        if (added && !mine && message.sent() >= listeningSince) unread.merge(key, 1, Integer::sum);
        changed();
    }

    // ---------------------------------------------------------------- plumbing

    private interface Tweak { HttpRequest.Builder apply(HttpRequest.Builder builder); }

    private static JsonObject get(String path) {
        return call(path, null, null, true);
    }

    private static JsonObject post(String path, String body, Tweak tweak, boolean withToken) {
        return call(path, body == null ? "{}" : body, tweak, withToken);
    }

    private static JsonObject call(String path, String body, Tweak tweak, boolean withToken) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + path))
                    .header("Content-Type", "application/json")
                    // Longer than the worker's own hold, or every poll would
                    // look like a failure a moment before its answer arrived
                    .timeout(Duration.ofSeconds(45));

            if (withToken && !token.isEmpty()) {
                builder = builder.header("Authorization", "Bearer " + token);
            }
            if (tweak != null) builder = tweak.apply(builder);

            builder = body == null
                    ? builder.GET()
                    : builder.POST(HttpRequest.BodyPublishers.ofString(body));

            HttpResponse<String> response = SpaceApi.client()
                    .send(builder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 401) {
                // The token ran out under us; the next call signs in again
                token = "";
                status = "signed out - trying again";
                return null;
            }

            JsonObject parsed = JsonParser.parseString(response.body()).getAsJsonObject();
            if (response.statusCode() != 200) {
                status = parsed.has("note") ? parsed.get("note").getAsString()
                        : "refused (" + response.statusCode() + ")";
                return null;
            }
            return parsed;

        } catch (Throwable t) {
            // With the reason, not without it. "Could not reach the server" on
            // its own is the message that makes a diagnostics screen useless.
            status = "could not reach the friends server: "
                    + t.getClass().getSimpleName()
                    + (t.getMessage() == null ? "" : " - " + t.getMessage());
            return null;
        }
    }

    private static String text(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsString() : "";
    }

    /** JSON string quoting, without pulling a writer in for two fields. */
    static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }

    private Friends() {}
}
