package gg.spaceclient.modules;

import gg.spaceclient.module.Module;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.IntSetting;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gives the cape motion of its own instead of a flat, dead slab.
 *
 * Vanilla decides the cape angle from one thing: how fast you moved since last
 * frame. Stand still and it hangs perfectly rigid, which is what makes it read
 * as cardboard.
 *
 * <h2>Why the first attempt still looked wrong</h2>
 *
 * The first version multiplied vanilla's angle and added a sine wave on top of
 * the result. Both halves were the problem. Multiplying an instantaneous
 * response keeps it instantaneous - the cape still snapped to its new angle the
 * moment you moved, just further, so it read as stiffer rather than looser. And
 * a sine driven by the clock is motion with no cause: the cape fanned while you
 * stood still in a closed room, which is the one thing cloth never does.
 *
 * <h2>What actually reads as cloth</h2>
 *
 * Lag and overshoot. Real cloth arrives late and goes slightly too far before
 * settling, because it has mass. So vanilla's angle is treated as where the
 * cape is being pulled towards, and the drawn angle chases it as a damped
 * spring - it trails when you start moving, swings past when you stop, and
 * settles. The idle drift is still there but it now moves the target rather
 * than the output, so the spring smooths it into a stir instead of a wobble.
 *
 * <h2>What this deliberately is not</h2>
 *
 * Not a per-segment rope simulation like tr7zw's mod. That needs the cape drawn
 * as a chain of pieces, which means replacing the whole cape render path. This
 * steers the three angles vanilla already has, so it inherits every other
 * behaviour - elytra priority, sneaking, first person - for free and cannot
 * break rendering for anyone. The ceiling is lower: a cape that swings as one
 * flat piece will never ripple along its length, however well it is damped.
 */
public class WaveyCapeModule extends Module {

    private final IntSetting strength = new IntSetting(
            "strength", "Sway strength",
            "How far the cape swings when you move", 100, 20, 300);

    /**
     * How much the cape lags behind and swings past.
     *
     * The setting that does the work. At zero it tracks vanilla exactly and
     * looks like vanilla; at the top it is heavy velvet that takes its time.
     */
    private final IntSetting weight = new IntSetting(
            "weight", "Cloth weight",
            "How much the cape trails behind you and swings past", 55, 0, 100);

    private final IntSetting idleSway = new IntSetting(
            "idle_sway", "Idle drift",
            "How much it stirs while standing still", 25, 0, 100);

    private final IntSetting speed = new IntSetting(
            "speed", "Drift speed",
            "How quickly the idle motion cycles", 50, 10, 150);

    private final BooleanSetting sideways = new BooleanSetting(
            "sideways", "Sideways lean",
            "Let the cape swing left and right as well", true);

    public WaveyCapeModule() {
        super("waveycape", "Wavey Cape",
                "Makes capes move instead of hanging stiff", false);
        addSettings(strength, weight, idleSway, speed, sideways);
    }

    /** Where one cape currently is, and how fast it is getting somewhere else. */
    private static final class Cloth {
        float flap, lean, lean2;
        float flapRate, leanRate, lean2Rate;
        long last = 0L;
        boolean primed = false;
    }

    /**
     * One spring per player, because they are in different places doing
     * different things - sharing a single state would have everyone's cape
     * answer to whoever happened to be drawn last. Keyed on object identity and
     * capped, so a server full of people who have since walked out of range
     * cannot grow this without limit.
     */
    private final Map<Integer, Cloth> cloths = new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Cloth> eldest) {
            return size() > 64;
        }
    };

    /**
     * The angles to draw this frame, in the order vanilla stores them.
     *
     * Returns null when off, so the mixin can leave the state untouched rather
     * than write back values that happen to match - an untouched state is one
     * fewer thing to explain when something else looks wrong.
     *
     * @param key identifies whose cape this is; any value stable per player does
     */
    public float[] shape(int key, float capeFlap, float capeLean, float capeLean2) {
        if (!isEnabled()) return null;

        Cloth cloth = cloths.computeIfAbsent(key, id -> new Cloth());

        long now = System.nanoTime();
        float dt = cloth.last == 0L ? 1f / 60f : (now - cloth.last) / 1_000_000_000f;
        cloth.last = now;
        if (dt <= 0f) return new float[]{cloth.flap, cloth.lean, cloth.lean2};
        // Back from a pause or a loading screen. Letting the real gap through
        // would fire the spring across a second of travel in one step.
        if (dt > 0.25f) dt = 0.25f;

        float boost = strength.get() / 100f;
        float drift = idleSway.get() / 100f;

        double t = (now % 100_000_000_000L) / 1_000_000_000.0 * (speed.get() / 50.0);
        // Two waves at different rates, because a single sine reads as a
        // machine rather than as cloth.
        float breathe = (float) (Math.sin(t * 1.7) * 2.5 + Math.sin(t * 0.9) * 1.5) * drift;
        float sway = (float) (Math.sin(t * 1.1) * 3.0) * drift;

        float targetFlap = capeFlap * boost + breathe;
        float targetLean = capeLean * boost;
        float targetLean2 = sideways.get() ? capeLean2 + sway : capeLean2;

        // First frame for this player: start where vanilla is, or the cape
        // swings in from zero the moment somebody walks into view.
        if (!cloth.primed) {
            cloth.primed = true;
            cloth.flap = targetFlap;
            cloth.lean = targetLean;
            cloth.lean2 = targetLean2;
            return new float[]{cloth.flap, cloth.lean, cloth.lean2};
        }

        float heavy = weight.get() / 100f;
        float pull = 140f - 122f * heavy;
        float drag = 24f - 18.5f * heavy;

        // Substepped so the integration never depends on the frame rate being
        // good. A single step at 10 fps with this much drag would diverge and
        // the cape would tear off into a spin.
        int steps = Math.min(8, Math.max(1, (int) Math.ceil(dt * 120f)));
        float step = dt / steps;
        for (int i = 0; i < steps; i++) {
            cloth.flapRate  += (pull * (targetFlap  - cloth.flap)  - drag * cloth.flapRate)  * step;
            cloth.leanRate  += (pull * (targetLean  - cloth.lean)  - drag * cloth.leanRate)  * step;
            cloth.lean2Rate += (pull * (targetLean2 - cloth.lean2) - drag * cloth.lean2Rate) * step;
            cloth.flap  += cloth.flapRate  * step;
            cloth.lean  += cloth.leanRate  * step;
            cloth.lean2 += cloth.lean2Rate * step;
        }

        // Overshoot is the point, a cape through the player's chest is not.
        // Capped as a distance from where vanilla wanted it rather than as an
        // absolute angle, because what these three mean in world space is not
        // something this mod has ever verified.
        cloth.flap = hold(cloth.flap, targetFlap);
        cloth.lean = hold(cloth.lean, targetLean);
        cloth.lean2 = hold(cloth.lean2, targetLean2);

        return new float[]{cloth.flap, cloth.lean, cloth.lean2};
    }

    private static final float MAX_TRAIL = 25f;

    private static float hold(float value, float target) {
        if (value > target + MAX_TRAIL) return target + MAX_TRAIL;
        if (value < target - MAX_TRAIL) return target - MAX_TRAIL;
        return value;
    }
}
