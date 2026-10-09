package gg.spaceclient.mixin;

import gg.spaceclient.access.ItemScaleReport;
import gg.spaceclient.config.ItemSizes;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
//#if MC >= 26.3
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
//#else
//$$ import net.minecraft.client.renderer.ItemInHandRenderer;
//$$ import net.minecraft.client.player.AbstractClientPlayer;
//$$ import net.minecraft.world.entity.LivingEntity;
//$$ import net.minecraft.world.item.ItemDisplayContext;
//#endif
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
//#if MC >= 26.3
@Mixin(FirstPersonHandsAndItemsRenderer.class)
//#else
//$$ @Mixin(ItemInHandRenderer.class)
//#endif
public class ItemInHandRendererMixin {

    //#if MC >= 26.3
    private static final String ARM = "submitArmWithItem";
    //#elseif MC >= 26.2
    //$$ private static final String ARM = "submitArmWithItem";
    //#else
    //$$ private static final String ARM = "renderArmWithItem";
    //#endif

    /** The stack of the arm being drawn; render thread only. */
    @Unique
    private static ItemStack spaceclient$stack = ItemStack.EMPTY;

    @Unique
    private static InteractionHand spaceclient$hand = InteractionHand.MAIN_HAND;

    //#if MC >= 26.3
    @Inject(method = ARM, at = @At("HEAD"))
    private void spaceclient$note(PlayerRenderState player, FirstPersonHandsAndItemsRenderState state,
                                  float a, float b, InteractionHand hand, float c, ItemStack stack,
                                  float d, PoseStack poseStack, SubmitNodeCollector collector, int light,
                                  CallbackInfo ci) {
        spaceclient$stack = stack;
        spaceclient$hand = hand;
    }

    @Redirect(method = ARM, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit("
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void spaceclient$scaled(ItemStackRenderState item, PoseStack poseStack,
                                    SubmitNodeCollector collector, int light, int overlay, int outline) {
        spaceclient$draw(poseStack, () -> item.submit(poseStack, collector, light, overlay, outline));
    }
    //#else
    //$$ @Inject(method = ARM, at = @At("HEAD"))
    //$$ private void spaceclient$note(AbstractClientPlayer player, float a, float b, InteractionHand hand,
    //$$                               float c, ItemStack stack, float d, PoseStack poseStack,
    //$$                               SubmitNodeCollector collector, int light, CallbackInfo ci) {
    //$$     spaceclient$stack = stack;
    //$$     spaceclient$hand = hand;
    //$$ }
    //$$
    //$$ @Redirect(method = ARM, at = @At(value = "INVOKE",
    //$$         target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem("
    //$$                 + "Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;"
    //$$                 + "Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;"
    //$$                 + "Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"))
    //$$ private void spaceclient$scaled(ItemInHandRenderer renderer, LivingEntity owner, ItemStack stack,
    //$$                                 ItemDisplayContext context, PoseStack poseStack,
    //$$                                 SubmitNodeCollector collector, int light) {
    //$$     spaceclient$draw(poseStack, () -> renderer.renderItem(owner, stack, context, poseStack, collector, light));
    //$$ }
    //#endif

    /** Draws the held item with the Overlay module's moves and the item size applied. */
    @Unique
    private static void spaceclient$draw(PoseStack poseStack, Runnable submit) {
        ItemScaleReport.sawHand();
        // The Overlay module can leave the off-hand item out entirely
        if (spaceclient$hand == InteractionHand.OFF_HAND && !gg.spaceclient.modules.OverlayModule.offhand()) return;
        float scale = spaceclient$scaleFor(spaceclient$stack);
        boolean shield = spaceclient$stack.is(Items.SHIELD);
        boolean moved = gg.spaceclient.modules.OverlayModule.movesHands();
        if (scale == 1f && !shield && !moved) {
            submit.run();
            return;
        }
        poseStack.pushPose();
        try {
            // The Overlay module's hand position, its shield height and turn,
            // then the item size
            gg.spaceclient.modules.OverlayModule.hand(poseStack, spaceclient$hand);
            if (shield) gg.spaceclient.modules.OverlayModule.shield(poseStack, spaceclient$hand);
            if (scale != 1f) poseStack.scale(scale, scale, scale);
            submit.run();
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
