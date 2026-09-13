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

    /**
     * Hooked onto the cape's own extraction, not the general one.
     *
     * The general one is why this never worked. extractRenderState exists three
     * times on this class - once for the avatar, once for the living entity it
     * inherits from, and once for the plain entity - so naming it without a
     * descriptor gave the injector three candidates and no way to choose. With
     * require = 0 that resolved to doing nothing at all, silently, which is
     * exactly what it looked like in game.
     *
     * extractCapeState is a single private method that exists for this and
     * nothing else, so there is nothing to disambiguate. The descriptor is
     * spelled out anyway: the entity parameter is a type variable bounded by
     * Avatar, and erasure turns that into Avatar itself.
     */
    @Inject(
            method = "extractCapeState(Lnet/minecraft/world/entity/Avatar;"
                    + "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"),
            require = 0)
    private void spaceclient$waveCape(Avatar avatar,
                                      AvatarRenderState state,
                                      float partialTick,
                                      CallbackInfo ci) {
        try {
            gg.spaceclient.render.CapeReport.ran();

            // Everything reported below is about the cape you can actually see.
            // This hook runs for every player in view, so without this the
            // report would be describing somebody else's cape on a server.
            // Held as Object because the comparison is identity, not type: the
            // local player's class is one more name that moved in 26.2.
            Object self = net.minecraft.client.Minecraft.getInstance().player;
            boolean isSelf = self != null && self == avatar;
            if (isSelf) {
                gg.spaceclient.render.CapeReport.sawSelf();
                // Before the write, so the reading is vanilla's and not ours
                gg.spaceclient.render.CapeReport.snapshot(state);
            }

            var manager = gg.spaceclient.SpaceClient.getModuleManager();
            if (manager == null) {
                if (isSelf) gg.spaceclient.render.CapeReport.skipped("the module manager is not up yet");
                return;
            }

            var module = manager.get("waveycape");
            if (!(module instanceof gg.spaceclient.modules.WaveyCapeModule wavey)) {
                if (isSelf) gg.spaceclient.render.CapeReport.skipped("the Wavey Cape module was not found");
                return;
            }

            float flapBefore = state.capeFlap;
            float leanBefore = state.capeLean;
            float lean2Before = state.capeLean2;

            float[] shaped = wavey.shape(flapBefore, leanBefore, lean2Before);
            if (shaped == null) {
                if (isSelf) gg.spaceclient.render.CapeReport.skipped("the Wavey Cape module is switched off");
                return;
            }

            state.capeFlap = shaped[0];
            state.capeLean = shaped[1];
            state.capeLean2 = shaped[2];

            if (isSelf) {
                gg.spaceclient.render.CapeReport.applied(
                        flapBefore, leanBefore, lean2Before,
                        shaped[0], shaped[1], shaped[2]);
            }

        } catch (Throwable ignored) {
            // Motion is a nicety; rendering the player is not
        }
    }
}
