package gg.spaceclient.modules.visual;

import gg.spaceclient.module.Category;
import gg.spaceclient.module.Module;
import gg.spaceclient.setting.BooleanSetting;

/**
 * Keeps sprint held down for you.
 *
 * The addition other clients skip: HUNGER_AWARE stops sprinting on its own once
 * your hunger drops to the point where sprinting would start draining the last
 * shanks, so you do not accidentally starve yourself while exploring.
 */
public class ToggleSprintModule extends Module {
    private final BooleanSetting sneakToo = new BooleanSetting(
            "toggle_sneak", "Toggle sneak", "Also hold sneak permanently", false);

    private final BooleanSetting hungerAware = new BooleanSetting(
            "hunger_aware", "Hunger aware", "Stop sprinting when hunger gets low", true);

    private final BooleanSetting pauseInMenus = new BooleanSetting(
            "pause_in_menus", "Pause in menus", "Release the keys while a screen is open", true);

    public ToggleSprintModule() {
        super("togglesprint", "Toggle Sprint", "Makes you sprint automatically", Category.UTILITY);
        addSettings(sneakToo, hungerAware, pauseInMenus);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.options == null) return;

        if (pauseInMenus.get() && mc.currentScreen != null) {
            mc.options.sprintKey.setPressed(false);
            return;
        }

        boolean allowed = true;
        if (hungerAware.get()) {
            // Below 7 shanks vanilla stops letting you sprint anyway; stopping
            // at 8 keeps a buffer so you never eat into the last one.
            allowed = mc.player.getHungerManager().getFoodLevel() > 8;
        }

        mc.options.sprintKey.setPressed(allowed);
        if (sneakToo.get()) {
            mc.options.sneakKey.setPressed(true);
        }
    }

    @Override
    protected void onDisable() {
        if (mc.options == null) return;
        mc.options.sprintKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
    }
}
