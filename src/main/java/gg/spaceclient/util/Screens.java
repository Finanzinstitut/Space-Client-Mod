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
        Minecraft.getInstance().gui.setScreen(screen);
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

        Object viaGetter = gg.spaceclient.util.Reflect.call(mc.gui, "getScreen");
        if (viaGetter instanceof Screen screen) return screen;

        Screen fromGui = screenField(mc.gui);
        return fromGui != null ? fromGui : screenField(mc);
    }

    private static Screen screenField(Object host) {
        if (host == null) return null;
        for (Class<?> type = host.getClass(); type != null && type != Object.class;
             type = type.getSuperclass()) {
            for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                if (!Screen.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(host);
                    if (value instanceof Screen screen) return screen;
                } catch (Throwable ignored) {
                    // Sealed away, or not the one; keep looking
                }
            }
        }
        return null;
    }

    /** Writes one line into the player's own chat. Nothing is sent anywhere. */
    public static void chat(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal(text));
    }

    private Screens() {}
}
