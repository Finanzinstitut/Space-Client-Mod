package gg.spaceclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Opening a screen and writing a line of chat.
 *
 * Both were renamed in this version, and both are now verified rather than
 * guessed. setScreen moved from Minecraft to Gui - every other screen in this
 * mod already called it that way. displayClientMessage was split in two:
 * the boolean that used to choose between chat and the hotbar overlay is now
 * the method name, so sendSystemMessage is the chat one.
 */
public final class Screens {

    /** Puts a screen on the display. */
    public static void open(Screen screen) {
        gg.spaceclient.compat.Screens.set(screen);
    }

    /**
     * The screen on display right now, or null if it cannot be read.
     *
     * Found by type rather than by name: something on Minecraft or on its Gui
     * holds the current Screen, and which one it is has moved between versions.
     * A field whose type is Screen is unambiguous in a way a getter's name is
     * not, and a version that moves it again finds it here without an edit.
     */
    public static Screen current() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return null;

        // Compiled against this version, so asked directly. The search below
        // used to run every tick from the title screen watcher - two failed
        // method lookups and a field scan, twenty times a second.
        return mc.gui != null ? gg.spaceclient.compat.Screens.current() : null;
    }

    /** Writes one line into the player's own chat. Nothing is sent anywhere. */
    public static void chat(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal(text));
    }

    private Screens() {}
}
