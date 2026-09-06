package gg.spaceclient.modules;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.module.Module;
import gg.spaceclient.setting.IntSetting;
import gg.spaceclient.util.Reflect;

import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;

/**
 * Shifts how strong the world's colours are, from grey to oversaturated.
 *
 * This is not a HUD element and draws nothing of its own. It is a post
 * processing pass: after the world has been rendered, every pixel is pulled
 * towards or away from its own brightness. The weights are Mojang's own
 * (0.3, 0.59, 0.11) rather than the technically correct Rec.709 ones, so a
 * fully desaturated world looks the same as the game's built-in greyscale
 * effects rather than subtly different.
 *
 * <h2>Why fixed steps rather than a free slider</h2>
 *
 * The strength lives in the effect's JSON as a uniform, and that JSON is read
 * once when the chain is loaded. Changing it afterwards would mean writing into
 * the uniform buffer directly - which this version does not allow: PostPass
 * creates those buffers with USAGE_UNIFORM only, no USAGE_MAP_WRITE, so mapping
 * one for writing fails at runtime. Confirmed from the bytecode rather than
 * guessed, and it would have failed in game rather than at build time.
 *
 * So the mod ships one small JSON per step and picks between them. The slider
 * still reads 0 to 200 and simply snaps, which is invisible in use - the eye
 * cannot tell 62% saturation from 75% anyway.
 *
 * <h2>Why reflection rather than a mixin</h2>
 *
 * Applying an effect means GameRenderer.setPostEffect, which is private, and
 * reading the current one means currentPostEffect. Following this mod's rule -
 * anything proven by a compile is called directly, anything guessed goes
 * through Reflect - both go through reflection, because neither has been
 * compiled against. A wrong name then costs a log line instead of a build.
 *
 * Going through the game's own field also means the frame graph, the render
 * targets and the timing stay vanilla's problem rather than ours.
 */
public class SaturationModule extends Module {

    /**
     * The strengths there is a JSON for. 100 is missing on purpose: it is the
     * neutral value, and the cheapest way to render "no change" is to render
     * nothing at all rather than to run two passes that do nothing.
     */
    private static final int[] STEPS = { 0, 25, 50, 75, 100, 125, 150, 200 };

    private final IntSetting level = new IntSetting(
            "level", "Saturation (percent)",
            "100 leaves colours alone, 0 is greyscale, above 100 pushes them", 100, 0, 200);

    /** The effect this module put in place, so it only ever clears its own. */
    private Identifier applied = null;

    private boolean warned = false;

    public SaturationModule() {
        super("saturation", "Saturation", "Shifts how strong the world's colours are", false);
        addSettings(level);
    }

    /** The nearest strength there is a file for. */
    private static int snap(int value) {
        int best = STEPS[0];
        int bestDistance = Integer.MAX_VALUE;
        for (int step : STEPS) {
            int distance = Math.abs(step - value);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = step;
            }
        }
        return best;
    }

    private static Identifier idFor(int step) {
        return Identifier.fromNamespaceAndPath(SpaceClient.MOD_ID, "saturation_" + step);
    }

    /**
     * Whether an effect is one of ours.
     *
     * Compares against the identifiers this module can produce rather than
     * reading the namespace off the object, because the accessor's name under
     * this version's mappings is not something a compile here has proven.
     * Seven equals calls a tick is nothing next to a wrong guess.
     */
    private static boolean isOurs(Identifier id) {
        if (id == null) return false;
        for (int step : STEPS) {
            if (step != 100 && idFor(step).equals(id)) return true;
        }
        return false;
    }

    @Override
    public void onTick() {
        Object renderer = gameRenderer();
        if (renderer == null) return;

        Identifier current = currentEffect(renderer);

        // Something else is already showing an effect - spectating a creeper,
        // most likely. Vanilla allows exactly one at a time, so the polite
        // thing is to stand down rather than to fight over it every tick.
        if (current != null && !isOurs(current)) {
            applied = null;
            return;
        }

        int step = snap(level.get());
        if (step == 100) {
            clear(renderer);
            return;
        }

        Identifier wanted = idFor(step);
        if (wanted.equals(current)) {
            applied = wanted;
            return;
        }

        // Reloading a resource pack throws the chain away without telling us,
        // which is why this compares against the game's own field every tick
        // rather than trusting what was set last.
        setEffect(renderer, wanted);
    }

    @Override
    protected void onDisable() {
        Object renderer = gameRenderer();
        if (renderer != null) clear(renderer);
    }

    /** Takes our effect down, leaving anyone else's alone. */
    private void clear(Object renderer) {
        Identifier current = currentEffect(renderer);
        if (!isOurs(current)) {
            applied = null;
            return;
        }
        Reflect.call(renderer, "clearPostEffect");
        applied = null;
    }

    private void setEffect(Object renderer, Identifier id) {
        Object result = Reflect.callWith(renderer, "setPostEffect", id);
        applied = id;

        // callWith returns null for a void method as well as for a miss, so a
        // failure shows up as the effect simply never appearing. Checking the
        // field back is what tells the two apart.
        if (result == null && currentEffect(renderer) == null) {
            warnOnce();
        }
    }

    private Identifier currentEffect(Object renderer) {
        Object value = Reflect.call(renderer, "currentPostEffect");
        return value instanceof Identifier id ? id : null;
    }

    private void warnOnce() {
        if (warned) return;
        warned = true;
        SpaceClient.LOGGER.warn("Saturation could not apply a post effect on this version");
    }

    // --- reaching the renderer ---

    private static Field rendererField = null;
    private static boolean rendererLookedUp = false;

    /**
     * The game's renderer, read by reflection for the same reason the calls
     * above are: the field's name under this version's mappings has not been
     * proven by a compile.
     */
    private Object gameRenderer() {
        if (mc == null) return null;
        if (!rendererLookedUp) {
            rendererLookedUp = true;
            Class<?> current = mc.getClass();
            while (current != null && rendererField == null) {
                try {
                    Field field = current.getDeclaredField("gameRenderer");
                    field.setAccessible(true);
                    rendererField = field;
                } catch (NoSuchFieldException ignored) {
                    current = current.getSuperclass();
                } catch (Throwable ignored) {
                    break;
                }
            }
        }
        if (rendererField == null) return null;
        try {
            return rendererField.get(mc);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
