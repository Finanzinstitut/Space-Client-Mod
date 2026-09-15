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
 *
 * One lesson is baked into the numbers below: every highlight here is derived
 * from the plate's own opacity. A fixed bright edge on a nearly transparent
 * plate is not glass, it is a stray line with nothing underneath it.
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
        if (alpha == 0) return;

        int red = (tint >> 16) & 0xFF;
        int green = (tint >> 8) & 0xFF;
        int blue = tint & 0xFF;

        // The body, drawn as rows so the corners can be pulled in.
        //
        // The gradient lightens the colour toward the top rather than thinning
        // the alpha. Thinning was the first attempt and it was wrong: these
        // plates are dark and sit on dark backgrounds, so a more transparent
        // top simply disappeared and the plate lost its shape.
        for (int row = 0; row < height; row++) {
            int inset = 0;
            if (row < insets.length) inset = insets[row];
            else if (row >= height - insets.length) inset = insets[height - 1 - row];

            float down = row / (float) Math.max(1, height - 1);
            float lift = (1f - down) * 0.16f;

            int rowColor = (alpha << 24)
                    | (lighten(red, lift) << 16)
                    | (lighten(green, lift) << 8)
                    | lighten(blue, lift);

            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, rowColor);
        }

        int sheenInset = insets.length > 0 ? insets[0] : 0;

        // The sheen, tied to how opaque the plate is and capped.
        //
        // It was a flat 55% white before, which on a nearly transparent dark
        // plate was the only thing visible - a bright grey line floating with
        // nothing under it. A highlight cannot be brighter than the surface it
        // is supposed to be lying on.
        int sheenAlpha = Math.min(46, Math.round(alpha * 0.30f));
        if (sheenAlpha > 6) {
            graphics.fill(x + sheenInset + 1, y, x + width - sheenInset - 1, y + 1,
                    (sheenAlpha << 24) | 0xFFFFFF);
        }

        int shadowAlpha = Math.min(60, Math.round(alpha * 0.28f));
        if (shadowAlpha > 6) {
            graphics.fill(x + sheenInset + 1, y + height - 1,
                    x + width - sheenInset - 1, y + height, shadowAlpha << 24);
        }
    }


    /**
     * A glass pill: rounded to any radius, with a soft edge around it.
     *
     * The older panel() above is built on two hand-written corner tables and
     * stops at a radius of six, which is a rounded rectangle rather than the
     * shape asked for. This computes the corner from a circle, so a radius of
     * half the height gives a capsule and anything between behaves.
     *
     * The softness around the outside is three rings of increasing size and
     * falling opacity. It is not a blur of what is behind the plate - that
     * needs the frame buffer read back, which is a different and much larger
     * piece of work - but it is what makes the plate read as lying above the
     * world rather than being cut into it.
     */
    public static void pill(GuiGraphicsExtractor graphics,
                            int x, int y, int width, int height,
                            int tint, int radius) {
        pill(graphics, x, y, width, height, tint, radius, NO_CLIP_TOP, NO_CLIP_BOTTOM);
    }

    /** Nothing is clipped away unless a caller asks for it. */
    public static final int NO_CLIP_TOP = Integer.MIN_VALUE / 4;
    public static final int NO_CLIP_BOTTOM = Integer.MAX_VALUE / 4;

    /**
     * The same pill, with the rows outside a horizontal band thrown away.
     *
     * A scrolling list needs this. Without it a card halfway out of its
     * viewport paints over the header above it, and the usual fix - a strip
     * drawn over the overflow - does not work on a panel whose own alpha is
     * below 0xFF, because the strip is then as see-through as what it covers.
     * Dropping the rows is the only version of this that is actually correct.
     */
    public static void pill(GuiGraphicsExtractor graphics,
                            int x, int y, int width, int height,
                            int tint, int radius, int clipTop, int clipBottom) {
        if (width <= 0 || height <= 0) return;
        if (y >= clipBottom || y + height <= clipTop) return;

        int alpha = (tint >>> 24) & 0xFF;
        if (alpha == 0) return;

        int red = (tint >> 16) & 0xFF;
        int green = (tint >> 8) & 0xFF;
        int blue = tint & 0xFF;

        // Outward, faintest first. Each ring is the same shape one pixel
        // larger, which is a cheap stand-in for a shadow that falls off.
        for (int step = 3; step >= 1; step--) {
            int shadow = Math.round(alpha * 0.055f * (4 - step));
            if (shadow <= 2) continue;
            roundedRows(graphics, x - step, y - step,
                    width + step * 2, height + step * 2, radius + step,
                    row -> shadow << 24, clipTop, clipBottom);
        }

        // The body, one flat tone. It used to lighten toward the top, which
        // read as the plate being lit from above - and the top edge then sat
        // brighter than the bottom, which is the thing that stood out.
        int body = (alpha << 24) | (red << 16) | (green << 8) | blue;
        roundedRows(graphics, x, y, width, height, radius, row -> body, clipTop, clipBottom);

        int[] insets = circleInsets(radius, height);
        int top = insets.length > 0 ? insets[0] : 0;

        // The same dark edge top and bottom. A lighter top edge is the usual
        // way to suggest glass catching the light, and it was what made the
        // upper half look brighter than the lower - so both edges now get the
        // same line and the plate reads as one flat tone.
        int edge = Math.min(46, Math.round(alpha * 0.20f));
        if (edge > 2) {
            band(graphics, x + top + 2, y, x + width - top - 2, y + 1,
                    edge << 24, clipTop, clipBottom);
            band(graphics, x + top + 2, y + height - 1,
                    x + width - top - 2, y + height, edge << 24, clipTop, clipBottom);
        }
    }

    /** A fill that keeps only the part inside the band. */
    public static void band(GuiGraphicsExtractor graphics,
                            int x1, int y1, int x2, int y2,
                            int colour, int clipTop, int clipBottom) {
        int top = Math.max(y1, clipTop);
        int bottom = Math.min(y2, clipBottom);
        if (bottom <= top) return;
        graphics.fill(x1, top, x2, bottom, colour);
    }

    /** Draws one rounded shape, asking the caller for each row's colour. */
    private static void roundedRows(GuiGraphicsExtractor graphics,
                                    int x, int y, int width, int height,
                                    int radius, java.util.function.IntUnaryOperator colourOf) {
        roundedRows(graphics, x, y, width, height, radius, colourOf, NO_CLIP_TOP, NO_CLIP_BOTTOM);
    }

    private static void roundedRows(GuiGraphicsExtractor graphics,
                                    int x, int y, int width, int height,
                                    int radius, java.util.function.IntUnaryOperator colourOf,
                                    int clipTop, int clipBottom) {
        int[] insets = circleInsets(radius, height);
        for (int row = 0; row < height; row++) {
            if (y + row < clipTop || y + row >= clipBottom) continue;
            int inset = 0;
            if (row < insets.length) inset = insets[row];
            else if (row >= height - insets.length) inset = insets[height - 1 - row];

            int colour = colourOf.applyAsInt(row);
            if (((colour >>> 24) & 0xFF) == 0) continue;
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, colour);
        }
    }

    /**
     * How far each row near a corner is pulled in, taken off a circle.
     *
     * Cached per radius: a HUD with twenty elements would otherwise work this
     * out twenty times a frame for the same handful of numbers.
     */
    private static int[] circleInsets(int radius, int height) {
        int r = Math.max(0, Math.min(radius, height / 2));
        if (r == 0) return EMPTY_INSETS;

        int[] cached = INSET_CACHE.get(r);
        if (cached != null) return cached;

        int[] out = new int[r];
        for (int row = 0; row < r; row++) {
            double dy = r - row - 0.5;
            double dx = Math.sqrt(Math.max(0.0, (double) r * r - dy * dy));
            out[row] = (int) Math.round(r - dx);
        }
        INSET_CACHE.put(r, out);
        return out;
    }

    private static final int[] EMPTY_INSETS = new int[0];

    private static final java.util.Map<Integer, int[]> INSET_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Moves a channel toward white by a fraction. */
    private static int lighten(int channel, float amount) {
        return Math.min(255, Math.round(channel + (255 - channel) * amount));
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
