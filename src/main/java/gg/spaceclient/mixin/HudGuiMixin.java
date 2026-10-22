package gg.spaceclient.mixin;

import gg.spaceclient.modules.OverlayModule;

//#if MC >= 26.3
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
//#else
//$$ import com.mojang.blaze3d.pipeline.RenderPipeline;
//#endif

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
//#if MC >= 26.2
import net.minecraft.client.gui.Hud;
//#else
//$$ import net.minecraft.client.gui.Gui;
//#endif
//#if MC >= 26.2
import net.minecraft.client.gui.contextualbar.ContextualBar;
import net.minecraft.client.gui.contextualbar.ExperienceBar;
//#else
//$$ import net.minecraft.client.gui.contextualbar.ContextualBarRenderer;
//$$ import net.minecraft.client.gui.contextualbar.ExperienceBarRenderer;
//#endif
import net.minecraft.resources.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Overlay module's hotbar settings.
 *
 * The size and height work on the whole block the game draws in one method -
 * hotbar, hearts, armour, food, air, XP and the held item's name - scaled
 * about the bottom middle of the screen. Done as one, so nothing above the
 * hotbar ends up overlapping it or floating away from it.
 */
//#if MC >= 26.2
@Mixin(Hud.class)
//#else
//$$ @Mixin(Gui.class)
//#endif
public abstract class HudGuiMixin {

    //#if MC >= 26.3
    private static final String PIPELINE = "Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;";
    //#else
    //$$ private static final String PIPELINE = "Lcom/mojang/blaze3d/pipeline/RenderPipeline;";
    //#endif

    //#if FABRIC
    @Inject(method = "extractHotbarAndDecorations", at = @At("HEAD"))
    private void spaceclient$hotbarIn(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        OverlayModule.hotbarBegin(graphics, delta);
    }

    @Inject(method = "extractHotbarAndDecorations", at = @At("RETURN"))
    private void spaceclient$hotbarOut(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        OverlayModule.hotbarEnd(graphics, delta);
    }
    //#endif

    /** The hotbar frame, the selected-slot frame and the off-hand frame. */
    @Redirect(method = "extractItemHotbar", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite("
                    + PIPELINE
                    + "Lnet/minecraft/resources/Identifier;IIII)V"))
    private void spaceclient$frame(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite,
                                   int x, int y, int width, int height) {
        String path = sprite.getPath();
        int colour = 0xFFFFFFFF;
        if (path.equals("hud/hotbar_selection")) {
            colour = OverlayModule.selectionColour();
        } else if (path.equals("hud/hotbar") || path.startsWith("hud/hotbar_offhand")) {
            int alpha = Math.round(OverlayModule.hotbarOpacity() * 255);
            if (alpha <= 0) return;
            colour = (alpha << 24) | 0xFFFFFF;
        }
        if (colour == 0xFFFFFFFF) graphics.blitSprite(pipeline, sprite, x, y, width, height);
        else graphics.blitSprite(pipeline, sprite, x, y, width, height, colour);
    }

    @Inject(method = "extractItemHotbar", at = @At("TAIL"))
    private void spaceclient$slotNumbers(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        if (!OverlayModule.slotNumbers()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Font font = mc.font;
        int left = graphics.guiWidth() / 2 - 91;
        int top = graphics.guiHeight() - 22;
        graphics.pose().pushMatrix();
        graphics.pose().translate(0f, 0f);
        for (int i = 0; i < 9; i++) {
            float x = left + i * 20 + 3;
            float y = top + 3;
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);
            graphics.pose().scale(0.5f, 0.5f);
            graphics.text(font, String.valueOf(i + 1), 0, 0, 0xFFE0E0E0, true);
            graphics.pose().popMatrix();
        }
        graphics.pose().popMatrix();
    }

    // ---------------------------------------------------------------- XP
    // Fabric draws the bar and the level inside the one hotbar method.
    // NeoForge gives each its own layer method. Forge does the same for the
    // bar, under names of its own, and draws the level in a layer of its own
    // that ForgeEntry hides instead; BAR_LEVEL there is the vanilla method,
    // which Forge keeps but no longer calls. Forge's own name is split so
    // 1.21.11's renames, which turn extract into render, pass it by.
    //#if FABRIC
    private static final String BAR_BACK = "extractHotbarAndDecorations";
    private static final String BAR_FRONT = "extractHotbarAndDecorations";
    private static final String BAR_LEVEL = "extractHotbarAndDecorations";
    //#elseif NEOFORGE
    //$$ private static final String BAR_BACK = "extractContextualInfoBarBackground";
    //$$ private static final String BAR_FRONT = "extractContextualInfoBar";
    //$$ private static final String BAR_LEVEL = "extractExperienceLevel";
    //#else
    //$$ private static final String BAR_BACK = "updateContextualInfo";
    //$$ private static final String BAR_FRONT = "extract" + "ContextualInfoState";
    //$$ private static final String BAR_LEVEL = "extractHotbarAndDecorations";
    //#endif

    //#if MC >= 26.2

    @Redirect(method = BAR_BACK, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractBackground("
                    + "Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
    private void spaceclient$barBack(ContextualBar bar, GuiGraphicsExtractor graphics, DeltaTracker delta) {
        if (bar instanceof ExperienceBar && !OverlayModule.xpBar()) return;
        bar.extractBackground(graphics, delta);
    }

    @Redirect(method = BAR_FRONT, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractRenderState("
                    + "Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
    private void spaceclient$barFront(ContextualBar bar, GuiGraphicsExtractor graphics, DeltaTracker delta) {
        if (bar instanceof ExperienceBar && !OverlayModule.xpBar()) return;
        bar.extractRenderState(graphics, delta);
    }

    @Redirect(method = BAR_LEVEL, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractExperienceLevel("
                    + "Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;I)V"))
    private void spaceclient$level(GuiGraphicsExtractor graphics, Font font, int level) {
        if (!OverlayModule.xpLevel()) return;
        ContextualBar.extractExperienceLevel(graphics, font, level);
    }
    //#else
    //$$
    //$$ @Redirect(method = BAR_BACK, at = @At(value = "INVOKE",
    //$$         target = "Lnet/minecraft/client/gui/contextualbar/ContextualBarRenderer;extractBackground("
    //$$                 + "Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
    //$$ private void spaceclient$barBack(ContextualBarRenderer bar, GuiGraphicsExtractor graphics, DeltaTracker delta) {
    //$$     if (bar instanceof ExperienceBarRenderer && !OverlayModule.xpBar()) return;
    //$$     bar.extractBackground(graphics, delta);
    //$$ }
    //$$
    //$$ @Redirect(method = BAR_FRONT, at = @At(value = "INVOKE",
    //$$         target = "Lnet/minecraft/client/gui/contextualbar/ContextualBarRenderer;extractRenderState("
    //$$                 + "Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
    //$$ private void spaceclient$barFront(ContextualBarRenderer bar, GuiGraphicsExtractor graphics, DeltaTracker delta) {
    //$$     if (bar instanceof ExperienceBarRenderer && !OverlayModule.xpBar()) return;
    //$$     bar.extractRenderState(graphics, delta);
    //$$ }
    //$$
    //$$ @Redirect(method = BAR_LEVEL, at = @At(value = "INVOKE",
    //$$         target = "Lnet/minecraft/client/gui/contextualbar/ContextualBarRenderer;extractExperienceLevel("
    //$$                 + "Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;I)V"))
    //$$ private void spaceclient$level(GuiGraphicsExtractor graphics, Font font, int level) {
    //$$     if (!OverlayModule.xpLevel()) return;
    //$$     ContextualBarRenderer.extractExperienceLevel(graphics, font, level);
    //$$ }
    //$$
    //#endif

    // NeoForge draws the name through an overload that also takes the height
    // of the bars above the hotbar
    //#if NEOFORGE
    //$$ @Inject(method = "extractSelectedItemName(Lnet/minecraft/client/gui/GuiGraphicsExtractor;I)V",
    //$$         at = @At("HEAD"), cancellable = true)
    //$$ private void spaceclient$itemName(GuiGraphicsExtractor graphics, int shift, CallbackInfo ci) {
    //$$     if (!OverlayModule.itemName()) ci.cancel();
    //$$ }
    //#else
    @Inject(method = "extractSelectedItemName(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
            at = @At("HEAD"), cancellable = true)
    private void spaceclient$itemName(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (!OverlayModule.itemName()) ci.cancel();
    }
    //#endif
}
