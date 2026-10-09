package gg.spaceclient.mixin;

import gg.spaceclient.modules.OverlayModule;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
//#if MC >= 26.3
import net.minecraft.client.renderer.state.level.PlayerRenderState;
//#endif
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

    //#if MC >= 26.2
    private static final String FIRE = "submitFire";
    //#else
    //$$ private static final String FIRE = "renderFire";
    //#endif

    // The fire's pose: pushed, lowered, and popped again at every exit
    //#if MC >= 26.2
    @Inject(method = FIRE, at = @At("HEAD"))
    private static void spaceclient$fireIn(PoseStack poseStack, SubmitNodeCollector collector,
                                           TextureAtlasSprite sprite, CallbackInfo ci) {
        spaceclient$lower(poseStack);
    }

    @Inject(method = FIRE, at = @At("RETURN"))
    private static void spaceclient$fireOut(PoseStack poseStack, SubmitNodeCollector collector,
                                            TextureAtlasSprite sprite, CallbackInfo ci) {
        poseStack.popPose();
    }
    //#else
    //$$ @Inject(method = FIRE, at = @At("HEAD"))
    //$$ private static void spaceclient$fireIn(PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffers,
    //$$                                        TextureAtlasSprite sprite, CallbackInfo ci) {
    //$$     spaceclient$lower(poseStack);
    //$$ }
    //$$
    //$$ @Inject(method = FIRE, at = @At("RETURN"))
    //$$ private static void spaceclient$fireOut(PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffers,
    //$$                                         TextureAtlasSprite sprite, CallbackInfo ci) {
    //$$     poseStack.popPose();
    //$$ }
    //#endif

    private static void spaceclient$lower(PoseStack poseStack) {
        poseStack.pushPose();
        float lower = OverlayModule.fireLower();
        if (lower != 0f) poseStack.translate(0f, -lower, 0f);
    }

    //#if MC >= 26.2
    /** The fire's colour: white at 0.9 alpha in vanilla, as one packed int. */
    @ModifyConstant(method = "buildFireQuad", constant = @Constant(intValue = 0xE5FFFFFF))
    private static int spaceclient$fireColour(int vanilla) {
        return OverlayModule.fireColour(vanilla);
    }
    //#else
    //$$ /** The fire's alpha: 0.9 in vanilla, given as a float to each corner. */
    //$$ @ModifyConstant(method = FIRE, constant = @Constant(floatValue = 0.9f))
    //$$ private static float spaceclient$fireAlpha(float vanilla) {
    //$$     return ((OverlayModule.fireColour(0xE5FFFFFF) >>> 24) & 0xFF) / 255f;
    //$$ }
    //#endif

    //#if MC >= 26.3
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
    //#elseif MC >= 26.2
    //$$ @Inject(method = "submitWater", at = @At("HEAD"), cancellable = true)
    //$$ private static void spaceclient$water(net.minecraft.client.Minecraft minecraft, PoseStack poseStack,
    //$$                                       SubmitNodeCollector collector, CallbackInfo ci) {
    //$$     if (!OverlayModule.underwater()) ci.cancel();
    //$$ }
    //$$
    //$$ @Inject(method = "submitBlockSprite", at = @At("HEAD"), cancellable = true)
    //$$ private static void spaceclient$inWall(TextureAtlasSprite sprite, PoseStack poseStack,
    //$$                                        SubmitNodeCollector collector, int light, CallbackInfo ci) {
    //$$     if (!OverlayModule.inWall()) ci.cancel();
    //$$ }
    //#else
    //$$ @Inject(method = "renderWater", at = @At("HEAD"), cancellable = true)
    //$$ private static void spaceclient$water(net.minecraft.client.Minecraft minecraft, PoseStack poseStack,
    //$$                                       net.minecraft.client.renderer.MultiBufferSource buffers, CallbackInfo ci) {
    //$$     if (!OverlayModule.underwater()) ci.cancel();
    //$$ }
    //$$
    //$$ @Inject(method = "renderTex", at = @At("HEAD"), cancellable = true)
    //$$ private static void spaceclient$inWall(TextureAtlasSprite sprite, PoseStack poseStack,
    //$$                                        net.minecraft.client.renderer.MultiBufferSource buffers, CallbackInfo ci) {
    //$$     if (!OverlayModule.inWall()) ci.cancel();
    //$$ }
    //#endif
}
