package gg.spaceclient.mixin;

import gg.spaceclient.modules.DropLockModule;

import net.minecraft.client.player.LocalPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Drop Lock: Q and Ctrl+Q on the item in hand. Stopped before the game
 * removes the item or tells the server, so nothing is out of step.
 *
 * Where the drop starts moved in 26.3, from LocalPlayer.drop to
 * MultiPlayerGameMode.dropItem.
 */
//#if MC >= 26.3
@Mixin(net.minecraft.client.multiplayer.MultiPlayerGameMode.class)
//#else
//$$ @Mixin(LocalPlayer.class)
//#endif
public class DropLockDropMixin {

    //#if MC >= 26.3
    @Inject(method = "dropItem", at = @At("HEAD"), cancellable = true)
    private void spaceclient$dropLock(LocalPlayer player, boolean entireStack, CallbackInfo ci) {
        if (player != null && DropLockModule.blocks(player.getMainHandItem())) ci.cancel();
    }
    //#else
    //$$ @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    //$$ private void spaceclient$dropLock(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
    //$$     LocalPlayer player = (LocalPlayer) (Object) this;
    //$$     if (DropLockModule.blocks(player.getMainHandItem())) cir.setReturnValue(false);
    //$$ }
    //#endif
}
