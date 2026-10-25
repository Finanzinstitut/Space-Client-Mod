package gg.spaceclient.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import gg.spaceclient.SpaceClient;

import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Talks to the Space Client worker.
 *
 * This is the piece that went with ShopClient - nothing in the mod had spoken
 * to the worker since. What comes back is deliberately smaller than what was
 * there before: one handshake, one short lived token, and two calls that carry
 * nothing but a song.
 *
 * Why a token instead of handshaking per request, which is what the shop did:
 * a handshake is a joinServer call against Mojang, and the song is reported on
 * a timer. Handshaking every time would mean hitting Mojang every few seconds
 * for a heartbeat, which is both wasteful and a good way to get rate limited.
 * So the handshake happens once, the worker hands back a token, and the
 * heartbeats carry only that.
 *
 * The access token never leaves this machine. The game proves who it is to
 * Mojang directly; the worker only asks Mojang whether that happened.
 */
public final class SpaceApi {

    /**
     * The worker.
     *
     * ---> FILL THIS IN <--- The old value went with ShopClient and I have no
     * copy of it. It is whatever `wrangler deploy` prints for the
     * `spaceclient-badges` worker, without a trailing slash - typically
     * https://spaceclient-badges.<your-subdomain>.workers.dev
     *
     * Until it is right, every call here fails quietly and the feature simply
     * does nothing. Nothing else in the mod is affected.
     */
    public static final String BASE = "https://spaceclient-badges.spaceclient-finanzinstitut.workers.dev";

    private static final SecureRandom RANDOM = new SecureRandom();

    private static volatile String token = "";
    private static volatile long tokenExpiresAt = 0;

    /** Last failure, so the diagnostics screen can say why nothing shows. */
    private static volatile String status = "not started";

    /**
     * The same, for the badge calls.
     *
     * Kept apart from `status` on purpose: the now playing calls run every few
     * seconds and would overwrite a badge failure within a tick of it
     * happening, which is exactly the message worth reading.
     */
    private static volatile String badgeStatus = "not started";

    /**
     * Why the last Mojang handshake failed, and nothing else.
     *
     * Friends used to read status() after a failed handshake, but the now
     * playing calls overwrite that field every few seconds - the friends screen
     * could show "handshake failed: ok" or somebody else's report error. The
     * handshake keeps its own line.
     */
    private static volatile String handshakeStatus = "";

    /** When the session was last renewed because a handshake was refused. */
    private static volatile long lastRecovery = 0;

    public static String status() { return status; }
    public static String badgeStatus() { return badgeStatus; }
    public static String handshakeStatus() { return handshakeStatus; }
    public static boolean hasToken() { return !token.isEmpty(); }

    private static HttpClient http() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    }

    // ---------------- identity ----------------

    /**
     * Gets a token, handshaking first if the current one has run out.
     *
     * Runs on a background thread only - joinServer is a network call and the
     * render thread must never wait on it.
     */
    private static synchronized String ensureToken() {
        // A minute of slack, so a token does not expire mid request
        if (!token.isEmpty() && System.currentTimeMillis() < tokenExpiresAt - 60_000) {
            return token;
        }

        Minecraft mc = Minecraft.getInstance();
        String name;
        try {
            name = mc.getUser().getName();
        } catch (Throwable t) {
            status = "no account in the running game";
            return "";
        }
        if (name == null || name.isEmpty()) {
            status = "no account name";
            return "";
        }

        String serverId = randomServerId();
        if (!joinWithRecovery(serverId)) {
            // joinServer already set the status
            return "";
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/np/session"))
                    .header("X-Space-Name", name)
                    .header("X-Space-Server", serverId)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();

            HttpResponse<String> response =
                    http().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                status = "session refused (" + response.statusCode() + "): "
                        + shorten(response.body());
                return "";
            }

            JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!body.has("token")) {
                status = "worker returned no token";
                return "";
            }

            token = body.get("token").getAsString();
            long seconds = body.has("expiresIn") ? body.get("expiresIn").getAsLong() : 3600;
            tokenExpiresAt = System.currentTimeMillis() + seconds * 1000L;
            status = "ok";
            return token;

        } catch (Throwable t) {
            status = "could not reach the worker: " + t.getMessage();
            return "";
        }
    }

    /**
     * Proves to Mojang that this account is here, and hands back the id used.
     *
     * Public because the friends worker needs exactly the same proof and there
     * is no sense in writing the reflection over authlib's joinServer twice -
     * it is the most version-sensitive code in this mod, and two copies would
     * mean two places to fix when it moves again.
     *
     * Returns the server id on success and an empty string on failure, with
     * the reason left in handshakeStatus().
     */
    public static String handshake() {
        String serverId = randomServerId();
        return joinWithRecovery(serverId) ? serverId : "";
    }

    /**
     * joinServer, and once more after renewing the session if Mojang refused.
     *
     * A refusal is most often a token that went stale while the game ran - the
     * launcher hands over one that lasts about a day. The session can be
     * renewed from the launcher's account without a restart (the same thing the
     * Accounts screen does), so one renewal and one more try costs nothing and
     * saves the person from quitting the game. Not for rate limits, which a new
     * token would not fix, and not more than once a minute.
     *
     * Background threads only: the renewal is waited for.
     */
    private static boolean joinWithRecovery(String serverId) {
        if (joinServer(serverId)) return true;
        if (!recoverable) return false;

        long now = System.currentTimeMillis();
        if (now - lastRecovery < 60_000) return false;
        lastRecovery = now;

        try {
            SpaceClient.LOGGER.info("Mojang refused the handshake - renewing the session once");
            boolean renewed = gg.spaceclient.session.SessionManager.refreshCurrent()
                    .get(25, java.util.concurrent.TimeUnit.SECONDS);
            if (!renewed) return false;
        } catch (Throwable t) {
            return false;
        }
        return joinServer(serverId);
    }

    /** Whether the last joinServer failure is one a fresh session could fix. */
    private static volatile boolean recoverable = false;

    /** The name Mojang will be asked about. */
    public static String accountName() {
        try {
            String name = Minecraft.getInstance().getUser().getName();
            return name == null ? "" : name;
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** A one time id for the handshake, in the shape Mojang accepts. */
    private static String randomServerId() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        StringBuilder out = new StringBuilder(32);
        for (byte b : bytes) out.append(String.format("%02x", b));
        return out.toString();
    }

    /**
     * Tells Mojang this account is joining a server with the given id.
     *
     * A direct call now. It used to hunt for the session service by reflection,
     * because one jar had to fit whatever authlib the game shipped; the mod is
     * built once per Minecraft version since, and every one of them has
     * Minecraft.services().sessionService().joinServer(uuid, token, serverId).
     * A compile error is a better way to learn that this moved than a friends
     * screen that never signs in.
     */
    private static boolean joinServer(String serverId) {
        recoverable = false;

        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.User user;
        String token;
        UUID profile;
        try {
            user = mc.getUser();
            token = user.getAccessToken();
            profile = user.getProfileId();
        } catch (Throwable t) {
            SpaceClient.LOGGER.warn("Could not read the game session", t);
            return failed("handshake failed: " + describe(t), false);
        }

        // An offline profile carries a placeholder token Mojang will never
        // accept. Said plainly, because "handshake failed" sends people
        // looking for a network problem that is not there.
        if (token == null || token.length() < 20) {
            return failed("offline profiles cannot use friends - sign in with Microsoft in the launcher", false);
        }

        String viaGame;
        boolean refusedByMojang = false;
        try {
            // One handshake at a time: the now playing session, the badge and
            // the friends list each need one, and Mojang counts them together
            synchronized (SpaceApi.class) {
                mc.services().sessionService().joinServer(profile, token, serverId);
            }
            handshakeStatus = "";
            return true;

        } catch (com.mojang.authlib.exceptions.InvalidCredentialsException e) {
            SpaceClient.LOGGER.warn("Mojang handshake refused the login", e);
            viaGame = "Mojang no longer accepts this login - sign in again in the launcher";
            refusedByMojang = true;
        } catch (Throwable t) {
            SpaceClient.LOGGER.warn("Mojang handshake failed through the game's session service", t);
            viaGame = describe(t);
        }

        String lower = viaGame.toLowerCase();
        boolean limited = lower.contains("ratelimit") || lower.contains("429") || lower.contains("too many");
        if (limited) return failed("handshake failed: " + viaGame, false);

        // The game's session service is a layer between this mod and Mojang
        // that changes with the game version, and it can fail for reasons that
        // are its own - a service object built without the session server, a
        // renamed call. The handshake itself is one small request, so the same
        // request is made directly. If that works the friends list works, and
        // if it does not, Mojang's own answer is the reason worth showing.
        String direct = directJoin(token, profile, serverId);
        if (direct.isEmpty()) {
            SpaceClient.LOGGER.info("Handshake went through directly after the game's session service failed: {}", viaGame);
            handshakeStatus = "";
            return true;
        }

        boolean refused = refusedByMojang || lower.contains("invalid") || lower.contains("forbidden")
                || lower.contains("403") || lower.contains("401")
                || lower.contains("unauthorized") || lower.contains("expired")
                || direct.contains("HTTP 401") || direct.contains("HTTP 403");
        return failed("handshake failed: " + viaGame + " | direct: " + direct, refused);
    }

    /**
     * The handshake as Mojang documents it, without the game's classes: the
     * account's token, its profile id without dashes and the server id, posted
     * to the session server, which answers 204 when it accepted them.
     *
     * @return an empty string on success, otherwise what went wrong
     */
    private static String directJoin(String token, UUID profile, String serverId) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("accessToken", token);
            body.addProperty("selectedProfile", profile.toString().replace("-", ""));
            body.addProperty("serverId", serverId);

            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("https://sessionserver.mojang.com/session/minecraft/join"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> response = http().send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code == 204 || code == 200) return "";
            return "HTTP " + code + " " + shorten(response.body());
        } catch (Throwable t) {
            return describe(t);
        }
    }

    /** Records why a handshake failed, in both places that report it. */
    private static boolean failed(String reason, boolean fixableByNewSession) {
        status = reason;
        handshakeStatus = reason;
        recoverable = fixableByNewSession;
        return false;
    }

    /**
     * What went wrong, as the whole chain of causes with class names.
     *
     * Only the innermost message used to be kept, and for authlib that is often
     * an empty or generic line - the screen said "handshake failed" and nothing
     * a person could act on or report.
     */
    private static String describe(Throwable t) {
        StringBuilder out = new StringBuilder();
        Throwable current = t;
        for (int depth = 0; current != null && depth < 4; depth++) {
            if (depth > 0) out.append(" <- ");
            out.append(current.getClass().getSimpleName());
            String message = current.getMessage();
            if (message != null && !message.isBlank()) out.append(": ").append(message);
            current = current.getCause() == current ? null : current.getCause();
        }
        String clean = out.toString().replaceAll("\\s+", " ").trim();
        return clean.length() > 220 ? clean.substring(0, 220) : clean;
    }

    private static String dashed(String raw) {
        return raw.substring(0, 8) + "-" + raw.substring(8, 12) + "-"
                + raw.substring(12, 16) + "-" + raw.substring(16, 20) + "-"
                + raw.substring(20);
    }

    // ---------------- presence ----------------

    /**
     * Announces that this account runs Space Client.
     *
     * Carries the same token the now playing calls use, and nothing else. The
     * worker reads the identity out of that token rather than believing a uuid
     * in the body, which means a registration cannot be forged for somebody
     * who never installed the mod.
     *
     * An earlier attempt sent uuid and name for the worker to check against
     * Mojang's profile API. That check runs on Cloudflare, and Mojang answers
     * Cloudflare with a block page - so it always failed and nobody was ever
     * registered. The token costs nothing extra here: the mod holds one
     * already for reporting songs.
     *
     * Returns whether the worker accepted it, so the caller knows whether to
     * wait the full interval or retry sooner.
     */
    public static boolean register() {
        String bearer = ensureToken();
        if (bearer.isEmpty()) return false;

        JsonObject body = new JsonObject();
        body.addProperty("version", SpaceClient.VERSION);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/register"))
                    .header("Authorization", "Bearer " + bearer)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response =
                    http().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 401) {
                // The token died early; the next attempt mints a fresh one
                token = "";
                tokenExpiresAt = 0;
                badgeStatus = "token expired";
                return false;
            }
            if (response.statusCode() != 200) {
                badgeStatus = "register refused (" + response.statusCode() + "): "
                        + shorten(response.body());
                return false;
            }

            badgeStatus = "registered";
            return true;

        } catch (Throwable t) {
            badgeStatus = "register failed: " + t.getMessage();
            return false;
        }
    }

    /**
     * The session token, for the streaming calls in Twitch.
     *
     * Exposed rather than duplicating the handshake there: one place mints
     * tokens, one place holds them, and a token refreshed for a now playing
     * report is the same token the follower poll needs.
     */
    public static String tokenForStreaming() {
        try {
            return ensureToken();
        } catch (Throwable t) {
            return "";
        }
    }

    /** The shared HTTP client, so callers do not each build their own. */
    public static java.net.http.HttpClient client() { return http(); }

    /**
     * Heartbeat: tells the worker this account is in a world right now.
     *
     * The badge means "is playing with Space Client", not "installed it once",
     * and those are different questions needing different answers. Registration
     * records the permanent fact and is written at most twice a day; this
     * records the temporary one and expires on its own after fifteen minutes,
     * because a client that crashes never gets to say it left.
     */
    public static boolean heartbeat() {
        String bearer = ensureToken();
        if (bearer.isEmpty()) return false;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/presence"))
                    .header("Authorization", "Bearer " + bearer)
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response =
                    http().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 401) {
                token = "";
                tokenExpiresAt = 0;
                badgeStatus = "token expired";
                return false;
            }
            if (response.statusCode() != 200) {
                badgeStatus = "presence refused (" + response.statusCode() + ")";
                return false;
            }

            badgeStatus = "online";
            return true;

        } catch (Throwable t) {
            badgeStatus = "presence failed: " + t.getMessage();
            return false;
        }
    }

    /**
     * Says this account has stopped playing.
     *
     * Not strictly required - presence expires by itself - but without it the
     * badge hangs around for up to a quarter of an hour after someone quits,
     * which is exactly the wrong impression for a badge that claims to mean
     * "playing right now".
     */
    public static void leave() {
        String bearer = token;
        if (bearer == null || bearer.isEmpty()) return;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/presence"))
                    .header("Authorization", "Bearer " + bearer)
                    .timeout(Duration.ofSeconds(5))
                    .method("DELETE", HttpRequest.BodyPublishers.noBody())
                    .build();
            http().send(request, HttpResponse.BodyHandlers.ofString());
            badgeStatus = "offline";
        } catch (Throwable ignored) {
            // Leaving is best effort: the entry expires anyway
        }
    }

    /**
     * The full list of accounts carrying a badge.
     *
     * Everyone, not just the players in sight. The answer is identical for
     * every client and changes only when somebody installs the mod, so the
     * worker serves it from cache and one request replaces what would
     * otherwise be a lookup per player per poll.
     *
     * Returns null if the call did not get through, which the caller must not
     * confuse with an empty roster - the first means keep what you had, the
     * second means everyone lost their badge.
     */
    public static List<String> badgeUsers() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/users"))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    http().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                badgeStatus = "roster refused (" + response.statusCode() + ")";
                return null;
            }

            JsonObject parsed = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!parsed.has("users")) return null;

            JsonArray array = parsed.getAsJsonArray("users");
            List<String> users = new ArrayList<>(array.size());
            for (int i = 0; i < array.size(); i++) {
                users.add(array.get(i).getAsString());
            }
            return users;

        } catch (Throwable t) {
            badgeStatus = "roster failed: " + t.getMessage();
            return null;
        }
    }

    // ---------------- the two calls ----------------

    private static String shorten(String text) {
        if (text == null) return "";
        String clean = text.replaceAll("\\s+", " ").trim();
        return clean.length() > 120 ? clean.substring(0, 120) : clean;
    }

    private SpaceApi() {}
}
