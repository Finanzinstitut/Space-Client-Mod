package gg.spaceclient.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Draws a texture into the menu.
 *
 * This used to find the game's texture call by reflection, matching method
 * and field names at runtime. On 1.21.11 those names are obfuscated in a real
 * game, the search found nothing, and every server icon and cosmetic
 * thumbnail fell back to a plain tile. The call is the same on every
 * supported version, so it is made directly now.
 */
public final class Textures {

    private Textures() {}

    /**
     * Draws the whole texture into the given box.
     *
     * @return false if there was nothing to draw, so the caller can fill the
     *         space with something else.
     */
    public static boolean draw(GuiGraphicsExtractor graphics, Identifier texture,
                               int x, int y, int width, int height) {
        return drawFrame(graphics, texture, x, y, width, height, 1, 0);
    }

    /**
     * Draws one frame of a vertically stacked animation strip.
     *
     * Cosmetica's thumbnails are animation sheets rather than single images, so
     * drawing the whole texture would squash every frame into the card. Taking
     * a slice shows one frame at the right proportions.
     *
     * @param frames how many frames are stacked in the texture; 1 draws it whole
     * @param frame  which frame to show, from the top
     */
    public static boolean drawFrame(GuiGraphicsExtractor graphics, Identifier texture,
                                    int x, int y, int width, int height,
                                    int frames, int frame) {
        if (texture == null) return false;
        // The sampled region is expressed in the texture's own units. Declaring
        // the texture to be frames tall and one wide makes the maths land on
        // whole frames without knowing the real pixel size.
        float v = Math.max(0, Math.min(frames - 1, frame));
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, v, width, height, 1, 1, 1, Math.max(1, frames));
        return true;
    }
}
