package gg.spaceclient.mixin;

import gg.spaceclient.modules.SaturationModule;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Adds Saturation's pass to the effects the game asks for this frame.
 *
 * GameRenderer.update clears and refills requestedPostEffects every frame (the
 * end-of-frame pass, then the player's effects, then a spectated entity's), so
 * the list is extended right after it has been built.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererPostMixin {

    @Shadow @Final private List<Identifier> requestedPostEffects;

    /** The Overlay module's switch for the view tilting when hit; death still turns the camera. */
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void spaceclient$hurtCam(net.minecraft.client.renderer.state.level.CameraRenderState camera,
                                     com.mojang.blaze3d.vertex.PoseStack poseStack, CallbackInfo ci) {
        if (gg.spaceclient.modules.OverlayModule.hurtCam()) return;
        if (camera.entityRenderState != null && camera.entityRenderState.isDeadOrDying) return;
        ci.cancel();
    }

    /**
     * The Overlay module's nausea and portal sliders, applied to the warp the
     * game puts on the view. The green nausea tint is only drawn when the
     * game's own "distortion effects" option is turned down; with it at full,
     * which is the default, this warp is all nausea does - so scaling only the
     * tint left the slider doing nothing for most people.
     */
    @org.spongepowered.asm.mixin.injection.Redirect(method = "renderLevel", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/renderer/state/level/PlayerRenderState;nauseaEffectIntensity:F",
            opcode = org.objectweb.asm.Opcodes.GETFIELD))
    private float spaceclient$nauseaWarp(net.minecraft.client.renderer.state.level.PlayerRenderState state) {
        return state.nauseaEffectIntensity * gg.spaceclient.modules.OverlayModule.nauseaAlpha();
    }

    @org.spongepowered.asm.mixin.injection.Redirect(method = "renderLevel", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/renderer/state/level/PlayerRenderState;portalEffectIntensity:F",
            opcode = org.objectweb.asm.Opcodes.GETFIELD))
    private float spaceclient$portalWarp(net.minecraft.client.renderer.state.level.PlayerRenderState state) {
        return state.portalEffectIntensity * gg.spaceclient.modules.OverlayModule.portalAlpha();
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void spaceclient$saturation(DeltaTracker deltaTracker, CallbackInfo ci) {
        try {
            // The first entry is always the end-of-frame pass; anything after
            // it is an effect the game itself wants on screen
            int vanilla = Math.max(0, requestedPostEffects.size() - 1);
            SaturationModule.contribute(requestedPostEffects, vanilla);
        } catch (Throwable ignored) {
            // Colours as the game draws them
        }
    }
}
