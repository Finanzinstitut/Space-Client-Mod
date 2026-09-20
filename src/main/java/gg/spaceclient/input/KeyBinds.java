package gg.spaceclient.input;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.util.Reflect;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reading and changing what a key binding is bound to.
 *
 * <h2>Why this is not stored in the client's own config</h2>
 *
 * The game already keeps key bindings, in options.txt, and it is the thing that
 * actually decides whether a key fires. Keeping a second copy here would mean
 * two answers to one question, and the first time somebody changed the binding
 * in the game's own controls screen they would disagree - with no rule for
 * which wins that is not arbitrary.
 *
 * So the binding stays the game's. This class only reaches it: the row in the
 * client's settings reads the game's value and writes the game's value, and the
 * controls screen keeps showing the truth because it is the same value.
 *
 * <h2>Reflection, as usual</h2>
 *
 * Nothing here has been compiled against. getKey returns an InputConstants.Key
 * on this version and returned a plain int on older ones; setting a binding is
 * a method on Options on some versions and on the mapping itself on others.
 * Each is tried in turn and the first that works is used.
 */
public final class KeyBinds {

    public static final int UNBOUND = GLFW.GLFW_KEY_UNKNOWN;

    /** The GLFW code a mapping is currently bound to, or UNBOUND. */
    public static int codeOf(KeyMapping mapping) {
        if (mapping == null) return UNBOUND;

        Object key = Reflect.call(mapping, "getKey", "key");
        if (key == null) key = readKeyField(mapping);
        if (key == null) return UNBOUND;

        // Older shape: the code itself
        if (key instanceof Number number) return number.intValue();

        Object value = Reflect.call(key, "getValue", "value");
        return value instanceof Number number ? number.intValue() : UNBOUND;
    }

    /**
     * Binds a mapping to a GLFW key code and makes the game notice.
     *
     * The second half matters as much as the first: the game keeps a map from
     * key to binding for the sake of speed, and a binding whose key changed
     * without that map being rebuilt still answers to the key it used to have.
     */
    public static boolean bind(KeyMapping mapping, int code) {
        if (mapping == null) return false;

        Object key = keyFor(code);
        if (key == null) return false;

        Minecraft mc = Minecraft.getInstance();

        // Through the options first: that is the version that also writes
        // options.txt, so the binding survives the next start
        boolean set = mc != null && mc.options != null
                && invokeWith(mc.options, "setKey", mapping, key);

        if (!set) set = invokeWith(mapping, "setKey", key);
        if (!set) {
            SpaceClient.LOGGER.warn("Could not rebind {} on this version", mapping);
            return false;
        }

        resetLookup();
        if (mc != null && mc.options != null) Reflect.call(mc.options, "save");
        return true;
    }

    /** An InputConstants.Key for a GLFW code, or null if it cannot be made. */
    private static Object keyFor(int code) {
        try {
            Class<?> constants = Class.forName("com.mojang.blaze3d.platform.InputConstants");

            if (code == UNBOUND) {
                Field unknown = constants.getField("UNKNOWN");
                return unknown.get(null);
            }

            // The named lookup knows about every key, including the ones whose
            // scan code matters, so it is preferred over building one by hand
            for (Method method : constants.getMethods()) {
                if (!method.getName().equals("getKey")) continue;
                if (method.getParameterCount() != 2) continue;
                return method.invoke(null, code, -1);
            }

            Class<?> type = Class.forName("com.mojang.blaze3d.platform.InputConstants$Type");
            Object keysym = type.getField("KEYSYM").get(null);
            Method create = type.getMethod("getOrCreate", int.class);
            return create.invoke(keysym, code);

        } catch (Throwable t) {
            SpaceClient.LOGGER.warn("Could not build a key for {}: {}", code, String.valueOf(t));
            return null;
        }
    }

    private static void resetLookup() {
        try {
            Method reset = KeyMapping.class.getMethod("resetMapping");
            reset.invoke(null);
        } catch (Throwable ignored) {
            // Nothing to rebuild on this version, or it is called something
            // else; the binding is still set either way
        }
    }

    /** Calls a method by name whose arguments are known exactly. */
    private static boolean invokeWith(Object target, String name, Object... args) {
        if (target == null) return false;

        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name)) continue;
            if (method.getParameterCount() != args.length) continue;

            Class<?>[] params = method.getParameterTypes();
            boolean fits = true;
            for (int i = 0; i < args.length; i++) {
                if (args[i] != null && !params[i].isInstance(args[i])) { fits = false; break; }
            }
            if (!fits) continue;

            try {
                method.setAccessible(true);
                method.invoke(target, args);
                return true;
            } catch (Throwable ignored) {
                // Try the next overload
            }
        }
        return false;
    }

    private static Object readKeyField(Object mapping) {
        Class<?> current = mapping.getClass();
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                if (!field.getType().getName().endsWith("InputConstants$Key")) continue;
                try {
                    field.setAccessible(true);
                    return field.get(mapping);
                } catch (Throwable ignored) {
                    return null;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    // ---------------------------------------------------------------- names

    /**
     * What to call a key on screen.
     *
     * The game can name every key in the player's own language, so that is
     * asked first. The table below is only for when it cannot be reached - it
     * covers what people actually bind things to rather than all 120 keys,
     * because a wrong name is worse than a plain one.
     */
    public static String label(int code) {
        if (code == UNBOUND) return "None";

        Object key = keyFor(code);
        if (key != null) {
            Object name = Reflect.call(key, "getDisplayName", "getName");
            if (name != null) {
                Object text = Reflect.call(name, "getString");
                if (text instanceof String found && !found.isEmpty() && !found.startsWith("key.")) {
                    return found;
                }
            }
        }
        return fallbackLabel(code);
    }

    private static String fallbackLabel(int code) {
        if (code >= GLFW.GLFW_KEY_A && code <= GLFW.GLFW_KEY_Z) {
            return String.valueOf((char) ('A' + (code - GLFW.GLFW_KEY_A)));
        }
        if (code >= GLFW.GLFW_KEY_0 && code <= GLFW.GLFW_KEY_9) {
            return String.valueOf((char) ('0' + (code - GLFW.GLFW_KEY_0)));
        }
        if (code >= GLFW.GLFW_KEY_F1 && code <= GLFW.GLFW_KEY_F25) {
            return "F" + (code - GLFW.GLFW_KEY_F1 + 1);
        }
        return switch (code) {
            case GLFW.GLFW_KEY_SPACE -> "Space";
            case GLFW.GLFW_KEY_ENTER -> "Enter";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "Left Shift";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "Right Shift";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "Left Ctrl";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "Right Ctrl";
            case GLFW.GLFW_KEY_LEFT_ALT -> "Left Alt";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "Right Alt";
            case GLFW.GLFW_KEY_INSERT -> "Insert";
            case GLFW.GLFW_KEY_HOME -> "Home";
            case GLFW.GLFW_KEY_END -> "End";
            case GLFW.GLFW_KEY_PAGE_UP -> "Page Up";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "Page Down";
            default -> "Key " + code;
        };
    }

    // ---------------------------------------------------------------- catching

    /** The first and last GLFW code worth scanning for a press. */
    private static final int FIRST_KEY = GLFW.GLFW_KEY_SPACE;
    private static final int LAST_KEY = GLFW.GLFW_KEY_LAST;

    /**
     * The key being held right now, or UNBOUND if none is.
     *
     * Read straight from GLFW rather than waiting for a key event, for the same
     * reason the rest of this client does: this version reworked key handling
     * onto an event object, and a hand written handler whose signature no
     * longer matches becomes a method nothing calls - a binding screen that
     * silently refuses to catch anything.
     */
    public static int pressedKey() {
        if (!RawKeyboard.isAvailable()) return UNBOUND;

        for (int code = FIRST_KEY; code <= LAST_KEY; code++) {
            if (RawKeyboard.isDown(code)) return code;
        }
        return UNBOUND;
    }

    private KeyBinds() {}
}
