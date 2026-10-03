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
