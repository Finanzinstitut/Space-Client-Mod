package gg.spaceclient.ui;

import gg.spaceclient.SpaceClient;

/**
 * Colours lifted from the Space Client launcher so the in-game menu and the
 * launcher read as one product. Every value is ARGB - a plain RGB value renders
 * fully transparent in this version.
 */
public final class Theme {
    // --- launcher palette ---
    public static final int BG_DEEP   = 0xFF05040F; // --bg-deep
    public static final int BG_MID    = 0xFF1A1440; // radial highlight
    public static final int PANEL     = 0xE00D0B23; // --bg-panel
    public static final int PANEL_ALT = 0xFF14113A;
    public static final int BORDER    = 0xFF2A2555; // --border
    public static final int TEXT      = 0xFFE9E6FF; // --text
    public static final int TEXT_DIM  = 0xFF9A95C9; // --text-dim
    public static final int CYAN      = 0xFF38E0FF; // --accent-2
    public static final int OFF       = 0xFF3A3560;

    public static int backdrop() {
        return switch (SpaceClient.getSettings().backgroundStyle()) {
            case "SOLID_BLACK" -> 0xFF000000;
            case "TRANSPARENT" -> 0x66000000;
            case "DARK" -> 0xF00A0A12;
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
