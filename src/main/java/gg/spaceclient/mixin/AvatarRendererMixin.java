package gg.spaceclient.mixin;

import gg.spaceclient.cosmetics.CosmeticsManager;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
            Identifier wings = CosmeticsManager.wingsFor(id);
            if (cape == null && wings == null) return;

            PlayerSkin skin = state.skin;
            if (skin == null) return;

            // The vanilla record rather than a hand rolled Texture: it is what
            // every other cape in the game is, equals and all, and its single
            // argument form expands the id into textures/<path>.png for us.
            ClientAsset.Texture capeTex = cape == null
                    ? skin.cape()
                    : new ClientAsset.ResourceTexture(cape);

            // A cape file carries wing art in its right hand region, which is
            // why vanilla capes become matching elytra. So a cape supplies both
            // - unless separate wings are worn, and those win the elytra slot.
            ClientAsset.Texture elytraTex = wings != null
                    ? new ClientAsset.ResourceTexture(wings)
                    : capeTex;

            state.skin = new PlayerSkin(
                    skin.body(), capeTex, elytraTex, skin.model(), skin.secure());

            // The flag mirrors the player's own cape toggle, which says nothing
            // about a cape they got from the shop
            if (cape != null) state.showCape = true;

            // The elytra slot only draws when the game believes an elytra is
            // worn, so shop wings on an empty chest were invisible - which is
            // exactly what happened. Handing the render state an elytra makes
            // the layer draw, and the state is a per frame copy, so nothing
            // about the actual inventory is touched.
            if (wings != null && state.chestEquipment != null
                    && state.chestEquipment.isEmpty()) {
                state.chestEquipment = new ItemStack(Items.ELYTRA);
            }

        } catch (Throwable ignored) {
            // A cosmetic must never be able to stop a player from rendering
        }

        try {
            spaceclient$wave(state);
        } catch (Throwable ignored) {
            // Motion is a nicety; rendering the player is not
        }
    }

    /**
     * Applies the Wavey Cape module's angles, for every player rather than
     * only the one wearing a shop cape.
     *
     * Vanilla capes deserve the motion too - the module is about how capes
     * behave, not about what was bought.
     */
    private static void spaceclient$wave(AvatarRenderState state) {
        var manager = gg.spaceclient.SpaceClient.getModuleManager();
        if (manager == null) return;
        var module = manager.get("waveycape");
        if (!(module instanceof gg.spaceclient.modules.WaveyCapeModule wavey)) return;

        float[] shaped = wavey.shape(state.capeFlap, state.capeLean, state.capeLean2);
        if (shaped == null) return;

        state.capeFlap = shaped[0];
        state.capeLean = shaped[1];
        state.capeLean2 = shaped[2];
    }
}
