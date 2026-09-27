package gg.spaceclient.mixin;

import gg.spaceclient.wavey.WaveyCapes;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the vanilla cape from drawing under the wavey one.
 *
 * WaveyCapes removes the vanilla layer when the renderer is built; here it is
 * only silenced while the module is on, so it is back the moment it is off.
 */
@Mixin(CapeLayer.class)
public class CapeLayerMixin {

    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;I"
            + "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void spaceclient$makeWay(PoseStack poseStack, SubmitNodeCollector collector, int light,
                                     AvatarRenderState state, float yRot, float xRot, CallbackInfo ci) {
        if (WaveyCapes.enabled) ci.cancel();
    }
}
