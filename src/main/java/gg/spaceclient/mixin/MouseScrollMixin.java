package gg.spaceclient.mixin;

import gg.spaceclient.input.RawMouse;

import net.minecraft.client.MouseHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives the zoom first look at the scroll wheel. See RawMouse.
 */
@Mixin(MouseHandler.class)
public abstract class MouseScrollMixin {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void spaceclient$scroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (RawMouse.onScroll(yOffset)) ci.cancel();
    }
}
