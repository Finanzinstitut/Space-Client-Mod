package gg.spaceclient.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Rounded, layered panels that read as glass.
 *
 * What this is not: a blur. Frosting the view behind a panel means sampling
 * the framebuffer and running a shader over it, and there is no way to do that
 * here that would survive a version change. What carries the look instead is
 * everything else glass does - a rounded edge, a bright line along the top
 * where light catches, a darker one underneath, and a body that is lighter at
 * the top than the bottom.
 *
 * Rounding is done by insetting each row. At the small radii an interface uses
 * that is indistinguishable from a real curve and costs a handful of fills.
 */
public final class Glass {

    /** How far each row is pulled in, by distance from the corner. */
    private static final int[] INSET_4 = { 2, 1, 0, 0 };
    private static final int[] INSET_6 = { 3, 2, 1, 1, 0, 0 };

    private static int[] insetsFor(int radius) {
        return radius >= 6 ? INSET_6 : INSET_4;
    }

    /**
     * A glass plate.
     *
     * `tint` carries both the colour and the opacity: glass is defined by what
     * shows through it, so a caller that cannot set the alpha cannot make
     * glass.
     */
    public static void panel(GuiGraphicsExtractor graphics,
                             int x, int y, int width, int height,
                             int tint, int radius) {
        if (width <= 0 || height <= 0) return;

        int[] insets = insetsFor(radius);
        int alpha = (tint >>> 24) & 0xFF;

        // The body, drawn as rows so the corners can be pulled in. Lighter at
        // the top: a flat fill reads as paper, a gradient reads as a surface
        // with light falling on it.
        for (int row = 0; row < height; row++) {
            int inset = 0;
            if (row < insets.length) inset = insets[row];
            else if (row >= height - insets.length) inset = insets[height - 1 - row];

            float down = row / (float) Math.max(1, height - 1);
            int rowAlpha = Math.round(alpha * (1f - down * 0.25f));
            int rowColor = (rowAlpha << 24) | (tint & 0xFFFFFF);

            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, rowColor);
        }

        // The sheen: one bright line just inside the top edge. This single row
        // does more for the illusion than the gradient does.
        int sheenInset = insets.length > 0 ? insets[0] : 0;
        graphics.fill(x + sheenInset + 1, y, x + width - sheenInset - 1, y + 1,
                (Math.round(alpha * 0.55f) << 24) | 0xFFFFFF);

        // And a shadow line under the bottom edge, which is what stops the
        // panel floating rather than sitting on something
        graphics.fill(x + sheenInset + 1, y + height - 1, x + width - sheenInset - 1, y + height,
                (Math.round(alpha * 0.35f) << 24));
    }

    /**
     * The same plate with a coloured edge, for something that is selected or
     * active.
     */
    public static void panel(GuiGraphicsExtractor graphics,
                             int x, int y, int width, int height,
                             int tint, int radius, int edge, float strength) {
        panel(graphics, x, y, width, height, tint, radius);
        if (strength <= 0.01f) return;

        int alpha = Math.round(255 * Math.min(1f, strength));
        int color = (alpha << 24) | (edge & 0xFFFFFF);
        int inset = insetsFor(radius)[0];

        graphics.fill(x + inset, y, x + width - inset, y + 1, color);
        graphics.fill(x + inset, y + height - 1, x + width - inset, y + height, color);
        graphics.fill(x, y + inset, x + 1, y + height - inset, color);
        graphics.fill(x + width - 1, y + inset, x + width, y + height - inset, color);
    }

    private Glass() {}
}
