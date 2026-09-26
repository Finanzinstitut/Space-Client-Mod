package gg.spaceclient.render;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.access.TintHolder;
import gg.spaceclient.modules.HitColorModule;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Field;

/**
 * Paints the hit colours into the game's entity overlay texture.
 *
 * That texture is 16 by 16. Its top eight rows are the red hurt flash, its
 * bottom eight the white creeper swell, and the game only ever samples rows 3
 * and 10. Rows 1 and 6 are therefore free: the hit colour goes into one, the
 * reach colour into the other, and a model pointed at either is shaded with it
 * exactly like the red flash shades it - across the skin, not around it.
 *
 * The shader keeps overlay alpha worth of the model's own colour, so the
 * setting's alpha (how strong the tint is) is written inverted.
 */
public final class HitTint {
    private static final int SIZE = 16;
    private static final int HIT_ROW = 1;
    private static final int REACH_ROW = 6;

    private static Field textureField;
    private static Object painted;
    private static int paintedHit;
    private static int paintedReach;

    private static boolean ready = false;
    private static boolean failed = false;
    private static String failure = "";

    private HitTint() {}

    /** For diagnostics: whether the colours made it into the texture. */
    public static String status() {
        if (failed) return "failed: " + failure;
        return ready ? "ok" : "not used yet";
    }

    /**
     * What an entity should be drawn with this frame.
     * Called during extraction, on the render thread.
     */
    public static int decide(Entity entity, boolean hurt) {
        if (failed) return TintHolder.NONE;

        var manager = SpaceClient.getModuleManager();
        if (manager == null) return TintHolder.NONE;
        if (!(manager.get("hitcolor") instanceof HitColorModule module)) return TintHolder.NONE;

        int kind = module.kindFor(entity, hurt);
        if (kind == TintHolder.NONE) return kind;

        // Only hand out a row once it actually holds the colour; until then the
        // entity keeps the game's own look rather than whatever the row had
        return paint(module.hitColour(), module.reachColour()) ? kind : TintHolder.NONE;
    }

    /** The game's overlay coordinates with the row swapped for ours. */
    public static int overlayFor(int tint, int original) {
        if (!ready) return original;
        int row = tint == TintHolder.HIT ? HIT_ROW : REACH_ROW;
        // Column (the creeper swell) kept, row replaced: pack is u | v << 16
        return (original & 0xFFFF) | (row << 16);
    }

    /** Writes the colours into the texture if they are not there already. */
    private static boolean paint(int hit, int reach) {
        try {
            OverlayTexture overlay = Minecraft.getInstance().gameRenderer.overlayTexture();
            DynamicTexture texture = texture(overlay);
            if (texture == null) return fail("overlay texture not found");

            if (texture == painted && hit == paintedHit && reach == paintedReach) return true;

            NativeImage pixels = texture.getPixels();
            if (pixels == null) return fail("overlay texture has no pixels");

            int hitPixel = toOverlay(hit);
            int reachPixel = toOverlay(reach);
            for (int x = 0; x < SIZE; x++) {
                pixels.setPixel(x, HIT_ROW, hitPixel);
                pixels.setPixel(x, REACH_ROW, reachPixel);
            }
            texture.upload();

            painted = texture;
            paintedHit = hit;
            paintedReach = reach;
            ready = true;
            return true;
        } catch (Throwable t) {
            return fail(t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    /**
     * A setting colour as an overlay pixel.
     * The setting's alpha is how much colour; the overlay's is how much model.
     */
    static int toOverlay(int argb) {
        int strength = (argb >>> 24) & 0xFF;
        return ((255 - strength) << 24) | (argb & 0x00FFFFFF);
    }

    private static DynamicTexture texture(OverlayTexture overlay) throws IllegalAccessException {
        if (overlay == null) return null;
        if (textureField == null) {
            for (Field field : OverlayTexture.class.getDeclaredFields()) {
                if (DynamicTexture.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    textureField = field;
                    break;
                }
            }
            if (textureField == null) return null;
        }
        return (DynamicTexture) textureField.get(overlay);
    }

    private static boolean fail(String why) {
        // Once is enough: every entity would otherwise retry it every frame
        failed = true;
        ready = false;
        failure = why;
        SpaceClient.LOGGER.warn("Hit colour disabled, the game's red flash stays: {}", why);
        return false;
    }
}
