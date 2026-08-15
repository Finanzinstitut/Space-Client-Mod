package gg.spaceclient.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Random;

/**
 * The launcher's background: a deep violet gradient, a scattering of stars and
 * a glowing planet in the top right corner.
 *
 * Star positions come from a fixed seed so they stay put between frames instead
 * of flickering, and are only recomputed when the window size changes.
 */
public final class Backdrop {
    private static final int STAR_COUNT = 140;

    private static int[] starX = new int[0];
    private static int[] starY = new int[0];
    private static int[] starSize = new int[0];
    private static int[] starColor = new int[0];
    private static int cachedWidth = -1;
    private static int cachedHeight = -1;

    private static void rebuild(int width, int height) {
        Random random = new Random(42);
        starX = new int[STAR_COUNT];
        starY = new int[STAR_COUNT];
        starSize = new int[STAR_COUNT];
        starColor = new int[STAR_COUNT];

        for (int i = 0; i < STAR_COUNT; i++) {
            starX[i] = random.nextInt(Math.max(1, width));
            starY[i] = random.nextInt(Math.max(1, height));
            starSize[i] = random.nextInt(10) == 0 ? 2 : 1;
            int brightness = 150 + random.nextInt(106);
            starColor[i] = 0xB0000000 | (brightness << 16) | (brightness << 8) | 0xFF;
        }
        cachedWidth = width;
        cachedHeight = height;
    }

    public static void draw(GuiGraphicsExtractor graphics, int width, int height) {
        if (!Theme.spaceBackdrop()) {
            graphics.fill(0, 0, width, height, Theme.backdrop());
            return;
        }

        if (width != cachedWidth || height != cachedHeight) {
            rebuild(width, height);
        }

        // Vertical gradient from the violet highlight down to near black
        graphics.fillGradient(0, 0, width, height, Theme.BG_MID, Theme.BG_DEEP);

        for (int i = 0; i < STAR_COUNT; i++) {
            int size = starSize[i];
            graphics.fill(starX[i], starY[i], starX[i] + size, starY[i] + size, starColor[i]);
        }

        drawPlanetGlow(graphics, width);
    }

    /**
     * The launcher's planet sits partly off the top right corner. Concentric
     * rings of increasing transparency stand in for the CSS radial gradient.
     */
    private static void drawPlanetGlow(GuiGraphicsExtractor graphics, int width) {
        int cx = width - 60;
        int cy = 30;
        int maxRadius = 120;

        for (int radius = maxRadius; radius > 0; radius -= 6) {
            // Fades out towards the edge of the glow
            int alpha = (int) (70 * (1.0 - radius / (double) maxRadius));
            if (alpha <= 2) continue;
            int color = (alpha << 24) | 0x7C5CFF;

            for (int dy = -radius; dy <= radius; dy += 2) {
                int y = cy + dy;
                if (y < 0) continue;
                double halfWidth = Math.sqrt(Math.max(0, radius * radius - dy * dy));
                if (halfWidth < 1) continue;
                int x1 = (int) (cx - halfWidth);
                int x2 = (int) (cx + halfWidth);
                graphics.fill(x1, y, x2, y + 2, color);
            }
        }
    }

    private Backdrop() {}
}
