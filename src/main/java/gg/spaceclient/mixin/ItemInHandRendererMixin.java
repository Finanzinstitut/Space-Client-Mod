package gg.spaceclient.mixin;

import gg.spaceclient.access.ItemScaleReport;
import gg.spaceclient.config.ItemSizes;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scales the item held in first person.
 *
 * 26.3 replaced ItemInHandRenderer with FirstPersonHandsAndItemsRenderer, and
 * the item is no longer drawn by a method of its own: submitArmWithItem moves
 * the pose for the arm and then hands the item's render state to submit. So the
 * stack is noted when the arm starts, and the scale goes around that one submit
 * call - after the arm transforms, exactly where renderItem used to start.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class ItemInHandRendererMixin {

    /** The stack of the arm being drawn; render thread only. */
    @Unique
    private static ItemStack spaceclient$stack = ItemStack.EMPTY;

    @Unique
    private static InteractionHand spaceclient$hand = InteractionHand.MAIN_HAND;

    @Inject(method = "submitArmWithItem", at = @At("HEAD"))
    private void spaceclient$note(PlayerRenderState player, FirstPersonHandsAndItemsRenderState state,
                                  float a, float b, InteractionHand hand, float c, ItemStack stack,
                                  float d, PoseStack poseStack, SubmitNodeCollector collector, int light,
                                  CallbackInfo ci) {
        spaceclient$stack = stack;
        spaceclient$hand = hand;
    }

    @Redirect(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit("
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void spaceclient$scaled(ItemStackRenderState item, PoseStack poseStack,
                                    SubmitNodeCollector collector, int light, int overlay, int outline) {
        ItemScaleReport.sawHand();
        float scale = spaceclient$scaleFor(spaceclient$stack);
        boolean shield = spaceclient$stack.is(Items.SHIELD);
        if (scale == 1f && !shield) {
            item.submit(poseStack, collector, light, overlay, outline);
            return;
        }
        poseStack.pushPose();
        try {
            // The Overlay module's shield height and turn, then the item size
            if (shield) gg.spaceclient.modules.OverlayModule.shield(poseStack, spaceclient$hand);
            if (scale != 1f) poseStack.scale(scale, scale, scale);
            item.submit(poseStack, collector, light, overlay, outline);
        } finally {
            poseStack.popPose();
        }
    }

    @Unique
    private static float spaceclient$scaleFor(ItemStack stack) {
        try {
            return ItemSizes.effective(ItemSizes.keyFor(stack)).hand();
        } catch (Throwable ignored) {
            return 1f;
        }
    }
}
