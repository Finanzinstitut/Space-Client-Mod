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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Space Client's post effects (Motion Blur, Saturation), the Overlay module's
 * hurt-cam switch and its nausea and portal warp.
 *
 * <h2>Post effects</h2>
 *
 * From 26.3 the game keeps a list of effects per frame, rebuilt in
 * GameRenderer.update, so ours are added to it right after. Before 26.3 the
 * game has room for one effect only - the creeper or spider view when
 * spectating - so ours are run straight after it, on the same target, at the
 * end of render().
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererPostMixin {

    //#if MC >= 26.3
    @Shadow @Final private List<Identifier> requestedPostEffects;
    //#else
    //$$ @Shadow private Identifier postEffectId;
    //$$ @Shadow private boolean effectActive;
    //$$ @Shadow @Final private com.mojang.blaze3d.resource.CrossFrameResourcePool resourcePool;
    //#endif

    // ---------------------------------------------------------------- hurt cam

    /** The Overlay module's switch for the view tilting when hit; death still turns the camera. */
    //#if MC >= 26.1
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void spaceclient$hurtCam(net.minecraft.client.renderer.state.level.CameraRenderState camera,
                                     com.mojang.blaze3d.vertex.PoseStack poseStack, CallbackInfo ci) {
        if (gg.spaceclient.modules.OverlayModule.hurtCam()) return;
        if (camera.entityRenderState != null && camera.entityRenderState.isDeadOrDying) return;
        ci.cancel();
    }
    //#else
    //$$ @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    //$$ private void spaceclient$hurtCam(com.mojang.blaze3d.vertex.PoseStack poseStack, float partialTicks, CallbackInfo ci) {
    //$$     if (gg.spaceclient.modules.OverlayModule.hurtCam()) return;
    //$$     var camera = net.minecraft.client.Minecraft.getInstance().getCameraEntity();
    //$$     if (camera instanceof net.minecraft.world.entity.LivingEntity living && living.isDeadOrDying()) return;
    //$$     ci.cancel();
    //$$ }
    //#endif

    // ---------------------------------------------------------------- nausea and portal

    /*
     * The Overlay module's nausea and portal sliders, applied to the warp the
     * game puts on the view. The green nausea tint is only drawn when the
     * game's own "distortion effects" option is turned down; with it at full,
     * which is the default, this warp is all nausea does - so scaling only the
     * tint left the slider doing nothing for most people.
     */
    //#if MC >= 26.3
    @Redirect(method = "renderLevel", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/renderer/state/level/PlayerRenderState;nauseaEffectIntensity:F",
            opcode = org.objectweb.asm.Opcodes.GETFIELD))
    private float spaceclient$nauseaWarp(net.minecraft.client.renderer.state.level.PlayerRenderState state) {
        return state.nauseaEffectIntensity * gg.spaceclient.modules.OverlayModule.nauseaAlpha();
    }

    @Redirect(method = "renderLevel", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/renderer/state/level/PlayerRenderState;portalEffectIntensity:F",
            opcode = org.objectweb.asm.Opcodes.GETFIELD))
    private float spaceclient$portalWarp(net.minecraft.client.renderer.state.level.PlayerRenderState state) {
        return state.portalEffectIntensity * gg.spaceclient.modules.OverlayModule.portalAlpha();
    }
    //#else
    //$$ @Redirect(method = "renderLevel", at = @At(value = "INVOKE",
    //$$         target = "Lnet/minecraft/client/player/LocalPlayer;getEffectBlendFactor(Lnet/minecraft/core/Holder;F)F"))
    //$$ private float spaceclient$nauseaWarp(net.minecraft.client.player.LocalPlayer player,
    //$$                                      net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, float partialTicks) {
    //$$     return player.getEffectBlendFactor(effect, partialTicks) * gg.spaceclient.modules.OverlayModule.nauseaAlpha();
    //$$ }
    //$$
    //$$ @Redirect(method = "renderLevel", at = @At(value = "FIELD",
    //$$         target = "Lnet/minecraft/client/player/LocalPlayer;portalEffectIntensity:F",
    //$$         opcode = org.objectweb.asm.Opcodes.GETFIELD))
    //$$ private float spaceclient$portalWarp(net.minecraft.client.player.LocalPlayer player) {
    //$$     return player.portalEffectIntensity * gg.spaceclient.modules.OverlayModule.portalAlpha();
    //$$ }
    //$$
    //$$ @Redirect(method = "renderLevel", at = @At(value = "FIELD",
    //$$         target = "Lnet/minecraft/client/player/LocalPlayer;oPortalEffectIntensity:F",
    //$$         opcode = org.objectweb.asm.Opcodes.GETFIELD))
    //$$ private float spaceclient$portalWarpBefore(net.minecraft.client.player.LocalPlayer player) {
    //$$     return player.oPortalEffectIntensity * gg.spaceclient.modules.OverlayModule.portalAlpha();
    //$$ }
    //#endif

    // ---------------------------------------------------------------- post effects

    //#if MC >= 26.3
    @Inject(method = "update", at = @At("TAIL"))
    private void spaceclient$postEffects(DeltaTracker deltaTracker, CallbackInfo ci) {
        try {
            // The first entry is always the end-of-frame pass; anything after
            // it is an effect the game itself wants on screen
            int vanilla = Math.max(0, requestedPostEffects.size() - 1);
            gg.spaceclient.modules.MotionBlurModule.contribute(requestedPostEffects);
            SaturationModule.contribute(requestedPostEffects, vanilla);
        } catch (Throwable ignored) {
            // Colours as the game draws them
        }
    }
    //#else
    //$$ @Inject(method = "render", at = @At(value = "INVOKE",
    //$$         target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
    //$$ private void spaceclient$postEffects(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
    //$$     if (!renderLevel) return;
    //$$     try {
    //$$         List<Identifier> ours = new java.util.ArrayList<>(2);
    //$$         int vanilla = postEffectId != null && effectActive ? 1 : 0;
    //$$         gg.spaceclient.modules.MotionBlurModule.contribute(ours);
    //$$         SaturationModule.contribute(ours, vanilla);
    //$$         if (ours.isEmpty()) return;
    //$$         var mc = net.minecraft.client.Minecraft.getInstance();
    //#if MC >= 26.2
    //$$         com.mojang.blaze3d.pipeline.RenderTarget target = ((GameRenderer) (Object) this).mainRenderTarget();
    //#else
    //$$         com.mojang.blaze3d.pipeline.RenderTarget target = mc.getMainRenderTarget();
    //#endif
    //$$         for (Identifier id : ours) {
    //$$             var chain = mc.getShaderManager().getPostChain(id, net.minecraft.client.renderer.LevelTargetBundle.MAIN_TARGETS);
    //$$             if (chain != null) chain.process(target, resourcePool);
    //$$         }
    //$$     } catch (Throwable ignored) {
    //$$         // Colours as the game draws them
    //$$     }
    //$$ }
    //#endif
}
