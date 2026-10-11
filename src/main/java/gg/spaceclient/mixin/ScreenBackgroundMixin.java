package gg.spaceclient.mixin;

import gg.spaceclient.modules.OverlayModule;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * How dark the world goes behind an open inventory, chest or other in-game
 * screen: the game's two-colour shade with its alpha scaled by the Overlay
 * module's setting.
 */
@Mixin(Screen.class)
public abstract class ScreenBackgroundMixin {

    @Redirect(method = "extractTransparentBackground", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fillGradient(IIIIII)V"))
    private void spaceclient$dim(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int top, int bottom) {
        float factor = OverlayModule.inventoryDim();
        if (factor >= 1f) {
            graphics.fillGradient(x1, y1, x2, y2, top, bottom);
            return;
        }
        if (factor <= 0f) return;
        graphics.fillGradient(x1, y1, x2, y2, spaceclient$fade(top, factor), spaceclient$fade(bottom, factor));
    }

    private static int spaceclient$fade(int argb, float factor) {
        int alpha = Math.round(((argb >>> 24) & 0xFF) * factor);
        return (alpha << 24) | (argb & 0xFFFFFF);
    }
}
