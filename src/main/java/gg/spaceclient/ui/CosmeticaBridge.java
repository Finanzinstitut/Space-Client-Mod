package gg.spaceclient.ui;

import gg.spaceclient.SpaceClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Opens Cosmetica's own cosmetics menu from the Space Client sidebar.
 *
 * Reached by reflection rather than by importing the class, and that is the
 * whole point: Cosmetica is a separate mod that a given player may or may not
 * have installed. A direct import would make it a hard build dependency and
 * refuse to compile without it; this way the entry simply explains itself when
 * the mod is absent, and the client works either way.
 *
 * Cosmetica handles its own account, its own catalogue and its own rendering.
 * Nothing here mirrors any of that - the sidebar entry is a door, not a copy.
 */
public final class CosmeticaBridge {

    /** The menu Cosmetica opens for itself; no-argument constructor. */
    private static final String HOME_SCREEN = "cc.cosmetica.cosmetica.gui.HomeScreen";

    private static Boolean present = null;

    private CosmeticaBridge() {}

    /** Whether Cosmetica is loaded, checked once and remembered. */
    public static boolean installed() {
        if (present == null) {
            try {
                Class.forName(HOME_SCREEN);
                present = true;
            } catch (Throwable ignored) {
                present = false;
            }
        }
        return present;
    }

    /**
     * Opens Cosmetica's menu, or an explanation if it is not installed.
     *
     * The explanation matters: a sidebar entry that does nothing when clicked
     * looks like a bug in Space Client, when the actual situation is a missing
     * mod the player can go and install.
     */
    public static void open(Screen parent) {
        Minecraft mc = Minecraft.getInstance();

        if (!installed()) {
            mc.gui.setScreen(new CosmeticaMissingScreen(parent));
            return;
        }

        try {
            Object screen = Class.forName(HOME_SCREEN)
                    .getConstructor()
                    .newInstance();
            mc.gui.setScreen((Screen) screen);
        } catch (Throwable t) {
            SpaceClient.LOGGER.warn("Could not open the Cosmetica menu", t);
            mc.gui.setScreen(new CosmeticaMissingScreen(parent));
        }
    }
}
