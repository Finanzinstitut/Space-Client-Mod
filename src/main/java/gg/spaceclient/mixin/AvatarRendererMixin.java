package gg.spaceclient.mixin;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives capes motion of their own.
 *
 * All this once did far more - it swapped in shop capes and stamped wings onto
 * the render state. That went with the shop, and none of it is missed here:
 * this steers the three cape angles vanilla already keeps, so it works on any
 * cape at all, whether it came from Mojang or from a cosmetics mod, without
 * knowing or caring where the cloth came from.
 */
@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void spaceclient$waveCape(Avatar avatar,
                                      AvatarRenderState state,
                                      float partialTick,
                                      CallbackInfo ci) {
        try {
            var manager = gg.spaceclient.SpaceClient.getModuleManager();
            if (manager == null) return;

            var module = manager.get("waveycape");
            if (!(module instanceof gg.spaceclient.modules.WaveyCapeModule wavey)) return;

            float[] shaped = wavey.shape(state.capeFlap, state.capeLean, state.capeLean2);
            if (shaped == null) return;

            state.capeFlap = shaped[0];
            state.capeLean = shaped[1];
            state.capeLean2 = shaped[2];

        } catch (Throwable ignored) {
            // Motion is a nicety; rendering the player is not
        }
    }
}
