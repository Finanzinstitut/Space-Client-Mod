package gg.spaceclient.mixin;

import gg.spaceclient.modules.OverlayModule;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Overlay module's hooks into the HUD: the bars are scaled around the
 * corner they grow from, and the full-screen tints are dimmed or left out.
 *
 * Every scale is a push at the start of the game's own method and a pop at
 * every return, so the game still lays out each heart and bubble itself.
 */
@Mixin(Hud.class)
public abstract class HudOverlayMixin {

    @Shadow
    private void extractTextureOverlay(GuiGraphicsExtractor graphics, Identifier texture, float alpha) {}

    @Unique
    private static boolean spaceclient$redrawing = false;

    @Unique
    private static void spaceclient$push(GuiGraphicsExtractor graphics, float pivotX, float pivotY, float scale, float lift) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(pivotX, pivotY - lift);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate(-pivotX, -pivotY);
    }

    // ---------------------------------------------------------------- hearts

    @Inject(method = "extractHearts", at = @At("HEAD"))
    private void spaceclient$heartsIn(GuiGraphicsExtractor graphics, Player player, int x, int y,
                                      int rowHeight, int regen, float maxHealth, int health,
                                      int lastHealth, int absorption, boolean blink, CallbackInfo ci) {
        // Grows up and to the right from the bottom left of the bottom row
        spaceclient$push(graphics, x, y + 9, OverlayModule.heartScale(), 0);
    }

    @Inject(method = "extractHearts", at = @At("RETURN"))
    private void spaceclient$heartsOut(GuiGraphicsExtractor graphics, Player player, int x, int y,
                                       int rowHeight, int regen, float maxHealth, int health,
                                       int lastHealth, int absorption, boolean blink, CallbackInfo ci) {
        graphics.pose().popMatrix();
    }

    // ---------------------------------------------------------------- armour

    @Inject(method = "extractArmor", at = @At("HEAD"))
    private static void spaceclient$armorIn(GuiGraphicsExtractor graphics, Player player, int yLineBase,
                                            int numHealthRows, int healthRowHeight, int xLeft, CallbackInfo ci) {
        int armorY = yLineBase - (numHealthRows - 1) * healthRowHeight - 10;
        // Pushed up by however much taller the hearts below it became, so a
        // larger heart bar does not grow into the armour
        float heartsHeight = (numHealthRows - 1) * healthRowHeight + 10;
        float lift = (OverlayModule.heartScale() - 1f) * heartsHeight;
        spaceclient$push(graphics, xLeft, armorY + 9, OverlayModule.armorScale(), lift);
    }

    @Inject(method = "extractArmor", at = @At("RETURN"))
    private static void spaceclient$armorOut(GuiGraphicsExtractor graphics, Player player, int yLineBase,
                                             int numHealthRows, int healthRowHeight, int xLeft, CallbackInfo ci) {
        graphics.pose().popMatrix();
    }

    // ---------------------------------------------------------------- food and air

    @Inject(method = "extractFood", at = @At("HEAD"))
    private void spaceclient$foodIn(GuiGraphicsExtractor graphics, Player player, int y, int xRight, CallbackInfo ci) {
        // Grows up and to the left from its bottom right
        spaceclient$push(graphics, xRight, y + 9, OverlayModule.foodScale(), 0);
    }

    @Inject(method = "extractFood", at = @At("RETURN"))
    private void spaceclient$foodOut(GuiGraphicsExtractor graphics, Player player, int y, int xRight, CallbackInfo ci) {
        graphics.pose().popMatrix();
    }

    @Inject(method = "extractAirBubbles", at = @At("HEAD"))
    private void spaceclient$airIn(GuiGraphicsExtractor graphics, Player player, int vehicleHearts,
                                   int y, int xRight, CallbackInfo ci) {
        float lift = (OverlayModule.foodScale() - 1f) * 10f;
        spaceclient$push(graphics, xRight, y + 9, OverlayModule.airScale(), lift);
    }

    @Inject(method = "extractAirBubbles", at = @At("RETURN"))
    private void spaceclient$airOut(GuiGraphicsExtractor graphics, Player player, int vehicleHearts,
                                    int y, int xRight, CallbackInfo ci) {
        graphics.pose().popMatrix();
    }

    // ---------------------------------------------------------------- tints

    @Inject(method = "extractTextureOverlay", at = @At("HEAD"), cancellable = true)
    private void spaceclient$tint(GuiGraphicsExtractor graphics, Identifier texture, float alpha, CallbackInfo ci) {
        if (spaceclient$redrawing) return;
        float factor = OverlayModule.textureOverlay(texture);
        if (factor >= 1f) return;
        ci.cancel();
        if (factor <= 0f) return;
        // Drawn again by the game's own method with less alpha
        spaceclient$redrawing = true;
        try {
            extractTextureOverlay(graphics, texture, alpha * factor);
        } finally {
            spaceclient$redrawing = false;
        }
    }

    @Inject(method = "extractVignette", at = @At("HEAD"), cancellable = true)
    private void spaceclient$vignette(GuiGraphicsExtractor graphics, Entity camera, CallbackInfo ci) {
        if (!OverlayModule.vignette()) ci.cancel();
    }

    @ModifyVariable(method = "extractPortalOverlay", at = @At("HEAD"), argsOnly = true)
    private float spaceclient$portal(float alpha) {
        return alpha * OverlayModule.portalAlpha();
    }
}
