package gg.spaceclient.mixin;

import gg.spaceclient.modules.FullbrightModule;

import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hands the freshly filled light map inputs to Fullbright. */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapMixin {

    @Inject(method = "extract", at = @At("TAIL"))
    private void spaceclient$fullbright(LightmapRenderState state, float partialTick, CallbackInfo ci) {
        try {
            FullbrightModule.apply(state);
        } catch (Throwable ignored) {
            // The game's own light, untouched
        }
    }
}
