package gg.spaceclient.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The totem this client draws when one saves you.
 *
 * Drawn from rectangles, like every other mark in this menu, and that is the
 * whole point rather than a stylistic preference. The pop used to be the item's
 * own icon, which is a model a resource pack can replace - so the one moment
 * worth seeing clearly was also the one moment somebody else's pack got to
 * decide what you saw. Nothing here can be replaced, because there is no asset
 * to replace: the picture is in the class.
 *
 * It is also this client's own drawing rather than a copy of the game's. Making
 * something that reads as a totem is allowed; shipping Mojang's texture is not.
 */
public final class TotemArt {

    /**
     * One character per pixel. Square, so the grid's length is both its width
     * and its height.
     *
     * o outline, Y gold, y gold shade, e eye, r mouth, G body, c gem.
     */
    private static final String[] GRID = {
            "....oooooooo....",
            "...oYYYYYYYYo...",
            "...oYeYYYYeYo...",
            "...oYYYYYYYYo...",
            "...oYYyrryYYo...",
            "...oYYYYYYYYo...",
            "....oooooooo....",
            "..oooooooooooo..",
            ".oGGGGGGGGGGGGo.",
            ".oGGooGGGGooGGo.",
            ".oGGooGccGooGGo.",
            ".oGGooGGGGooGGo.",
            "..oo.oGGGGo.oo..",
            ".....oGGGGo.....",
            ".....oGGGGo.....",
            "......oooo......",
    };

    private static int colourOf(char ch) {
        return switch (ch) {
            case 'o' -> 0x142B1A;
            case 'G' -> 0x468C46;
            case 'Y' -> 0xF2CC5A;
            case 'y' -> 0xC29A33;
            case 'e' -> 0x1E1812;
            case 'r' -> 0x9E4F2C;
            case 'c' -> 0x5FE0C0;
            default -> 0;
        };
    }

    /** How many pixels across the drawing is, in its own units. */
    public static int cells() { return GRID.length; }

    /**
     * Draws the totem centred on a point.
     *
     * @param size  width and height in screen pixels
     * @param alpha 0 to 255, so the pop can fade rather than vanish
     */
    public static void draw(GuiGraphicsExtractor graphics, int centreX, int centreY,
                            float size, int alpha) {
        if (alpha <= 2 || size < 4f) return;

        int clamped = Math.min(255, alpha);
        float cell = size / GRID.length;
        float left = centreX - size / 2f;
        float top = centreY - size / 2f;

        for (int row = 0; row < GRID.length; row++) {
            String line = GRID[row];

            // Rounded from the edges rather than multiplied out per pixel, so
            // neighbouring cells share an edge exactly and no seam of
            // background shows through between them at large sizes.
            int y1 = Math.round(top + row * cell);
            int y2 = Math.round(top + (row + 1) * cell);
            if (y2 <= y1) y2 = y1 + 1;

            int col = 0;
            while (col < line.length()) {
                char ch = line.charAt(col);
                int end = col;
                // A run of one colour is one rectangle. At 400% that is the
                // difference between sixteen fills a row and a hundred.
                while (end < line.length() && line.charAt(end) == ch) end++;

                int colour = colourOf(ch);
                if (colour != 0) {
                    int x1 = Math.round(left + col * cell);
                    int x2 = Math.round(left + end * cell);
                    if (x2 <= x1) x2 = x1 + 1;
                    graphics.fill(x1, y1, x2, y2, (clamped << 24) | colour);
                }
                col = end;
            }
        }
    }

    private TotemArt() {}
}
