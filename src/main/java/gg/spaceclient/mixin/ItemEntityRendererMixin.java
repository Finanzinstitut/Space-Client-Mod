package gg.spaceclient.mixin;

import gg.spaceclient.access.ItemIdHolder;
import gg.spaceclient.access.ItemPhysicsHolder;
import gg.spaceclient.access.ItemScaleReport;
import gg.spaceclient.config.ItemSizes;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
//#if MC >= 26.1
import net.minecraft.client.renderer.state.level.CameraRenderState;
//#else
//$$ import net.minecraft.client.renderer.state.CameraRenderState;
//#endif
import net.minecraft.world.entity.item.ItemEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
            String id = ItemSizes.keyFor(entity.getItem());
            ((ItemIdHolder) (Object) state).spaceclient$setItemId(id);
            ItemScaleReport.sawId(id);
        } catch (Throwable ignored) {
            // Without an id the item simply draws at its normal size
        }

        spaceclient$settle(entity, state, partialTicks);
        spaceclient$judge(entity, state);
    }

    /**
     * Decides here whether this item is worth drawing at all.
     *
     * Moved from shouldRender, which never matched: the diagnostics said so
     * plainly - "hook never ran". Rather than guess that method's shape a
     * second time, the decision rides on the one injection in this class whose
     * descriptor is spelled out and proven to apply.
     *
     * The verdict cannot cancel anything from here, so it is carried on the
     * render state and acted on when the item is submitted. That costs the
     * extraction of an item that will not be drawn, which is a fraction of
     * what drawing it costs.
     */
    private void spaceclient$judge(ItemEntity entity, ItemEntityRenderState state) {
        try {
            var manager = gg.spaceclient.SpaceClient.getModuleManager();
            if (manager == null) return;

            var module = manager.get("fpsboost");
            if (!(module instanceof gg.spaceclient.modules.FpsBoostModule boost)) return;

            boolean draw = boost.allowItem(entity);
            ((ItemPhysicsHolder) (Object) state).spaceclient$setSkipped(!draw);

            if (draw) gg.spaceclient.render.CullReport.keptItem();
            else gg.spaceclient.render.CullReport.culledItem();

        } catch (Throwable ignored) {
            // Draw it, as the game would have
        }
    }

    /**
     * Works out how this item is lying, once, while the entity is still here.
     *
     * Both halves have to happen at this point rather than at drawing time.
     * The yaw has to be stable across frames or the item shivers, and the spin
     * can only be cancelled by emptying what it is computed from - which the
     * drawing step has already read by the time it runs.
     */
    private void spaceclient$settle(ItemEntity entity, ItemEntityRenderState state, float partialTicks) {
        try {
            var manager = gg.spaceclient.SpaceClient.getModuleManager();
            if (manager == null) return;

            var module = manager.get("itemphysics");
            if (!(module instanceof gg.spaceclient.modules.ItemPhysicsModule physics)) return;
            if (!physics.isEnabled()) return;

            var holder = (ItemPhysicsHolder) (Object) state;

            // Settled means resting on something - the ground, or floating on
            // water, where an item tumbling forever would look broken. Anything
            // else is in the air and turns over as it falls.
            boolean settled = entity.onGround() || entity.isInWater() || entity.isInLava();

            holder.spaceclient$setSettled(settled);
            holder.spaceclient$setRestYaw(physics.restYawFor(entity.getId()));

            boolean tumbling = !settled && physics.tumblesInAir();
            if (tumbling) {
                float age = entity.tickCount + partialTicks;
                holder.spaceclient$setTumble(physics.tumbleFor(entity.getId(), age));
            }

            // A tumbling or flat item carries its own pose, and the drawing
            // step undoes the game's lift on the assumption that the bob is
            // gone - so for those both are always stilled.
            boolean posed = tumbling || (settled && physics.laysFlat());
            if (posed || (settled && (physics.stopsSpin() || physics.stopsBob()))) {
                gg.spaceclient.render.ItemStateFields.still(state);
            }

        } catch (Throwable ignored) {
            // Physics is a nicety; drawing the item is not
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
            at = @At("HEAD"), cancellable = true)
    private void spaceclient$grow(ItemEntityRenderState state, PoseStack poseStack,
                                  SubmitNodeCollector collector, CameraRenderState camera,
                                  CallbackInfo ci) {
        // Before anything is pushed. Cancelling after a pushPose would leave
        // the matrix stack unbalanced and every later draw inside a transform
        // that was never meant for it - a whole frame ruined to save one item.
        try {
            if (((ItemPhysicsHolder) (Object) state).spaceclient$isSkipped()) {
                ci.cancel();
                return;
            }
        } catch (Throwable ignored) {
            // Draw it
        }

        ItemScaleReport.sawGround();
        poseStack.pushPose();
        float scale = spaceclient$scaleFor(state);
        if (scale != 1f) poseStack.scale(scale, scale, scale);

        // After the scale on purpose: the pose is worked out from the model's
        // own box, which the scale enlarges along with everything else, so a
        // large item still rests on its face instead of sinking or floating.
        spaceclient$lay(state, poseStack);
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

    /**
     * Turns a settled item onto its face on the ground, or a falling one over
     * about its own middle.
     *
     * The game draws the item after this, with one more lift of its own -
     * 0.1625 blocks plus however far the model reaches below its origin - and
     * that lift happens inside whatever rotation is set up here. Undone first,
     * then, or a flat item would be shoved sideways by it and a falling one
     * would swing round a point below itself instead of tumbling. Both cases
     * work from the model's own bounding box, so a sword, a block and a totem
     * all rest on their faces rather than at one height that fits none of them.
     */
    private static void spaceclient$lay(ItemEntityRenderState state, PoseStack poseStack) {
        try {
            var manager = gg.spaceclient.SpaceClient.getModuleManager();
            if (manager == null) return;

            var module = manager.get("itemphysics");
            if (!(module instanceof gg.spaceclient.modules.ItemPhysicsModule physics)) return;
            if (!physics.laysFlat()) return;

            var holder = (ItemPhysicsHolder) (Object) state;
            boolean settled = holder.spaceclient$isSettled();
            if (!settled && !physics.tumblesInAir()) return;

            net.minecraft.world.phys.AABB box = state.item.getModelBoundingBox();
            // The game's own lift, with the bob already zeroed during extraction
            float lift = 0.1f + 0.0625f - (float) box.minY;
            float cx = (float) ((box.minX + box.maxX) / 2);
            float cy = (float) ((box.minY + box.maxY) / 2);
            float cz = (float) ((box.minZ + box.maxZ) / 2);

            float yaw = holder.spaceclient$restYaw();
            float pitch;
            float height;
            if (settled) {
                // Face down: the model's depth becomes its height, and the
                // middle sits half of that above the floor, less the sink. The
                // sink is capped at half of that half: a flat item is only a
                // thirty-second of a block thick, and a sink chosen with a
                // block in mind would bury it out of sight.
                pitch = 90f;
                float half = (float) (box.maxZ - box.minZ) / 2f;
                height = half - Math.min(physics.sinkDepth(), half * 0.5f);
            } else {
                pitch = holder.spaceclient$tumble();
                height = lift + cy;
            }

            poseStack.translate(0f, height, 0f);
            gg.spaceclient.render.PoseOps.rotate(poseStack, yaw, pitch);
            poseStack.translate(-cx, -(lift + cy), -cz);

        } catch (Throwable ignored) {
            // The item draws upright, as it always did
        }
    }

    private static float spaceclient$scaleFor(ItemEntityRenderState state) {
        // Asked rather than caught. A failed cast here used to be swallowed
        // like any other, which turned "the state mixin never applied" into
        // "the size setting does nothing" - the same symptom as a setting left
        // at 100%, and no way to tell the two apart from inside the game.
        boolean holder = state instanceof ItemIdHolder;
        ItemScaleReport.sawHolder(holder);
        if (!holder) return 1f;

        try {
            String id = ((ItemIdHolder) (Object) state).spaceclient$itemId();
            return ItemSizes.effective(id).ground();
        } catch (Throwable ignored) {
            return 1f;
        }
    }
}
