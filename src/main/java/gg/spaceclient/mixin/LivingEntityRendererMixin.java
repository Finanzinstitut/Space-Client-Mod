package gg.spaceclient.mixin;

import gg.spaceclient.access.TintHolder;
import gg.spaceclient.render.HitTint;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Colours players and mobs themselves - skin, fur, armour stand - instead of a
 * box around them.
 *
 * The game already has a way to shade a whole model: the red flash when
 * something is hurt. It is a row in a small overlay texture that every entity
 * model samples, picked by getOverlayCoords. Two rows of that texture are never
 * used by the game; HitTint paints the chosen colours into them, and this
 * points an entity at one of those rows instead of the red one. Everything
 * that follows the model - layers, the second skin layer, sheep wool - picks
 * it up for free, because they all ask the same method.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;"
            + "Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void spaceclient$decideTint(LivingEntity entity, LivingEntityRenderState state,
                                        float partialTicks, CallbackInfo ci) {
        int tint = TintHolder.NONE;
        try {
            // hasRedOverlay is the game's own "was just hurt", already set by
            // the time extraction reaches its end
            tint = HitTint.decide(entity, state.hasRedOverlay);
        } catch (Throwable ignored) {
            // The game's own red flash, as before
        }
        ((TintHolder) (Object) state).spaceclient$setTint(tint);
    }

    @Inject(method = "getOverlayCoords(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)I",
            at = @At("RETURN"), cancellable = true)
    private static void spaceclient$tintOverlay(LivingEntityRenderState state, float whiteOverlay,
                                                CallbackInfoReturnable<Integer> cir) {
        try {
            int tint = ((TintHolder) (Object) state).spaceclient$tint();
            if (tint == TintHolder.NONE) return;
            cir.setReturnValue(HitTint.overlayFor(tint, cir.getReturnValue()));
        } catch (Throwable ignored) {
            // Leave the game's choice alone
        }
    }
}
