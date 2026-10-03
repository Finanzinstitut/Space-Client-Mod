package gg.spaceclient.mixin;

import gg.spaceclient.render.TotemActivation;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Catches the totem pop before the game starts drawing it.
 *
 * This is the method the world calls when a totem saves somebody, and it is
 * public and plainly named, which is why the hook goes here rather than at the
 * field it writes. Cancelling it stops the game's animation at the source, so
 * exactly one totem is ever on screen.
 *
 * Refusing to cancel when the module is off is the whole of the module's
 * switch: with it off nothing is claimed and the game animates as it always
 * did. If this injection never applies, TotemActivation falls back to the
 * field, and the diagnostics page says which of the two is carrying it.
 */
@Mixin(LocalPlayer.class)
// 26.3 moved the item activation (the totem pop) from GameRenderer onto the
// local player, which now owns an ItemActivation; displayItemActivation kept
// its name and signature there.
public class TotemPopMixin {

    @Inject(method = "displayItemActivation(Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"), cancellable = true)
    private void spaceclient$pop(ItemStack stack, CallbackInfo ci) {
        if (TotemActivation.offer(stack)) ci.cancel();
    }
}
