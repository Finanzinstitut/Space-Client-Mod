package gg.spaceclient.ui;

import gg.spaceclient.SpaceClient;

/**
 * The interface's colours: black, with white ink and one accent used sparingly.
 *
 * It used to be the launcher's violet-navy, carried over so the two read as one
 * product. That was the wrong thing to match. The menu sits on top of a game,
 * over whatever the world happens to look like, and a tinted panel competes
 * with it; a black one gets out of the way. Every value is ARGB - a plain RGB
 * value renders fully transparent in this version.
 */
public final class Theme {
    // --- surfaces, neutral by design ---
    public static final int BG_DEEP   = 0xFF000000;
    public static final int BG_MID    = 0xFF0C0C0E;
    public static final int PANEL     = 0xE60B0B0D;
    public static final int PANEL_ALT = 0xFF141416;
    public static final int BORDER    = 0xFF26262A;
    public static final int TEXT      = 0xFFF3F3F6;
    public static final int TEXT_DIM  = 0xFF97979F;

    /**
     * The "on" colour. Near white rather than a hue: in a black interface the
     * thing that reads as switched on is the thing that is brighter, and a
     * coloured highlight on every active row is what made the old menu look
     * tinted throughout.
     */
    public static final int CYAN      = 0xFFEDEDF2;
    public static final int OFF       = 0xFF3A3A40;

    // --- surfaces for the card layout ---
    public static final int SIDEBAR      = 0xF0070708;
    public static final int SIDEBAR_PICK = 0x24FFFFFF;
    public static final int CONTENT      = 0xE6060607;
    public static final int CARD         = 0xFF121214;
    public static final int CARD_HOVER   = 0xFF1B1B1F;

    /**
     * Chips sit on the bare content panel with nothing behind them, so they are
     * a step lighter than any card - a filter row that cannot be read is a
     * filter row nobody uses.
     */
    public static final int CHIP         = 0xFF1E1E22;
    public static final int CHIP_HOVER   = 0xFF2A2A30;
    public static final int CHIP_BORDER  = 0xFF3A3A42;
    public static final int CARD_FOOT    = 0xFF0A0A0C;
    public static final int BORDER_HOVER = 0xFF3C3C44;

    /** Text drawn on top of a filled accent strip, which is bright. */
    public static final int TEXT_ON_ACCENT = 0xFF08080A;

    public static int backdrop() {
        return switch (SpaceClient.getSettings().backgroundStyle()) {
            case "SOLID_BLACK" -> 0xFF000000;
            case "TRANSPARENT" -> 0x66000000;
            case "DARK" -> 0xF0070708;
            default -> BG_DEEP;
        };
    }

    /** The starfield background is the launcher's signature look. */
    public static boolean spaceBackdrop() {
        return SpaceClient.getSettings().backgroundStyle().equals("SPACE");
    }

    public static int accent() {
        return SpaceClient.getSettings().accentColor();
    }

    /** Dimmed accent for filled backgrounds behind text. */
    public static int accentDim() {
        int accent = accent();
        int r = ((accent >> 16) & 0xFF) / 4;
        int g = ((accent >> 8) & 0xFF) / 4;
        int b = (accent & 0xFF) / 4;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private Theme() {}
}
