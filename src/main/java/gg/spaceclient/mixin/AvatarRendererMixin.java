package gg.spaceclient.mixin;

import gg.spaceclient.wavey.PlayerWrapper;
import gg.spaceclient.wavey.render.CustomCapeRenderLayer;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the WaveyCapes cape layer to the player renderer.
 *
 * As WaveyCapes' own PlayerRendererMixin does (tr7zw, tr7zw Protective
 * License). The vanilla cape layer stays in place and steps aside while the
 * module is on (see CapeLayerMixin), so switching the module off brings the
 * ordinary cape straight back without a resource reload.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin
        extends LivingEntityRenderer<AbstractClientPlayer, AvatarRenderState, PlayerModel> {

    public AvatarRendererMixin(EntityRendererProvider.Context context, PlayerModel model, float shadow) {
        super(context, model, shadow);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Z)V",
            at = @At("RETURN"))
    private void spaceclient$addWaveyCape(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
        // The same equipment lookup vanilla's cape layer uses, for the elytra
        // and chestplate checks
        PlayerWrapper.equipmentAssets = context.getEquipmentAssets();
        addLayer(new CustomCapeRenderLayer(this));
        gg.spaceclient.render.CapeReport.layerAdded();
    }
}
