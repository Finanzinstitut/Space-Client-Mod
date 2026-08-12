package gg.spaceclient.modules;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.module.Module;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.IntSetting;
import net.minecraft.client.KeyMapping;

import java.lang.reflect.Method;

/**
 * Hold the zoom key to narrow the field of view.
 *
 * The field of view is changed through the game's own option rather than by
 * hooking the renderer, which keeps this free of mixins. The option object's
 * getter and setter are reached by reflection because their names have moved
 * between versions - a mismatch logs a warning instead of failing the build.
 */
public class ZoomModule extends Module {
    private final IntSetting factor = new IntSetting(
            "factor", "Zoom factor", "How far the zoom goes", 4, 2, 12);

    private final BooleanSetting smooth = new BooleanSetting(
            "smooth", "Smooth", "Ease in and out instead of snapping", true);

    private final BooleanSetting slowSensitivity = new BooleanSetting(
            "slow_sensitivity", "Reduce sensitivity", "Aim slower while zoomed", true);

    private double normalFov = -1;
    private double normalSensitivity = -1;
    private double current = 1.0;
    private boolean warned = false;

    public ZoomModule() {
        super("zoom", "Zoom", "Hold the zoom key to look further", false);
        addSettings(factor, smooth, slowSensitivity);
    }

    private boolean keyDown() {
        KeyMapping key = SpaceClient.getZoomKey();
        return key != null && key.isDown();
    }

    @Override
    public void onTick() {
        if (mc.options == null) return;

        double target = keyDown() ? factor.get() : 1.0;
        if (smooth.get()) {
            current += (target - current) * 0.35;
            if (Math.abs(current - target) < 0.01) current = target;
        } else {
            current = target;
        }

        Object fovOption = mc.options.fov();
        Object sensitivityOption = mc.options.sensitivity();

        if (normalFov < 0) {
            Double value = readOption(fovOption);
            if (value == null) {
                warnOnce();
                return;
            }
            normalFov = value;
        }

        if (current <= 1.001) {
            // Back to normal, and stop holding the option hostage
            writeOption(fovOption, normalFov);
            if (normalSensitivity >= 0) {
                writeOption(sensitivityOption, normalSensitivity);
                normalSensitivity = -1;
            }
            normalFov = -1;
            return;
        }

        writeOption(fovOption, normalFov / current);

        if (slowSensitivity.get()) {
            if (normalSensitivity < 0) {
                Double value = readOption(sensitivityOption);
                if (value != null) normalSensitivity = value;
            }
            if (normalSensitivity >= 0) {
                writeOption(sensitivityOption, normalSensitivity / current);
            }
        }
    }

    @Override
    protected void onDisable() {
        if (mc.options == null) return;
        if (normalFov >= 0) {
            writeOption(mc.options.fov(), normalFov);
            normalFov = -1;
        }
        if (normalSensitivity >= 0) {
            writeOption(mc.options.sensitivity(), normalSensitivity);
            normalSensitivity = -1;
        }
        current = 1.0;
    }

    private void warnOnce() {
        if (warned) return;
        warned = true;
        SpaceClient.LOGGER.warn("Zoom could not read the field of view option on this version");
    }

    /** Options expose their value under a name that has changed over time. */
    private Double readOption(Object option) {
        if (option == null) return null;
        for (String name : new String[]{"get", "getValue", "value"}) {
            try {
                Method method = option.getClass().getMethod(name);
                Object value = method.invoke(option);
                if (value instanceof Number number) return number.doubleValue();
            } catch (Exception ignored) {
                // Try the next name
            }
        }
        return null;
    }

    private void writeOption(Object option, double value) {
        if (option == null) return;
        for (String name : new String[]{"set", "setValue"}) {
            for (Class<?> type : new Class<?>[]{Object.class, Double.class, Integer.class}) {
                try {
                    Method method = option.getClass().getMethod(name, type);
                    Object argument = type == Integer.class
                            ? Integer.valueOf((int) Math.round(value))
                            : Double.valueOf(value);
                    method.invoke(option, argument);
                    return;
                } catch (Exception ignored) {
                    // Try the next shape
                }
            }
        }
    }
}
