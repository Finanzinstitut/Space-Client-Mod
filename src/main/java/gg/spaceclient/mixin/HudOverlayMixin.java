package gg.spaceclient.mixin;

import gg.spaceclient.modules.OverlayModule;

import net.minecraft.client.gui.GuiGraphicsExtractor;
//#if MC >= 26.2
import net.minecraft.client.gui.Hud;
//#else
//$$ import net.minecraft.client.gui.Gui;
//#endif
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
//#if MC >= 26.2
@Mixin(Hud.class)
//#else
//$$ @Mixin(Gui.class)
//#endif
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

    /** The game's own effect icons, top right, left out while the Effects module replaces them. */
    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
    private void spaceclient$effects(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta, CallbackInfo ci) {
        if (gg.spaceclient.modules.EffectsModule.replacesVanilla()) ci.cancel();
    }

    // ---------------------------------------------------------------- scoreboard

    /** The game's own scoreboard pass, skipped while the Scoreboard module draws it instead. */
    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void spaceclient$scoreboard(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta, CallbackInfo ci) {
        if (gg.spaceclient.modules.ScoreboardModule.replacesVanilla()) ci.cancel();
    }

    /** The board's two background fills: the title's first, then the lines'. */
    @org.spongepowered.asm.mixin.injection.Redirect(method = "displayScoreboardSidebar", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 0))
    private void spaceclient$titleFill(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int colour) {
        graphics.fill(x1, y1, x2, y2, gg.spaceclient.modules.ScoreboardModule.backgroundColour(true, colour));
    }

    @org.spongepowered.asm.mixin.injection.Redirect(method = "displayScoreboardSidebar", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 1))
    private void spaceclient$bodyFill(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int colour) {
        graphics.fill(x1, y1, x2, y2, gg.spaceclient.modules.ScoreboardModule.backgroundColour(false, colour));
    }

    /** No red numbers: the board is asked to format every score as blank. */
    @org.spongepowered.asm.mixin.injection.Redirect(method = "displayScoreboardSidebar", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/scores/Objective;numberFormatOrDefault(Lnet/minecraft/network/chat/numbers/NumberFormat;)Lnet/minecraft/network/chat/numbers/NumberFormat;"))
    private net.minecraft.network.chat.numbers.NumberFormat spaceclient$numbers(net.minecraft.world.scores.Objective objective,
                                                                                net.minecraft.network.chat.numbers.NumberFormat fallback) {
        return gg.spaceclient.modules.ScoreboardModule.numberFormat(objective.numberFormatOrDefault(fallback));
    }

    // ---------------------------------------------------------------- boss bar, titles, tools

    @Inject(method = "extractBossOverlay", at = @At("HEAD"), cancellable = true)
    private void spaceclient$bossIn(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta, CallbackInfo ci) {
        float scale = OverlayModule.bossBarScale();
        if (scale <= 0f) {
            ci.cancel();
            return;
        }
        // Grows from the top centre, where the bars hang
        spaceclient$push(graphics, graphics.guiWidth() / 2f, 0f, scale, 0);
    }

    @Inject(method = "extractBossOverlay", at = @At("RETURN"))
    private void spaceclient$bossOut(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta, CallbackInfo ci) {
        // A cancelled call never pushed; only a zero scale cancels, so it is
        // read again here to keep push and pop paired
        if (OverlayModule.bossBarScale() > 0f) graphics.pose().popMatrix();
    }

    @Inject(method = "extractTitle", at = @At("HEAD"))
    private void spaceclient$titleIn(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta, CallbackInfo ci) {
        spaceclient$push(graphics, graphics.guiWidth() / 2f, graphics.guiHeight() / 2f, OverlayModule.titleScale(), 0);
    }

    @Inject(method = "extractTitle", at = @At("RETURN"))
    private void spaceclient$titleOut(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta, CallbackInfo ci) {
        graphics.pose().popMatrix();
    }

    @Inject(method = "extractSpyglassOverlay", at = @At("HEAD"), cancellable = true)
    private void spaceclient$spyglass(GuiGraphicsExtractor graphics, float scale, CallbackInfo ci) {
        if (!OverlayModule.spyglass()) ci.cancel();
    }

    @ModifyVariable(method = "extractConfusionOverlay", at = @At("HEAD"), argsOnly = true)
    private float spaceclient$nausea(float strength) {
        return strength * OverlayModule.nauseaAlpha();
    }

    @ModifyVariable(method = "extractPortalOverlay", at = @At("HEAD"), argsOnly = true)
    private float spaceclient$portal(float alpha) {
        return alpha * OverlayModule.portalAlpha();
    }
}
