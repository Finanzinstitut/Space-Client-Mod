package gg.spaceclient.mixin;

import gg.spaceclient.cosmetics.CosmeticsManager;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.PlayerSkin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Puts a shop cape onto the render state, and lets vanilla draw it.
 *
 * The obvious place to intervene looks like CapeLayer, but it cannot work
 * there: AvatarRenderState carries the skin and the cape flags and no identity
 * at all - no uuid, no name - so at draw time there is no way to tell whose
 * cape is being drawn. Here the entity is still in hand, so the uuid is.
 *
 * Swapping the skin rather than drawing anything means the cape inherits every
 * bit of vanilla behaviour for free: the flapping while running, the lean while
 * sneaking, the elytra taking priority. A hand written cape renderer would have
 * to reproduce all of it and would drift out of step with the next change.
 */
@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void spaceclient$applyShopCape(Avatar avatar,
                                           AvatarRenderState state,
                                           float partialTick,
                                           CallbackInfo ci) {
        try {
            UUID id = avatar.getUUID();
            if (id == null) return;

            Identifier cape = CosmeticsManager.capeFor(id);
            if (cape == null) return;

            PlayerSkin skin = state.skin;
            if (skin == null) return;

            // The vanilla record rather than a hand rolled Texture: it is what
            // every other cape in the game is, equals and all, and its single
            // argument form expands the id into textures/<path>.png for us.
            ClientAsset.Texture texture = new ClientAsset.ResourceTexture(cape);

            state.skin = new PlayerSkin(
                    skin.body(), texture, skin.elytra(), skin.model(), skin.secure());

            // The flag mirrors the player's own cape toggle, which says nothing
            // about a cape they got from the shop
            state.showCape = true;

        } catch (Throwable ignored) {
            // A cosmetic must never be able to stop a player from rendering
        }
    }
}
