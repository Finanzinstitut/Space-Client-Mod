package gg.spaceclient.input;

import gg.spaceclient.SpaceClient;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;

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
 * <h2>Codes</h2>
 *
 * Since 26.3 a key code is an SDL scancode (InputConstants.KEY_*), not a GLFW
 * key. The game stores bindings by name in options.txt, so a binding made on
 * 26.2 carries over; only the numbers this class hands around changed.
 */
public final class KeyBinds {

    public static final int UNBOUND = RawKeyboard.UNKNOWN;

    /** The keyboard code a mapping is currently bound to, or UNBOUND. */
    public static int codeOf(KeyMapping mapping) {
        if (mapping == null || mapping.isUnbound()) return UNBOUND;
        InputConstants.Key key = readKey(mapping);
        if (key == null || key.getType() != InputConstants.Type.KEYBOARD) return UNBOUND;
        return key.getValue();
    }

    /**
     * Binds a mapping to a keyboard code and makes the game notice.
     *
     * The second half matters as much as the first: the game keeps a map from
     * key to binding for the sake of speed, and a binding whose key changed
     * without that map being rebuilt still answers to the key it used to have.
     */
    public static boolean bind(KeyMapping mapping, int code) {
        if (mapping == null) return false;
        InputConstants.Key key = keyFor(code);
        try {
            mapping.setKey(key);
        } catch (Throwable t) {
            SpaceClient.LOGGER.warn("Could not rebind {}: {}", mapping.getName(), String.valueOf(t));
            return false;
        }
        KeyMapping.resetMapping();
        Minecraft mc = Minecraft.getInstance();
        // Written straight away so the binding survives the next start
        if (mc != null && mc.options != null) mc.options.save();
        return true;
    }

    private static InputConstants.Key keyFor(int code) {
        if (code == UNBOUND || code <= 0) return InputConstants.UNKNOWN;
        return InputConstants.Type.KEYBOARD.getOrCreate(code);
    }

    /** The mapping's key; the field is protected, so it is read directly. */
    private static InputConstants.Key readKey(KeyMapping mapping) {
        Class<?> current = mapping.getClass();
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                if (field.getType() != InputConstants.Key.class) continue;
                try {
                    field.setAccessible(true);
                    return (InputConstants.Key) field.get(mapping);
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
     * asked first. The table below is only for when it has no name.
     */
    public static String label(int code) {
        if (code == UNBOUND) return "None";
        try {
            String found = keyFor(code).getDisplayName().getString();
            if (found != null && !found.isEmpty() && !found.startsWith("key.")) return found;
        } catch (Throwable ignored) {
            // Fall back to the table
        }
        return fallbackLabel(code);
    }

    private static String fallbackLabel(int code) {
        if (code >= InputConstants.KEY_A && code <= InputConstants.KEY_Z) {
            return String.valueOf((char) ('A' + (code - InputConstants.KEY_A)));
        }
        if (code >= InputConstants.KEY_1 && code <= InputConstants.KEY_9) {
            return String.valueOf((char) ('1' + (code - InputConstants.KEY_1)));
        }
        if (code == InputConstants.KEY_0) return "0";
        if (code >= InputConstants.KEY_F1 && code <= InputConstants.KEY_F12) {
            return "F" + (code - InputConstants.KEY_F1 + 1);
        }
        return switch (code) {
            case InputConstants.KEY_SPACE -> "Space";
            case InputConstants.KEY_RETURN -> "Enter";
            case InputConstants.KEY_TAB -> "Tab";
            case InputConstants.KEY_LSHIFT -> "Left Shift";
            case InputConstants.KEY_RSHIFT -> "Right Shift";
            case InputConstants.KEY_LCONTROL -> "Left Ctrl";
            case InputConstants.KEY_RCONTROL -> "Right Ctrl";
            case InputConstants.KEY_LALT -> "Left Alt";
            case InputConstants.KEY_RALT -> "Right Alt";
            case InputConstants.KEY_INSERT -> "Insert";
            case InputConstants.KEY_HOME -> "Home";
            case InputConstants.KEY_END -> "End";
            case InputConstants.KEY_PAGEUP -> "Page Up";
            case InputConstants.KEY_PAGEDOWN -> "Page Down";
            default -> "Key " + code;
        };
    }

    // ---------------------------------------------------------------- catching

    /**
     * The scancodes worth scanning for a press: SDL numbers letters from 4 and
     * the modifiers end at 231 (right GUI). Everything a binding is made of
     * sits between.
     */
    private static final int FIRST_KEY = InputConstants.KEY_A;
    private static final int LAST_KEY = InputConstants.KEY_RGUI;

    /**
     * The key being held right now, or UNBOUND if none is.
     *
     * Read from the keyboard state rather than waiting for a key event: a
     * screen's key handler has changed shape between versions, and a handler
     * whose signature no longer matches is one nothing calls - a binding row
     * that silently refuses to catch anything.
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
