package gg.spaceclient.mixin;

import gg.spaceclient.modules.OverlayModule;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Overlay module's hooks into what is drawn in front of the camera: the
 * fire when burning, the underwater tint and the block seen from inside a wall.
 */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectMixin {

    @Inject(method = "submitFire", at = @At("HEAD"))
    private static void spaceclient$fireIn(PoseStack poseStack, SubmitNodeCollector collector,
                                           TextureAtlasSprite sprite, CallbackInfo ci) {
        poseStack.pushPose();
        float lower = OverlayModule.fireLower();
        if (lower != 0f) poseStack.translate(0f, -lower, 0f);
    }

    @Inject(method = "submitFire", at = @At("RETURN"))
    private static void spaceclient$fireOut(PoseStack poseStack, SubmitNodeCollector collector,
                                            TextureAtlasSprite sprite, CallbackInfo ci) {
        poseStack.popPose();
    }

    /** The fire's colour: white at 0.9 alpha in vanilla. */
    @ModifyConstant(method = "buildFireQuad", constant = @Constant(intValue = 0xE5FFFFFF))
    private static int spaceclient$fireColour(int vanilla) {
        return OverlayModule.fireColour(vanilla);
    }

    @Inject(method = "submitWater", at = @At("HEAD"), cancellable = true)
    private static void spaceclient$water(PlayerRenderState.WaterOverlay overlay, PoseStack poseStack,
                                          SubmitNodeCollector collector, CallbackInfo ci) {
        if (!OverlayModule.underwater()) ci.cancel();
    }

    @Inject(method = "submitBlockSprite", at = @At("HEAD"), cancellable = true)
    private static void spaceclient$inWall(Identifier atlas, float u0, float v0, float u1, float v1,
                                           PoseStack poseStack, SubmitNodeCollector collector, int light,
                                           CallbackInfo ci) {
        if (!OverlayModule.inWall()) ci.cancel();
    }
}
