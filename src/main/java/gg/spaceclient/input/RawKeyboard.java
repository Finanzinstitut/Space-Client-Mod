package gg.spaceclient.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

import org.lwjgl.sdl.SDLMouse;

import java.nio.FloatBuffer;

/**
 * Reads the physical keyboard, independent of what each key is bound to.
 *
 * Key bindings are the wrong source for a keyboard display: a player with
 * hotbar slot 5 on F should see F light up when they press F, not see nothing
 * because "F" is not a control the game calls F.
 *
 * Since 26.3 Minecraft runs on SDL3 rather than GLFW, and asks SDL for the
 * keyboard state itself through InputConstants.isKeyDown. The codes are SDL
 * scancodes - the physical key, whatever the layout - which is exactly what a
 * keyboard display wants. Going through the game's own call rather than SDL
 * directly keeps this working however the game sets SDL up.
 */
public final class RawKeyboard {

    /** What a key with no code reads as. */
    public static final int UNKNOWN = -1;

    private static volatile boolean ready = false;

    /**
     * The input system must not be asked anything before the game has started
     * it. The flag is set from the first client tick, by which point the window
     * certainly exists.
     */
    public static void markReady() {
        ready = true;
    }

    public static boolean isDown(int key) {
        if (!ready || key <= 0) return false;
        try {
            return InputConstants.isKeyDown(key);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isAvailable() {
        return ready;
    }

    /**
     * A mouse button by InputConstants.MOUSE_BUTTON_* number, which since 26.3
     * are SDL's own button numbers (left 1, middle 2, right 3).
     *
     * Asked of SDL directly rather than of the game's MouseHandler: the game
     * only records its pressed flags while no screen is open, so inside the HUD
     * editor it reported every button as up and elements could not be dragged.
     * SDL's state is the real one, in a screen or not.
     */
    public static boolean isMouseDown(int button) {
        if (!ready || button < 1 || button > 32) return false;
        try {
            int buttons = SDLMouse.SDL_GetMouseState((FloatBuffer) null, (FloatBuffer) null);
            return (buttons & (1 << (button - 1))) != 0;
        } catch (Throwable t) {
            // SDL not reachable for some reason: the game's own view, which is
            // right in game even if it is blind inside screens
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.mouseHandler == null) return false;
            return switch (button) {
                case InputConstants.MOUSE_BUTTON_LEFT -> mc.mouseHandler.isLeftPressed();
                case InputConstants.MOUSE_BUTTON_MIDDLE -> mc.mouseHandler.isMiddlePressed();
                case InputConstants.MOUSE_BUTTON_RIGHT -> mc.mouseHandler.isRightPressed();
                default -> false;
            };
        }
    }

    public static int codeFor(String label) {
        return switch (label) {
            case "A" -> InputConstants.KEY_A;
            case "B" -> InputConstants.KEY_B;
            case "C" -> InputConstants.KEY_C;
            case "D" -> InputConstants.KEY_D;
            case "E" -> InputConstants.KEY_E;
            case "F" -> InputConstants.KEY_F;
            case "G" -> InputConstants.KEY_G;
            case "H" -> InputConstants.KEY_H;
            case "I" -> InputConstants.KEY_I;
            case "J" -> InputConstants.KEY_J;
            case "K" -> InputConstants.KEY_K;
            case "L" -> InputConstants.KEY_L;
            case "M" -> InputConstants.KEY_M;
            case "N" -> InputConstants.KEY_N;
            case "O" -> InputConstants.KEY_O;
            case "P" -> InputConstants.KEY_P;
            case "Q" -> InputConstants.KEY_Q;
            case "R" -> InputConstants.KEY_R;
            case "S" -> InputConstants.KEY_S;
            case "T" -> InputConstants.KEY_T;
            case "U" -> InputConstants.KEY_U;
            case "V" -> InputConstants.KEY_V;
            case "W" -> InputConstants.KEY_W;
            case "X" -> InputConstants.KEY_X;
            case "Y" -> InputConstants.KEY_Y;
            case "Z" -> InputConstants.KEY_Z;
            case "1" -> InputConstants.KEY_1;
            case "2" -> InputConstants.KEY_2;
            case "3" -> InputConstants.KEY_3;
            case "4" -> InputConstants.KEY_4;
            case "5" -> InputConstants.KEY_5;
            case "6" -> InputConstants.KEY_6;
            case "7" -> InputConstants.KEY_7;
            case "8" -> InputConstants.KEY_8;
            case "9" -> InputConstants.KEY_9;
            case "0" -> InputConstants.KEY_0;
            case "TAB" -> InputConstants.KEY_TAB;
            case "CAPS" -> InputConstants.KEY_CAPSLOCK;
            case "SHIFT" -> InputConstants.KEY_LSHIFT;
            case "CTRL" -> InputConstants.KEY_LCONTROL;
            case "ALT" -> InputConstants.KEY_LALT;
            case "SPACE" -> InputConstants.KEY_SPACE;
            case "ESC" -> InputConstants.KEY_ESCAPE;
            case "ENTER" -> InputConstants.KEY_RETURN;
            case "F1" -> InputConstants.KEY_F1;
            case "F2" -> InputConstants.KEY_F2;
            case "F3" -> InputConstants.KEY_F3;
            case "F4" -> InputConstants.KEY_F4;
            case "F5" -> InputConstants.KEY_F5;
            default -> UNKNOWN;
        };
    }

    private RawKeyboard() {}
}
