package gg.spaceclient.mixin;

import gg.spaceclient.access.ItemIdHolder;
import gg.spaceclient.access.ItemScaleReport;
import gg.spaceclient.config.ItemSizes;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.item.ItemEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scales dropped items.
 *
 * This is the one that matters. When somebody dies in a fight their inventory
 * lands as thirty entities in one heap, and a totem in that heap looks exactly
 * like the cobblestone next to it. Drawing one item type larger turns reading
 * the pile into seeing a shape.
 *
 * The id is captured during extraction, because that is the last point at which
 * the actual stack is in reach - the render state carries a baked model and
 * nothing that says what the item was.
 */
@Mixin(ItemEntityRenderer.class)
public class ItemEntityRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;"
            + "Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",
            at = @At("TAIL"))
    private void spaceclient$rememberId(ItemEntity entity, ItemEntityRenderState state,
                                        float partialTicks, CallbackInfo ci) {
        try {
            // Über ItemSizes.keyFor, nicht stack.getDescriptionId(): das gibt
            // es auf 26.2 nicht, die Id sitzt am Item statt am Stack.
            ((ItemIdHolder) (Object) state)
                    .spaceclient$setItemId(ItemSizes.keyFor(entity.getItem()));
        } catch (Throwable ignored) {
            // Without an id the item simply draws at its normal size
        }
    }

    /**
     * Pushed at the top and popped at every exit.
     *
     * The method returns early when the stack is empty, so the pop has to sit
     * on RETURN rather than after the last statement - otherwise that one path
     * would leave the matrix pushed and everything drawn afterwards in the
     * frame would inherit the scale.
     */
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
            + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"))
    private void spaceclient$grow(ItemEntityRenderState state, PoseStack poseStack,
                                  SubmitNodeCollector collector, CameraRenderState camera,
                                  CallbackInfo ci) {
        ItemScaleReport.sawGround();
        poseStack.pushPose();
        float scale = spaceclient$scaleFor(state);
        if (scale != 1f) poseStack.scale(scale, scale, scale);
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
            + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("RETURN"))
    private void spaceclient$shrink(ItemEntityRenderState state, PoseStack poseStack,
                                    SubmitNodeCollector collector, CameraRenderState camera,
                                    CallbackInfo ci) {
        poseStack.popPose();
    }

    private static float spaceclient$scaleFor(ItemEntityRenderState state) {
        try {
            String id = ((ItemIdHolder) (Object) state).spaceclient$itemId();
            return ItemSizes.get(id).ground();
        } catch (Throwable ignored) {
            return 1f;
        }
    }
}
