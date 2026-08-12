package gg.spaceclient.session;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.spaceclient.SpaceClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Mints a fresh Minecraft session and puts it into the running game.
 *
 * Why this exists: a session token lasts about a day. Leave the game open
 * longer than that and joining a server fails with "invalid session", which
 * normally means quitting and relaunching. Re-running the token chain and
 * swapping the result in avoids the restart entirely.
 *
 * The swap uses reflection rather than a mixin, and finds the field by its type
 * rather than its name. Field names move between versions; the type does not.
 * A failure here is a message in the log, not a broken game.
 */
public class SessionManager {
    private static final String AZURE_CLIENT_ID = "74f6b1c6-c83b-425b-ae42-573992624ab2";
    private static final String TOKEN_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String XBL_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN_URL =
            "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE_URL =
            "https://api.minecraftservices.com/minecraft/profile";

    private static String status = "";
    private static boolean busy = false;

    public static String status() { return status; }
    public static boolean isBusy() { return busy; }

    private static HttpClient http() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    }

    /**
     * Refreshes the given account and applies it to the running game.
     * Runs off the render thread; the result lands in {@link #status()}.
     */
    public static CompletableFuture<Boolean> applyAccount(LauncherAccount account) {
        if (busy) return CompletableFuture.completedFuture(false);
        busy = true;
        status = "Signing in as " + account.username() + "...";

        return CompletableFuture.supplyAsync(() -> {
            try {
                if (account.offline()) {
                    boolean ok = swapUser(account.username(), account.uuid(), "0", "legacy");
                    status = ok
                            ? "Switched to " + account.username() + " (offline)"
                            : "Could not apply the offline profile.";
                    return ok;
                }

                String token = mintSession(account);
                if (token == null) {
                    status = "Could not refresh the session. Sign in again in the launcher.";
                    return false;
                }

                // The profile call confirms the token works and gives the exact
                // name and UUID the session belongs to.
                JsonObject profile = fetchProfile(token);
                if (profile == null) {
                    status = "The new session was rejected by Minecraft services.";
                    return false;
                }

                String name = profile.get("name").getAsString();
                String uuid = dashed(profile.get("id").getAsString());

                boolean ok = swapUser(name, uuid, token, "msa");
                status = ok
                        ? "Session refreshed - you are now " + name
                        : "Got a session but could not apply it to the running game.";
                return ok;

            } catch (Exception e) {
                SpaceClient.LOGGER.warn("Session refresh failed", e);
                status = "Session refresh failed: " + e.getMessage();
                return false;
            } finally {
                busy = false;
            }
        });
    }

    /** Refreshes whichever account the launcher currently has active. */
    public static CompletableFuture<Boolean> refreshCurrent() {
        String active = LauncherAccounts.activeUuid();
        Optional<LauncherAccount> account = LauncherAccounts.load().stream()
                .filter(a -> a.uuid().equalsIgnoreCase(active))
                .findFirst();

        if (account.isEmpty()) {
            // Fall back to whichever account matches the name we are playing as
            String current = Minecraft.getInstance().getUser().getName();
            account = LauncherAccounts.load().stream()
                    .filter(a -> a.username().equalsIgnoreCase(current))
                    .findFirst();
        }

        if (account.isEmpty()) {
            status = "No matching account found in the launcher.";
            return CompletableFuture.completedFuture(false);
        }
        return applyAccount(account.get());
    }

    // ---------------- token chain ----------------

    private static String mintSession(LauncherAccount account) throws Exception {
        // A token that is still valid needs no round trip
        if (!account.isExpired() && !account.accessToken().isEmpty()) {
            return account.accessToken();
        }
        if (account.refreshToken().isEmpty()) return null;

        String body = "client_id=" + AZURE_CLIENT_ID
                + "&grant_type=refresh_token"
                + "&refresh_token=" + java.net.URLEncoder.encode(
                        account.refreshToken(), java.nio.charset.StandardCharsets.UTF_8)
                + "&scope=" + java.net.URLEncoder.encode(
                        "XboxLive.signin offline_access", java.nio.charset.StandardCharsets.UTF_8);

        JsonObject microsoft = postForm(TOKEN_URL, body);
        if (microsoft == null || !microsoft.has("access_token")) return null;
        String msToken = microsoft.get("access_token").getAsString();

        JsonObject xbl = postJson(XBL_URL, String.format("""
                {"Properties":{"AuthMethod":"RPS","SiteName":"user.auth.xboxlive.com",
                "RpsTicket":"d=%s"},"RelyingParty":"http://auth.xboxlive.com","TokenType":"JWT"}""",
                msToken), null);
        if (xbl == null || !xbl.has("Token")) return null;
        String xblToken = xbl.get("Token").getAsString();

        JsonObject xsts = postJson(XSTS_URL, String.format("""
                {"Properties":{"SandboxId":"RETAIL","UserTokens":["%s"]},
                "RelyingParty":"rp://api.minecraftservices.com/","TokenType":"JWT"}""",
                xblToken), null);
        if (xsts == null || !xsts.has("Token")) return null;

        String xstsToken = xsts.get("Token").getAsString();
        String userHash = xsts.getAsJsonObject("DisplayClaims")
                .getAsJsonArray("xui").get(0).getAsJsonObject()
                .get("uhs").getAsString();

        JsonObject minecraft = postJson(MC_LOGIN_URL, String.format(
                "{\"identityToken\":\"XBL3.0 x=%s;%s\"}", userHash, xstsToken), null);
        if (minecraft == null || !minecraft.has("access_token")) return null;

        return minecraft.get("access_token").getAsString();
    }

    private static JsonObject fetchProfile(String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(MC_PROFILE_URL))
                .header("Authorization", "Bearer " + token)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = http().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) return null;
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static JsonObject postForm(String url, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(12))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = http().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            SpaceClient.LOGGER.warn("{} returned {}", url, response.statusCode());
            return null;
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static JsonObject postJson(String url, String body, String bearer) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(12))
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (bearer != null) builder.header("Authorization", "Bearer " + bearer);

        HttpResponse<String> response = http().send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            SpaceClient.LOGGER.warn("{} returned {}", url, response.statusCode());
            return null;
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    // ---------------- applying it ----------------

    /**
     * Replaces Minecraft's User instance. The field is located by type, so a
     * rename in a future version does not break it.
     */
    private static boolean swapUser(String name, String uuid, String token, String type) {
        try {
            Minecraft mc = Minecraft.getInstance();
            User replacement = buildUser(name, uuid, token, type);
            if (replacement == null) return false;

            for (Field field : Minecraft.class.getDeclaredFields()) {
                if (!User.class.equals(field.getType())) continue;
                field.setAccessible(true);
                field.set(mc, replacement);
                SpaceClient.LOGGER.info("Applied a new session for {}", name);
                return true;
            }
            SpaceClient.LOGGER.warn("No User field found on Minecraft - cannot apply the session");
            return false;

        } catch (Throwable t) {
            SpaceClient.LOGGER.warn("Could not swap the session", t);
            return false;
        }
    }

    /**
     * Builds a User without depending on its constructor signature, which has
     * gained and lost parameters repeatedly across versions. Whichever
     * constructor is present is filled in positionally by parameter type.
     */
    private static User buildUser(String name, String uuid, String token, String type) {
        for (var constructor : User.class.getDeclaredConstructors()) {
            try {
                Class<?>[] parameters = constructor.getParameterTypes();
                Object[] arguments = new Object[parameters.length];
                int stringsSeen = 0;

                for (int i = 0; i < parameters.length; i++) {
                    Class<?> parameter = parameters[i];

                    if (parameter == String.class) {
                        // Order in every known version: name, then uuid, then token
                        arguments[i] = switch (stringsSeen++) {
                            case 0 -> name;
                            case 1 -> uuid;
                            case 2 -> token;
                            default -> "";
                        };
                    } else if (parameter == UUID.class) {
                        arguments[i] = UUID.fromString(uuid);
                    } else if (parameter == Optional.class) {
                        arguments[i] = Optional.empty();
                    } else if (parameter.isEnum()) {
                        Object[] constants = parameter.getEnumConstants();
                        Object match = constants[0];
                        for (Object constant : constants) {
                            if (constant.toString().equalsIgnoreCase(type)) match = constant;
                        }
                        arguments[i] = match;
                    } else {
                        arguments[i] = null;
                    }
                }

                constructor.setAccessible(true);
                return (User) constructor.newInstance(arguments);

            } catch (Throwable ignored) {
                // Try the next constructor
            }
        }
        return null;
    }

    private static String dashed(String raw) {
        if (raw.length() != 32) return raw;
        return raw.substring(0, 8) + "-" + raw.substring(8, 12) + "-"
                + raw.substring(12, 16) + "-" + raw.substring(16, 20) + "-"
                + raw.substring(20);
    }

    private SessionManager() {}
}
