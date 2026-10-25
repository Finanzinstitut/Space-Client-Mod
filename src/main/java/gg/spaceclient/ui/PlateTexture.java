package gg.spaceclient.ui;

import gg.spaceclient.SpaceClient;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The HUD plate as three pieces of a picture instead of forty rectangles.
 *
 * Glass.plate builds the rounded plate row by row: a shadow ring, the body
 * and two edge lines, about forty fills for a capsule. The game places every
 * fill by testing it against the others on screen, and with a full HUD that
 * test was most of what the HUD cost. A plate is the same from left to right
 * except at its two ends, so it is drawn once into a small image here - by
 * the same fills, composited the same way - and then put on screen as the
 * left end, one middle column stretched to the width, and the right end.
 * Same pixels, three draws.
 *
 * Images are kept per height, radius and colour, which a HUD has a handful of.
 */
final class PlateTexture {

    /** The shadow ring reaches this far outside the plate. */
    private static final int PAD = 2;

    private record Key(int height, int radius, int tint) {}

    private record Made(Identifier id, int cap, int width, int height) {}

    private static final Map<Key, Made> MADE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Made> eldest) {
            if (size() <= 48) return false;
            Minecraft.getInstance().getTextureManager().release(eldest.getValue().id());
            return true;
        }
    };

    private static int serial = 0;
    private static boolean broken = false;

    private PlateTexture() {}

    /**
     * Draws the plate from its image.
     *
     * @return false when it cannot - too narrow for the two ends, or no image
     *         could be made - and the caller draws it the old way.
     */
    static boolean draw(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                        int tint, int radius) {
        if (broken) return false;
        int r = Math.max(0, Math.min(radius, height / 2));
        int cap = r + 3 + PAD;
        if (width + 2 * PAD < 2 * cap + 1) return false;

        Made made = made(height, radius, tint);
        if (made == null) return false;

        int left = x - PAD;
        int top = y - PAD;
        int h = made.height();
        int right = x + width + PAD - cap;
        int white = 0xFFFFFFFF;
        graphics.blit(RenderPipelines.GUI_TEXTURED, made.id(), left, top, 0f, 0f,
                cap, h, cap, h, made.width(), h, white);
        graphics.blit(RenderPipelines.GUI_TEXTURED, made.id(), left + cap, top, (float) cap, 0f,
                right - (left + cap), h, 1, h, made.width(), h, white);
        graphics.blit(RenderPipelines.GUI_TEXTURED, made.id(), right, top, (float) (cap + 1), 0f,
                cap, h, cap, h, made.width(), h, white);
        return true;
    }

    private static Made made(int height, int radius, int tint) {
        Key key = new Key(height, radius, tint);
        Made known = MADE.get(key);
        if (known != null) return known;
        try {
            int r = Math.max(0, Math.min(radius, height / 2));
            int cap = r + 3 + PAD;
            int plateW = 2 * cap + 1 - 2 * PAD;      // the narrowest plate with both ends whole
            int imageW = plateW + 2 * PAD;
            int imageH = height + 2 * PAD;

            Canvas canvas = new Canvas(imageW, imageH);
            Glass.plateInto(canvas, PAD, PAD, plateW, height, tint, radius);

            NativeImage image = new NativeImage(imageW, imageH, true);
            for (int py = 0; py < imageH; py++) {
                for (int px = 0; px < imageW; px++) image.setPixel(px, py, canvas.argb(px, py));
            }
            Identifier id = Identifier.fromNamespaceAndPath(SpaceClient.MOD_ID, "plate/" + (serial++));
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
            Made made = new Made(id, cap, imageW, imageH);
            MADE.put(key, made);
            return made;
        } catch (Throwable t) {
            broken = true;
            SpaceClient.LOGGER.warn("HUD plates fall back to rectangles: {}", t.toString());
            return null;
        }
    }

    /**
     * Somewhere to draw fills that is not the screen: the same rectangles,
     * blended the way the game blends them, into an image.
     */
    static final class Canvas implements Glass.Fills {
        private final int width, height;
        private final float[] a, r, g, b;

        Canvas(int width, int height) {
            this.width = width;
            this.height = height;
            int n = width * height;
            a = new float[n]; r = new float[n]; g = new float[n]; b = new float[n];
        }

        @Override
        public void fill(int x1, int y1, int x2, int y2, int colour) {
            float sa = ((colour >>> 24) & 0xFF) / 255f;
            if (sa <= 0f) return;
            float sr = ((colour >> 16) & 0xFF) / 255f, sg = ((colour >> 8) & 0xFF) / 255f, sb = (colour & 0xFF) / 255f;
            for (int y = Math.max(0, y1); y < Math.min(height, y2); y++) {
                for (int x = Math.max(0, x1); x < Math.min(width, x2); x++) {
                    int i = y * width + x;
                    float da = a[i];
                    float oa = sa + da * (1 - sa);
                    r[i] = (sr * sa + r[i] * da * (1 - sa)) / oa;
                    g[i] = (sg * sa + g[i] * da * (1 - sa)) / oa;
                    b[i] = (sb * sa + b[i] * da * (1 - sa)) / oa;
                    a[i] = oa;
                }
            }
        }

        int argb(int x, int y) {
            int i = y * width + x;
            return (Math.round(a[i] * 255) << 24) | (Math.round(r[i] * 255) << 16)
                    | (Math.round(g[i] * 255) << 8) | Math.round(b[i] * 255);
        }
    }
}
