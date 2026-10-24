package gg.spaceclient.skin;

import gg.spaceclient.SpaceClient;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Skin files as textures, prepared the way the game prepares a downloaded
 * skin, and drawn as a flat figure.
 *
 * The preparation matters for more than looks. An old 64x32 skin has no
 * left arm or leg of its own and the player model reads a 64x64 sheet, so
 * it is unfolded into one first. And the game never draws a skin quite as
 * its file is: the base layer is made opaque, and on an old skin a hat area
 * with nothing see-through in it is cleared - leftover fill from editors,
 * which would otherwise be a box around the head. Same steps, same regions.
 */
public final class SkinImage {

    /** Textures already made, by library id; a skin is decoded once. */
    private static final Map<String, Identifier> LOADED = new HashMap<>();

    private SkinImage() {}

    /** The texture for a skin's bytes, registered under the given key. */
    public static Identifier texture(String key, byte[] png) {
        Identifier known = LOADED.get(key);
        if (known != null) return known;
        try {
            NativeImage image = prepare(NativeImage.read(png));
            Identifier id = Identifier.fromNamespaceAndPath(SpaceClient.MOD_ID, "skins/" + key);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
            LOADED.put(key, id);
            return id;
        } catch (Throwable t) {
            SpaceClient.LOGGER.warn("Could not read a skin image: {}", t.toString());
            return null;
        }
    }

    /** Whether a skin image is one the game can wear: 64 wide, 64 or 32 high. */
    public static boolean fits(byte[] png) {
        try (NativeImage image = NativeImage.read(png)) {
            return image.getWidth() == 64 && (image.getHeight() == 64 || image.getHeight() == 32);
        } catch (Throwable t) {
            return false;
        }
    }

    /** The game's own skin preparation, for a 64-wide sheet. */
    static NativeImage prepare(NativeImage source) {
        if (source.getWidth() != 64) return source;
        boolean legacy = source.getHeight() == 32;
        NativeImage image = source;
        if (legacy) {
            image = new NativeImage(64, 64, true);
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 64; x++) image.setPixel(x, y, source.getPixel(x, y));
            }
            source.close();
            // The left leg and arm, mirrored from the right ones
            copy(image, 4, 16, 16, 32, 4, 4);
            copy(image, 8, 16, 16, 32, 4, 4);
            copy(image, 0, 20, 24, 32, 4, 12);
            copy(image, 4, 20, 16, 32, 4, 12);
            copy(image, 8, 20, 8, 32, 4, 12);
            copy(image, 12, 20, 16, 32, 4, 12);
            copy(image, 44, 16, -8, 32, 4, 4);
            copy(image, 48, 16, -8, 32, 4, 4);
            copy(image, 40, 20, 0, 32, 4, 12);
            copy(image, 44, 20, -8, 32, 4, 12);
            copy(image, 48, 20, -16, 32, 4, 12);
            copy(image, 52, 20, -8, 32, 4, 12);
        }
        opaque(image, 0, 0, 32, 16);
        if (legacy) notchHack(image, 32, 0, 64, 32);
        opaque(image, 0, 16, 64, 32);
        opaque(image, 16, 48, 48, 64);
        return image;
    }

    /** Copies a rectangle by an offset, mirrored left to right. */
    private static void copy(NativeImage image, int x, int y, int dx, int dy, int w, int h) {
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                int pixel = image.getPixel(x + col, y + row);
                image.setPixel(x + dx + w - 1 - col, y + dy + row, pixel);
            }
        }
    }

    private static void opaque(NativeImage image, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) image.setPixel(x, y, image.getPixel(x, y) | 0xFF000000);
        }
    }

    /** A hat area with nothing see-through in it is fill, not a hat. */
    private static void notchHack(NativeImage image, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if ((image.getPixel(x, y) >>> 24) < 128) return;
            }
        }
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) image.setPixel(x, y, image.getPixel(x, y) & 0x00FFFFFF);
        }
    }

    // ---------------------------------------------------------------- drawing

    /** One box of the model: where it is in the sheet and how big. */
    private record Part(int u, int v, int w, int h, int d) {
        int frontU() { return u + d; }
        int backU() { return u + 2 * d + w; }
        int faceV() { return v + d; }
    }

    /**
     * Draws a skin as a flat figure, from the front or from behind.
     *
     * @param scale screen pixels per skin pixel
     * @return the figure's width, for centring
     */
    public static int drawFigure(GuiGraphicsExtractor graphics, Identifier texture,
                                 int x, int y, int scale, boolean slim, boolean back) {
        int arm = slim ? 3 : 4;
        Part head = new Part(0, 0, 8, 8, 8), hat = new Part(32, 0, 8, 8, 8);
        Part body = new Part(16, 16, 8, 12, 4), bodyOver = new Part(16, 32, 8, 12, 4);
        Part armR = new Part(40, 16, arm, 12, 4), armROver = new Part(40, 32, arm, 12, 4);
        Part armL = new Part(32, 48, arm, 12, 4), armLOver = new Part(48, 48, arm, 12, 4);
        Part legR = new Part(0, 16, 4, 12, 4), legROver = new Part(0, 32, 4, 12, 4);
        Part legL = new Part(16, 48, 4, 12, 4), legLOver = new Part(0, 48, 4, 12, 4);

        int mid = x + arm * scale;
        // Seen from the front the character's right is on the left of the
        // screen; from behind it is the other way round
        Part leftArm = back ? armL : armR, leftArmOver = back ? armLOver : armROver;
        Part rightArm = back ? armR : armL, rightArmOver = back ? armROver : armLOver;
        Part leftLeg = back ? legL : legR, leftLegOver = back ? legLOver : legROver;
        Part rightLeg = back ? legR : legL, rightLegOver = back ? legROver : legLOver;

        // Base layer first, then the outer layer over it: part, screen x,
        // height down the figure in skin pixels
        Object[][] pieces = {
                {head, mid, 0}, {body, mid, 8},
                {leftArm, x, 8}, {rightArm, mid + 8 * scale, 8},
                {leftLeg, mid, 20}, {rightLeg, mid + 4 * scale, 20},
                {hat, mid, 0}, {bodyOver, mid, 8},
                {leftArmOver, x, 8}, {rightArmOver, mid + 8 * scale, 8},
                {leftLegOver, mid, 20}, {rightLegOver, mid + 4 * scale, 20},
        };
        for (Object[] piece : pieces) {
            Part p = (Part) piece[0];
            int px = (Integer) piece[1];
            int py = y + (Integer) piece[2] * scale;
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, px, py,
                    back ? p.backU() : p.frontU(), p.faceV(),
                    p.w() * scale, p.h() * scale, p.w(), p.h(), 64, 64);
        }
        return (arm * 2 + 8) * scale;
    }
}
