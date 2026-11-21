package gg.spaceclient.modules;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.config.DropLock;
import gg.spaceclient.input.KeyBinds;
import gg.spaceclient.module.Module;
import gg.spaceclient.setting.ActionSetting;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.KeySetting;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Items you cannot drop by accident.
 *
 * Pick items in the grid (DropLockScreen) and, while this is on, they stay
 * in your inventory: Q and Ctrl+Q in hand, Q over a slot, and throwing a
 * held stack out of an inventory window do nothing for them. Everything else
 * drops as usual. The toggle key switches the protection on and off, for
 * the moment you do want to hand your totem to a friend.
 *
 * Only your own dropping is stopped. Dying still drops everything, as the
 * server decides that.
 */
public class DropLockModule extends Module {

    private final KeySetting key = new KeySetting(
            "key", "Toggle key", "Press the chip, then your key - switches the lock on and off",
            () -> KeyBinds.codeOf(SpaceClient.getDropLockKey()),
            code -> KeyBinds.bind(SpaceClient.getDropLockKey(), code));

    private final ActionSetting items = new ActionSetting(
            "items", "Choose items", "Every item in the game - click the ones you never want to drop",
            () -> gg.spaceclient.compat.Screens.set(new gg.spaceclient.ui.DropLockScreen(
                    gg.spaceclient.compat.Screens.current())));

    private final BooleanSetting notify = new BooleanSetting(
            "notify", "Say when blocked", "A line above the hotbar when a drop is stopped", true);

    private static DropLockModule instance;

    public DropLockModule() {
        super("droplock", "Drop Lock", "Items you choose can't be dropped by accident", false);
        addSettings(items, key, notify);
        instance = this;
    }

    /** The toggle key: on and off, said above the hotbar. */
    public void togglePressed() {
        toggle();
        SpaceClient.getConfigManager().save();
        say(Component.literal("Drop Lock " + (isEnabled() ? "on" : "off")
                + (isEnabled() && DropLock.count() == 0 ? " - no items chosen yet" : "")));
    }

    /**
     * Whether dropping this stack is to be stopped; says so above the hotbar
     * when it is. Called from the drop mixins, so it never throws.
     */
    public static boolean blocks(ItemStack stack) {
        try {
            DropLockModule module = instance;
            if (module == null || !module.isEnabled() || !DropLock.isLocked(stack)) return false;
            if (module.notify.get()) {
                module.say(Component.literal(stack.getHoverName().getString() + " is drop-locked"));
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private long lastSaid = 0;

    private void say(Component text) {
        long now = System.currentTimeMillis();
        if (now - lastSaid < 400) return;
        lastSaid = now;
        try {
            if (mc.player == null) return;
            //#if MC >= 26.1
            mc.player.sendOverlayMessage(text);
            //#else
            //$$ mc.player.displayClientMessage(text, true);
            //#endif
        } catch (Throwable ignored) {
            // A message is not worth a crash
        }
    }
}
