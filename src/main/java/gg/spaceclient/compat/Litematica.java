package gg.spaceclient.compat;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.platform.Platform;

import net.minecraft.world.level.Level;

import java.lang.reflect.Method;

/**
 * The two things this mod asks of Litematica, looked up by name.
 *
 * Litematica is optional and Fabric only, and it is not on the build path:
 * the world it keeps the placed schematics in is an ordinary Level, so once
 * that one object is in hand everything else is the game's own API. Both
 * names used here - getSchematicWorld and EasyPlaceUtils.setHandling - are
 * the same from Litematica for 1.21.11 to the one for 26.3, and as
 * Litematica's own names they are never remapped.
 */
public final class Litematica {

    private static boolean looked = false;
    private static Method world = null;
    private static Method setHandling = null;

    private Litematica() {}

    private static void look() {
        if (looked) return;
        looked = true;
        if (!Platform.isModLoaded("litematica")) return;
        try {
            world = Class.forName("fi.dy.masa.litematica.world.SchematicWorldHandler").getMethod("getSchematicWorld");
        } catch (Throwable t) {
            SpaceClient.LOGGER.warn("Litematica is installed but its schematic world was not found: {}", t.toString());
        }
        try {
            setHandling = Class.forName("fi.dy.masa.litematica.util.EasyPlaceUtils").getMethod("setHandling", boolean.class);
        } catch (Throwable ignored) {
            // Only needed to open a container without Easy Place taking the click
        }
    }

    /** Whether Litematica is here and its schematic world can be read. */
    public static boolean available() {
        look();
        return world != null;
    }

    /** The world the schematic placements are rendered from, or null. */
    public static Level schematicWorld() {
        look();
        if (world == null) return null;
        try {
            return world.invoke(null) instanceof Level level ? level : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Runs a click with Easy Place standing aside. Easy Place takes over every
     * use of a block while it is on, and would turn a click meant to open a
     * chest into placing the next block; while it believes it is already
     * handling a click it lets the game have it.
     */
    public static void withoutEasyPlace(Runnable action) {
        look();
        boolean set = false;
        try {
            if (setHandling != null) {
                setHandling.invoke(null, true);
                set = true;
            }
        } catch (Throwable ignored) {
            // Then the click just goes through Easy Place
        }
        try {
            action.run();
        } finally {
            if (set) {
                try {
                    setHandling.invoke(null, false);
                } catch (Throwable ignored) {
                    // Nothing else to do
                }
            }
        }
    }
}
