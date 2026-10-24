package gg.spaceclient.mixin;

import gg.spaceclient.skin.SkinChanger;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The skin just put on in the game, shown on the player at once.
 *
 * The game keeps the skin it was handed on joining until it joins again. Only
 * the player's own figure is changed, and only the body and model - cape and
 * elytra stay as the server knows them.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerSkinMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void spaceclient$newSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        SkinChanger.Shown shown = SkinChanger.shown();
        if (shown == null) return;
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        if (!shown.player().equals(self.getUUID())) return;
        PlayerSkin skin = cir.getReturnValue();
        cir.setReturnValue(new PlayerSkin(
                new ClientAsset.ResourceTexture(shown.texture(), shown.texture()),
                skin.cape(), skin.elytra(),
                shown.slim() ? PlayerModelType.SLIM : PlayerModelType.WIDE,
                skin.secure()));
    }
}
