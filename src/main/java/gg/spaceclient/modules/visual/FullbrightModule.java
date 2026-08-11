package gg.spaceclient.modules.visual;

import gg.spaceclient.module.Category;
import gg.spaceclient.module.Module;

/**
 * Raises the gamma option while enabled and puts it back on disable, so the
 * player's own brightness setting is never lost.
 */
public class FullbrightModule extends Module {
    private double previousGamma = -1;

    public FullbrightModule() {
        super("fullbright", "Fullbright", "Removes darkness and improves visibility", Category.VISUAL);
    }

    @Override
    protected void onEnable() {
        if (mc.options == null) return;
        previousGamma = mc.options.getGamma().getValue();
    }

    @Override
    public void onTick() {
        if (mc.options == null) return;
        // Re-applied each tick because other code can reset it
        if (mc.options.getGamma().getValue() < 10.0) {
            mc.options.getGamma().setValue(10.0);
        }
    }

    @Override
    protected void onDisable() {
        if (mc.options == null || previousGamma < 0) return;
        mc.options.getGamma().setValue(previousGamma);
        previousGamma = -1;
    }
}
