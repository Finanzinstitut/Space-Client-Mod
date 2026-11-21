package gg.spaceclient.mixin;

import gg.spaceclient.modules.DropLockModule;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drop Lock in inventory windows: Q over a locked item's slot, and a locked
 * stack carried on the cursor and clicked outside the window, do nothing.
 * The creative inventory has its own slotClicked, so it is hooked too.
 *
 * The click type was renamed in 26.1 (ClickType became ContainerInput).
 */
@Mixin({AbstractContainerScreen.class, CreativeModeInventoryScreen.class})
public class DropLockSlotMixin {

    //#if MC >= 26.1
    @Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V",
            at = @At("HEAD"), cancellable = true)
    private void spaceclient$dropLock(Slot slot, int slotId, int button,
                                      net.minecraft.world.inventory.ContainerInput type, CallbackInfo ci) {
        boolean throwing = type == net.minecraft.world.inventory.ContainerInput.THROW;
    //#else
    //$$ @Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ClickType;)V",
    //$$         at = @At("HEAD"), cancellable = true)
    //$$ private void spaceclient$dropLock(Slot slot, int slotId, int button,
    //$$                                   net.minecraft.world.inventory.ClickType type, CallbackInfo ci) {
    //$$     boolean throwing = type == net.minecraft.world.inventory.ClickType.THROW;
    //#endif
        try {
            var menu = ((AbstractContainerScreen<?>) (Object) this).getMenu();
            if (slot != null) {
                // Q over a slot throws what is in it
                if (throwing && DropLockModule.blocks(slot.getItem())) ci.cancel();
            } else if (slotId == -999) {
                // A click outside the window throws what the cursor holds
                if (DropLockModule.blocks(menu.getCarried())) ci.cancel();
            }
        } catch (Throwable ignored) {
            // A click is never worth a crash
        }
    }
}
