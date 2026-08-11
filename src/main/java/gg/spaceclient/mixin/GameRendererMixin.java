package gg.spaceclient.mixin;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.modules.visual.ZoomModule;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Scales the field of view while the zoom key is held. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void spaceclient$zoom(Camera camera, float tickDelta, boolean changingFov,
                                  CallbackInfoReturnable<Float> cir) {
        ZoomModule zoom = (ZoomModule) SpaceClient.getModuleManager().get("zoom");
        if (zoom == null || !zoom.isEnabled() || !zoom.isZooming()) return;

        cir.setReturnValue(cir.getReturnValue() / zoom.getCurrentFactor());
    }
}
