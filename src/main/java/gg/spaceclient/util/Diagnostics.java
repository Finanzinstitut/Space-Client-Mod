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

        Method lineBox = findMethod("net.minecraft.client.renderer.LevelRenderer",
                "renderLineBox", 7);
        checks.add(new Check("Line box renderer", lineBox != null,
                lineBox != null ? "renderLineBox found" : "not found - boxes cannot be drawn"));

        checks.add(new Check("Hitbox drawing active",
                gg.spaceclient.render.HitboxRenderer.isAvailable(),
                gg.spaceclient.render.HitboxRenderer.isAvailable()
                        ? "custom boxes in use"
                        : "falling back to the game's own view"));

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

        checks.add(new Check("Playing as", true, mc.getUser().getName()));

        return checks;
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
