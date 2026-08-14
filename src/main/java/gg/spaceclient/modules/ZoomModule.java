package gg.spaceclient.modules;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.module.Module;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.IntSetting;
import gg.spaceclient.setting.ModeSetting;
import gg.spaceclient.input.RawKeyboard;

import java.util.Arrays;
import net.minecraft.client.KeyMapping;

import gg.spaceclient.util.Reflect;

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

    private final ModeSetting transition = new ModeSetting(
            "transition", "Transition", "How the zoom eases in and out",
            Arrays.asList("INSTANT", "LINEAR", "EASE_OUT", "EASE_IN_OUT", "EXPONENTIAL"),
            "EASE_OUT");

    private final IntSetting duration = new IntSetting(
            "duration", "Duration (tenths of a second)",
            "How long the zoom takes to reach full magnification", 3, 1, 20);

    private final BooleanSetting slowSensitivity = new BooleanSetting(
            "slow_sensitivity", "Reduce sensitivity", "Aim slower while zoomed", true);

    /**
     * The key is chosen here rather than only in the vanilla controls screen.
     * The registered binding still works and can be rebound there, but that
     * screen is easy to miss, and this reads the physical key directly so it
     * cannot collide with another control.
     */
    private final ModeSetting key = new ModeSetting(
            "key", "Zoom key", "Which key to hold",
            Arrays.asList("C", "X", "V", "B", "N", "Z", "G", "R", "F", "CTRL", "ALT", "BINDING"),
            "C");

    private double normalFov = -1;
    private double normalSensitivity = -1;
    private double current = 1.0;
    private boolean warned = false;

    /** 0 while not zooming, 1 at full magnification. */
    private double progress = 0;

    /** What the last write achieved, for the diagnostics page. */
    private static String lastResult = "not attempted";

    public static String lastResult() { return lastResult; }

    /** Set by the mixin the first time it runs, so the fallback can stand down. */
    private static volatile boolean MIXIN_ACTIVE = false;

    public static void markMixinActive() {
        if (!MIXIN_ACTIVE) {
            MIXIN_ACTIVE = true;
            lastResult = "renderer hook active";
        }
    }

    public static boolean isMixinActive() { return MIXIN_ACTIVE; }

    public ZoomModule() {
        super("zoom", "Zoom", "Hold the zoom key to look further", false);
        addSettings(key, factor, transition, duration, slowSensitivity);
    }

    private boolean keyDown() {
        // BINDING defers to whatever is set in the vanilla controls screen
        if (!key.is("BINDING") && RawKeyboard.isAvailable()) {
            int code = RawKeyboard.codeFor(key.get());
            if (code != org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN) {
                return RawKeyboard.isDown(code);
            }
        }

        KeyMapping binding = SpaceClient.getZoomKey();
        return binding != null && binding.isDown();
    }

    @Override
    public void onTick() {
        if (mc.options == null) return;

        // Progress moves between 0 and 1 over the configured duration; the
        // curve is applied to that rather than to the factor, so the timing
        // stays the same whatever magnification is chosen.
        double step = 1.0 / Math.max(1, duration.get() * 2);
        progress += keyDown() ? step : -step;
        progress = Math.max(0, Math.min(1, progress));

        double eased = ease(progress);
        current = 1.0 + (factor.get() - 1.0) * eased;

        // fov() has not been confirmed for this version, so it is looked up
        // With the mixin in place the renderer already divides the field of
        // view, so touching the option would zoom twice. It is only used when
        // the mixin could not attach.
        if (MIXIN_ACTIVE) {
            handleSensitivity();
            return;
        }

        Object fovOption = Reflect.call(mc.options, "fov", "getFov");
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

        double wanted = normalFov / current;
        writeOption(fovOption, wanted);

        // Read it back: a setter that throws inside is otherwise invisible, and
        // that is exactly how the zoom failed silently before.
        Double actual = readOption(fovOption);
        lastResult = actual == null
                ? "value not readable after writing"
                : Math.abs(actual - wanted) < 2
                        ? "working, fov " + actual.intValue()
                        : "write ignored, fov still " + actual.intValue();

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

    /** Sensitivity handling, shared by both paths. */
    private void handleSensitivity() {
        if (!slowSensitivity.get() || mc.options == null) return;
        Object sensitivityOption = mc.options.sensitivity();

        if (current > 1.05) {
            if (normalSensitivity < 0) {
                Double value = readOption(sensitivityOption);
                if (value != null) normalSensitivity = value;
            }
            if (normalSensitivity >= 0) {
                writeOption(sensitivityOption, normalSensitivity / current);
            }
        } else if (normalSensitivity >= 0) {
            writeOption(sensitivityOption, normalSensitivity);
            normalSensitivity = -1;
        }
    }

    @Override
    protected void onDisable() {
        if (mc.options == null) return;
        if (normalFov >= 0) {
            writeOption(Reflect.call(mc.options, "fov", "getFov"), normalFov);
            normalFov = -1;
        }
        if (normalSensitivity >= 0) {
            writeOption(mc.options.sensitivity(), normalSensitivity);
            normalSensitivity = -1;
        }
        current = 1.0;
    }

    /** The zoom factor the renderer should divide the field of view by. */
    public float currentFactor() {
        return (float) current;
    }

    /** Shapes the 0 to 1 progress into a curve. */
    private double ease(double t) {
        return switch (transition.get()) {
            case "INSTANT" -> t > 0 ? 1 : 0;
            case "LINEAR" -> t;
            case "EASE_IN_OUT" -> t < 0.5
                    ? 2 * t * t
                    : 1 - Math.pow(-2 * t + 2, 2) / 2;
            case "EXPONENTIAL" -> t == 0 ? 0 : Math.pow(2, 10 * t - 10);
            default -> 1 - Math.pow(1 - t, 3); // EASE_OUT
        };
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

    /**
     * Writes a value back into an option.
     *
     * The type matters: field of view is an Integer option, and the generic
     * setter erases to set(Object). Handing it a Double therefore compiles and
     * invokes fine, then throws a ClassCastException inside - which the old
     * version swallowed, so the zoom silently did nothing. The current value is
     * read first and the new one is boxed to match it.
     */
    private void writeOption(Object option, double value) {
        if (option == null) return;

        Object current = null;
        for (String getter : new String[]{"get", "getValue", "value"}) {
            try {
                current = option.getClass().getMethod(getter).invoke(option);
                if (current != null) break;
            } catch (Exception ignored) {
                // Try the next getter
            }
        }

        Object argument = current instanceof Integer
                ? Integer.valueOf((int) Math.round(value))
                : current instanceof Float
                        ? Float.valueOf((float) value)
                        : Double.valueOf(value);

        for (String name : new String[]{"set", "setValue"}) {
            for (Method method : option.getClass().getMethods()) {
                if (!method.getName().equals(name)) continue;
                if (method.getParameterCount() != 1) continue;
                try {
                    method.invoke(option, argument);

                    // The field of view option is clamped to the slider's range,
                    // roughly 30 to 110. A zoom wants to go well below that, and
                    // the setter silently clamps instead of refusing - which is
                    // why the sensitivity changed but the view barely did.
                    Double check = readOption(option);
                    if (check != null && Math.abs(check - value) > 0.5) {
                        writeFieldDirectly(option, argument);
                    }
                    return;
                } catch (Exception ignored) {
                    // Try the next overload
                }
            }
        }

        // No setter at all: go straight for the field
        writeFieldDirectly(option, argument);
    }

    /**
     * Writes the value into the option's own field, past whatever the setter
     * would clamp or validate. The renderer reads the same field, so this is
     * what makes a real zoom possible rather than a nudge to the slider's edge.
     */
    private void writeFieldDirectly(Object option, Object argument) {
        Class<?> current = option.getClass();
        while (current != null && current != Object.class) {
            for (java.lang.reflect.Field field : current.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                // The value field holds exactly the kind of box we just built
                if (!field.getType().isAssignableFrom(argument.getClass())
                        && field.getType() != Object.class) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object existing = field.get(option);
                    // Only overwrite something that already looks like the value
                    if (existing != null && existing.getClass() == argument.getClass()) {
                        field.set(option, argument);
                        return;
                    }
                } catch (Throwable ignored) {
                    // Try the next field
                }
            }
            current = current.getSuperclass();
        }
    }
}
