package gg.spaceclient.mixin;

import gg.spaceclient.access.TintHolder;
import gg.spaceclient.render.HitTint;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Colours end crystals the same way as living entities.
 *
 * A crystal is not a living entity and never flashes red, so the game draws it
 * with no overlay at all - the one argument this swaps for a coloured row.
 */
@Mixin(EndCrystalRenderer.class)
public abstract class EndCrystalRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;"
            + "Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;F)V",
            at = @At("TAIL"))
    private void spaceclient$decideTint(EndCrystal crystal, EndCrystalRenderState state,
                                        float partialTicks, CallbackInfo ci) {
        int tint = TintHolder.NONE;
        try {
            tint = HitTint.decide(crystal, false);
        } catch (Throwable ignored) {
            // Drawn as the game would
        }
        ((TintHolder) (Object) state).spaceclient$setTint(tint);
    }

    @ModifyArg(method = "submit(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
            + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel("
                            + "Lnet/minecraft/client/model/Model;Ljava/lang/Object;"
                            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/minecraft/resources/Identifier;III"
                            + "Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"),
            index = 5)
    private int spaceclient$tintOverlay(Model<?> model, Object state, PoseStack poseStack,
                                        Identifier texture, int light, int overlay, int outline,
                                        ModelFeatureRenderer.CrumblingOverlay crumbling) {
        try {
            if (!(state instanceof TintHolder holder)) return overlay;
            int tint = holder.spaceclient$tint();
            if (tint == TintHolder.NONE) return overlay;
            return HitTint.overlayFor(tint, overlay);
        } catch (Throwable ignored) {
            return overlay;
        }
    }
}
