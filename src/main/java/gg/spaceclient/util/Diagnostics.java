package gg.spaceclient.util;

import gg.spaceclient.session.LauncherAccounts;

import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Reports which of the reflective lookups actually found something.
 *
 * Every feature that could not be verified against this Minecraft version goes
 * through reflection, and a failed lookup only writes to the log - which is no
 * help when the symptom is "it does nothing". This turns each lookup into a
 * line you can read in game, so a broken feature can be pinned down in one look
 * instead of another round of guessing.
 */
public final class Diagnostics {

    public record Check(String name, boolean ok, String detail) {}

    public static List<Check> run() {
        List<Check> checks = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();

        // --- world rendering, needed for custom hitboxes ---
        Class<?> events = findClass(
                "net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents",
                "net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents",
                "net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents");
        checks.add(new Check("World render event", events != null,
                events != null ? events.getSimpleName() : "no known class found"));

        Method lineBox = gg.spaceclient.render.HitboxRenderer.lineBoxMethod();
        checks.add(new Check("Line box renderer", lineBox != null,
                lineBox != null
                        ? lineBox.getDeclaringClass().getSimpleName() + ".renderLineBox"
                        : "not found in " + String.join(", ",
                                gg.spaceclient.render.HitboxRenderer.SHAPE_CLASSES)));

        boolean drawing = gg.spaceclient.render.HitboxRenderer.isAvailable();
        checks.add(new Check("Hitbox drawing active", drawing,
                drawing ? "custom boxes in use" : "falling back to the game's own view"));

        // Why the subscription failed, which the previous page did not show
        checks.add(new Check("Render subscription", drawing,
                gg.spaceclient.render.WorldRenderHook.failure()));

        // --- raw keyboard, needed for keystrokes and the zoom key ---
        boolean keyboard = gg.spaceclient.input.RawKeyboard.isAvailable();
        checks.add(new Check("Raw keyboard", keyboard,
                keyboard ? "window handle found" : "handle not found - keys fall back to bindings"));

        // --- field of view, needed for zoom ---
        Object fov = Reflect.call(mc.options, "fov", "getFov");
        checks.add(new Check("Field of view option", fov != null,
                fov != null ? fov.getClass().getSimpleName() : "accessor not found"));

        if (fov != null) {
            Object value = Reflect.call(fov, "get", "getValue", "value");
            checks.add(new Check("FOV value readable", value != null,
                    value != null
                            ? value + " (" + value.getClass().getSimpleName() + ")"
                            : "no getter matched"));
        }

        checks.add(new Check("Zoom renderer hook",
                gg.spaceclient.modules.ZoomModule.isMixinActive(),
                gg.spaceclient.modules.ZoomModule.isMixinActive()
                        ? "CameraMixin attached - real zoom"
                        : "not attached - falling back to the clamped option (check Camera.calculateFov exists)"));

        checks.add(new Check("Zoom write",
                !gg.spaceclient.modules.ZoomModule.lastResult().startsWith("write ignored"),
                gg.spaceclient.modules.ZoomModule.lastResult()));

        // --- the account object, needed for switching sessions ---
        Field userField = null;
        for (Field field : Minecraft.class.getDeclaredFields()) {
            if (field.getType() == net.minecraft.client.User.class) {
                userField = field;
                break;
            }
        }
        checks.add(new Check("Account field", userField != null,
                userField != null ? "Minecraft." + userField.getName() : "no User field on Minecraft"));

        int constructors = net.minecraft.client.User.class.getDeclaredConstructors().length;
        checks.add(new Check("Account constructors", constructors > 0,
                constructors + " available"));

        // --- launcher data, needed for account switching ---
        boolean accountsFile = LauncherAccounts.isAvailable();
        checks.add(new Check("Launcher accounts", accountsFile,
                accountsFile
                        ? LauncherAccounts.load().size() + " account(s) readable"
                        : "not found at " + LauncherAccounts.accountsFile()));

        // Can an account object actually be built on this version? A field that
        // exists is no use if nothing can be put in it.
        String shape = testBuildUser();
        checks.add(new Check("Account constructor shape", shape != null, shape));

        // Does the live account carry a real token? A name alone proves nothing.
        String liveToken = null;
        for (String accessor : new String[]{"getAccessToken", "accessToken", "getSessionId"}) {
            try {
                Object value = net.minecraft.client.User.class
                        .getMethod(accessor).invoke(mc.getUser());
                if (value instanceof String text) { liveToken = text; break; }
            } catch (Exception ignored) {
                // Try the next accessor
            }
        }
        boolean tokenLooksReal = liveToken != null && liveToken.length() > 40;
        checks.add(new Check("Login token", tokenLooksReal,
                liveToken == null ? "no accessor found"
                        : tokenLooksReal
                                ? liveToken.length() + " chars, ends " + liveToken.substring(liveToken.length() - 6)
                                : "only " + liveToken.length() + " chars - not a real token"));

        // Chat signing: a key from the previous account is what produces
        // "invalid_public_key_signature" on servers with secure profiles on
        String signing = "no manager on this version";
        for (var field : Minecraft.class.getDeclaredFields()) {
            if (field.getType().getSimpleName().contains("ProfileKeyPairManager")) {
                signing = field.getType().getSimpleName() + " present";
                break;
            }
        }
        checks.add(new Check("Chat signing", !signing.startsWith("no"), signing));

        checks.add(new Check("Session status", true,
                gg.spaceclient.session.SessionManager.status().isEmpty()
                        ? "nothing attempted yet"
                        : gg.spaceclient.session.SessionManager.status()));

        checks.add(new Check("Playing as", true, mc.getUser().getName()));

        // Which modules are on, since a module that is off simply does nothing
        StringBuilder enabled = new StringBuilder();
        for (var module : gg.spaceclient.SpaceClient.getModuleManager().getAll()) {
            if (module.isEnabled()) {
                if (enabled.length() > 0) enabled.append(", ");
                enabled.append(module.getName());
            }
        }
        checks.add(new Check("Enabled modules", enabled.length() > 0,
                enabled.length() > 0 ? enabled.toString() : "none are switched on"));

        return checks;
    }

    /** The shape of the User constructor, which is what the builder must fill. */
    private static String testBuildUser() {
        for (var constructor : net.minecraft.client.User.class.getDeclaredConstructors()) {
            StringBuilder shape = new StringBuilder();
            for (Class<?> parameter : constructor.getParameterTypes()) {
                if (shape.length() > 0) shape.append(", ");
                shape.append(parameter.getSimpleName());
            }
            // Reporting the shape is more useful than a yes or no, since it
            // shows exactly what the builder has to fill in.
            return "takes (" + shape + ")";
        }
        return "no constructors at all";
    }

    private static Class<?> findClass(String... names) {
        for (String name : names) {
            try {
                return Class.forName(name);
            } catch (Throwable ignored) {
                // Try the next
            }
        }
        return null;
    }

    private static Method findMethod(String className, String method, int parameters) {
        try {
            Class<?> type = Class.forName(className);
            for (Method candidate : type.getMethods()) {
                if (candidate.getName().equals(method)
                        && candidate.getParameterCount() == parameters) {
                    return candidate;
                }
            }
        } catch (Throwable ignored) {
            // Falls through to null
        }
        return null;
    }

    private Diagnostics() {}
}
