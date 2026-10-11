package gg.spaceclient.mixin;

import gg.spaceclient.modules.FullbrightModule;

//#if MC >= 26.1
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
//#endif

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hands the freshly filled light map inputs to Fullbright. */
//#if MC >= 26.1
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
//#else
//$$ @Mixin(net.minecraft.client.renderer.LightTexture.class)
//$$ public abstract class LightmapMixin {
//$$
//$$     /** The light texture only asks for night vision's strength when the player has it. */
//$$     @org.spongepowered.asm.mixin.injection.Redirect(method = "updateLightTexture", at = @At(value = "INVOKE",
//$$             target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/core/Holder;)Z"))
//$$     private boolean spaceclient$hasEffect(net.minecraft.client.player.LocalPlayer player,
//$$                                           net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
//$$         if (effect == net.minecraft.world.effect.MobEffects.NIGHT_VISION && FullbrightModule.forcesNightVision()) return true;
//$$         return player.hasEffect(effect);
//$$     }
//$$
//$$     @org.spongepowered.asm.mixin.injection.Redirect(method = "updateLightTexture", at = @At(value = "INVOKE",
//$$             target = "Lnet/minecraft/client/renderer/GameRenderer;getNightVisionScale(Lnet/minecraft/world/entity/LivingEntity;F)F"))
//$$     private float spaceclient$nightVision(net.minecraft.world.entity.LivingEntity entity, float partialTicks) {
//$$         // Asked only for a player that really has the effect: the game's
//$$         // method reads the effect instance and would fail without one
//$$         float vanilla = entity.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION)
//$$                 ? net.minecraft.client.renderer.GameRenderer.getNightVisionScale(entity, partialTicks) : 0f;
//$$         return FullbrightModule.nightVision(vanilla);
//$$     }
//$$
//$$     @org.spongepowered.asm.mixin.injection.Redirect(method = "updateLightTexture", at = @At(value = "INVOKE",
//$$             target = "Lnet/minecraft/client/renderer/LightTexture;calculateDarknessScale(Lnet/minecraft/world/entity/LivingEntity;FF)F"))
//$$     private float spaceclient$darkness(net.minecraft.client.renderer.LightTexture self,
//$$                                        net.minecraft.world.entity.LivingEntity entity, float factor, float partialTicks) {
//$$         return FullbrightModule.darkness(((LightTextureInvoker) self).spaceclient$darknessScale(entity, factor, partialTicks));
//$$     }
//$$
//$$     @org.spongepowered.asm.mixin.injection.Redirect(method = "updateLightTexture", at = @At(value = "INVOKE",
//$$             target = "Lnet/minecraft/client/gui/components/BossHealthOverlay;shouldCreateWorldFog()Z"))
//$$     private boolean spaceclient$bossFog(net.minecraft.client.gui.components.BossHealthOverlay overlay) {
//$$         return FullbrightModule.bossFog(overlay.shouldCreateWorldFog());
//$$     }
//$$
//$$     /** The gamma option's value; the third option read in that method. */
//$$     @org.spongepowered.asm.mixin.injection.Redirect(method = "updateLightTexture", at = @At(value = "INVOKE",
//$$             target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 2))
//$$     private Object spaceclient$gamma(net.minecraft.client.OptionInstance<?> option) {
//$$         return FullbrightModule.gamma(option.get());
//$$     }
//$$ }
//#endif
