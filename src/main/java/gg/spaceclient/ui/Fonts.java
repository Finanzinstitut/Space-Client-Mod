package gg.spaceclient.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

/**
 * Which typeface the interface draws in.
 *
 * This used to swap the game's Font object at runtime, and that was the whole
 * trouble: it never reached every screen, and it ran after the title screen had
 * already drawn, so some text stayed on the old font and the start-up looked
 * unchanged. Swapping a live Font is a hack, and it behaved like one.
 *
 * The font is now a bundled resource pack (`resourcepacks/doodle`), toggled on
 * and off through Fabric. A resource pack replaces the font the way the game
 * loads it - so it covers everything, the title screen included, and there is
 * nothing left for this class to fight. `ui()` just hands back the game's
 * current font, whatever the pack made it.
 */
public final class Fonts {

    /** The pack id, matched by FontPacks when it registers the bundled pack. */
    public static final String DOODLE_PACK = "doodle";

    private static String status = "not applied";

    /**
     * The font to draw with.
     *
     * Simply the game's own font. With the pack on, that is Doodle everywhere
     * already; with it off, the pixel font. One source of truth, no second path
     * that could disagree.
     */
    public static Font ui() {
        return Minecraft.getInstance().font;
    }

    /** Applies the saved choice by enabling or disabling the pack. */
    public static void apply() {
        try {
            boolean wantDoodle = "DOODLE".equals(
                    gg.spaceclient.SpaceClient.getSettings().fontStyle());
            FontPacks.setEnabled(wantDoodle);
            status = wantDoodle ? "doodle pack on" : "minecraft (pack off)";
        } catch (Throwable t) {
            status = "failed: " + t.getMessage();
        }
    }

    /** Re-applies after a settings change. */
    public static void invalidate() { apply(); }

    public static String status() { return status; }

    private Fonts() {}
}
