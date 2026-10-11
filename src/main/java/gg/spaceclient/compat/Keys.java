package gg.spaceclient.compat;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;

/**
 * Keyboard and mouse state across the two input systems: GLFW up to 26.2,
 * SDL3 from 26.3. The InputConstants key numbers follow whichever one the
 * running game uses, so code that names keys through them needs nothing
 * more; only the type, the range and the questions below differ.
 */
public final class Keys {

    private Keys() {}

    /** The input type a keyboard binding carries. */
    //#if MC >= 26.3
    public static final InputConstants.Type KEYBOARD = InputConstants.Type.KEYBOARD;
    //#else
    //$$ public static final InputConstants.Type KEYBOARD = InputConstants.Type.KEYSYM;
    //#endif

    /** The span of key numbers worth scanning for a press. */
    //#if MC >= 26.3
    // SDL scancodes: letters from 4, the modifiers end at 231 (right GUI)
    public static final int FIRST_KEY = InputConstants.KEY_A;
    public static final int LAST_KEY = InputConstants.KEY_RGUI;
    //#else
    //$$ // GLFW key codes: space is the lowest printable key, menu the highest
    //$$ public static final int FIRST_KEY = InputConstants.KEY_SPACE;
    //$$ public static final int LAST_KEY = 348;
    //#endif

    public static boolean isKeyDown(int key) {
        //#if MC >= 26.3
        return InputConstants.isKeyDown(key);
        //#else
        //$$ return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key);
        //#endif
    }

    /**
     * Whether a mouse button is held, read from the input system itself
     * rather than from the game, which stops tracking buttons while a screen
     * is open. Buttons are InputConstants.MOUSE_BUTTON_* numbers.
     */
    public static boolean isMouseDown(int button) {
        //#if MC >= 26.3
        // SDL numbers buttons from 1 (left), as a bit mask
        if (button < 1 || button > 32) return false;
        int buttons = org.lwjgl.sdl.SDLMouse.SDL_GetMouseState((java.nio.FloatBuffer) null, (java.nio.FloatBuffer) null);
        return (buttons & (1 << (button - 1))) != 0;
        //#else
        //$$ // GLFW numbers buttons from 0 (left)
        //$$ if (button < 0 || button > 7) return false;
        //$$ long window = Minecraft.getInstance().getWindow().handle();
        //$$ return org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, button) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        //#endif
    }
}
