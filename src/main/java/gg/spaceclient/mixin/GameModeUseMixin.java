package gg.spaceclient.mixin;

import gg.spaceclient.modules.LitematicaModule;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tells the Litematica helper about every use of a block, before the game
 * handles it - the way to learn where a block is being placed, by hand or by
 * Easy Place, which makes this same call.
 */
@Mixin(MultiPlayerGameMode.class)
public class GameModeUseMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"), require = 0)
    private void spaceclient$onUseBlock(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                        CallbackInfoReturnable<InteractionResult> cir) {
        try {
            LitematicaModule.onUseBlock(hit);
        } catch (Throwable ignored) {
            // Never in the way of a click
        }
    }
}
