package gg.spaceclient.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The frame the settings screens share.
 *
 * Settings and Accounts are one errand - you go there to change something about
 * how the client behaves - and they were built as two unrelated screens. One
 * centred a large title over a two column grid on the wallpaper; the other put
 * a left aligned panel with its own icon and its own heading over the plain
 * backdrop, with rows of a different height in a column of a different width.
 * Moving between them read as moving between two programs.
 *
 * Nothing here is new drawing. It is the calls those two screens were already
 * making, in one place, so that changing how a settings screen looks is one
 * edit rather than a hunt - and so a third one cannot arrive with a third idea.
 */
public final class ScreenChrome {

    /** Where content starts, under the heading. */
    public static final int TOP = 88;

    /** The tile grid, which the account rows now also follow. */
    public static final int TILE_W = 230;
    public static final int TILE_H = 54;
    public static final int GAP = 10;

    private ScreenChrome() {}

    /** Wallpaper where it can be drawn, the drawn backdrop where it cannot. */
    public static void background(GuiGraphicsExtractor graphics, int width, int height,
                                  int mouseX, int mouseY, float delta) {
        if (!MenuWallpaper.draw(graphics, width, height, mouseX, mouseY, delta)) {
            Backdrop.draw(graphics, width, height);
        }
        // A wash over the picture so white text stays readable wherever the
        // drift happens to have put a bright patch.
        graphics.fill(0, 0, width, height, 0x60000000);
    }

    /**
     * The heading, at double size and centred, with one dim line under it.
     *
     * Scale.push can decline - it goes through the pose stack, which is one of
     * the things this mod reaches for reflectively - so the position is worked
     * out for both cases rather than assuming the transform took.
     */
    public static void header(GuiGraphicsExtractor graphics, Font font, int width,
                              String title, String subtitle) {
        int titleWidth = font.width(title) * 2;
        int x = (width - titleWidth) / 2;

        boolean scaled = Scale.push(graphics, x, 30, 2f);
        graphics.text(font, title, scaled ? 0 : x, scaled ? 0 : 30, 0xFFFFFFFF, false);
        if (scaled) Scale.pop(graphics);

        if (subtitle == null || subtitle.isEmpty()) return;
        graphics.text(font, subtitle,
                (width - font.width(subtitle)) / 2, 56, 0xFFB9B4DC, false);
    }

    /** Where the bottom row of buttons sits, measured from the bottom edge. */
    public static int bottomRow(int height) {
        return height - 34;
    }

    /**
     * How many rows of content fit between the heading and the given floor.
     *
     * Always at least one. A screen too short for a single row is not a screen
     * this can rescue, and returning zero would divide the caller by nothing.
     */
    public static int rowsBetween(int floor, int rowHeight) {
        return Math.max(1, (floor - TOP - GAP) / (rowHeight + GAP));
    }

    /** Two columns where two fit, one where they do not. */
    public static int columnsFor(int width) {
        return width >= TILE_W * 2 + GAP + 40 ? 2 : 1;
    }

    /** Left edge of a centred block of the given number of columns. */
    public static int blockLeft(int width, int columns) {
        return (width - (columns * TILE_W + (columns - 1) * GAP)) / 2;
    }

    /**
     * The entrance stagger, as a progress value for one item.
     *
     * Both screens animate their contents in; only one of them used to. Kept
     * here so the two arrive at the same speed, which is most of what makes
     * them feel like one place.
     */
    public static float appearAt(long elapsed, int index) {
        long start = 45L * index;
        long done = start + 240L;
        if (elapsed <= start) return 0f;
        if (elapsed >= done) return 1f;
        return (elapsed - start) / (float) (done - start);
    }
}
