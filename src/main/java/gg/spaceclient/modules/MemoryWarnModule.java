package gg.spaceclient.modules;

import gg.spaceclient.module.Module;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.IntSetting;
import gg.spaceclient.util.Screens;

/**
 * Says something before the heap runs out, not after.
 *
 * The Memory readout already turns amber and then red as the heap fills, which
 * helps exactly as much as you happen to be looking at it. The failure it is
 * warning about does not announce itself: the collector starts running more and
 * more often, each pass freeing less, and what you notice is the game stuttering
 * for reasons that look like anything else - a busy server, a shader, a chunk
 * boundary. By the time it is unmistakable there are seconds left.
 *
 * Those seconds are the point. Walking somewhere safe and leaving cleanly is a
 * different outcome from dying in a hole because the client locked up.
 *
 * <h2>Why a sustained reading rather than a peak</h2>
 *
 * A heap touching its limit for an instant is ordinary - it is what a collection
 * looks like from the outside, and warning on it would fire constantly and teach
 * you to ignore the warning. What matters is staying high: memory that is full
 * and stays full after collections have had their chance is memory that is not
 * coming back.
 */
public class MemoryWarnModule extends Module {

    private final IntSetting threshold = new IntSetting(
            "threshold", "Warn above",
            "Percent of the heap in use before warning", 88, 60, 99);

    private final IntSetting sustain = new IntSetting(
            "sustain", "Held for",
            "Seconds it has to stay that high before saying anything", 8, 2, 60);

    private final IntSetting quiet = new IntSetting(
            "quiet", "Say it again after",
            "Minutes before the same warning may repeat", 5, 1, 60);

    private final BooleanSetting suggestGc = new BooleanSetting(
            "suggest_gc", "Offer the usual advice",
            "Add a line about render distance and restarting", true);

    public MemoryWarnModule() {
        super("memorywarn", "Memory warning",
                "Warns in chat before the game runs out of memory", true);
        addSettings(threshold, sustain, quiet, suggestGc);
    }

    /** Ticks spent above the threshold without a break. */
    private int heldTicks = 0;
    private long lastWarned = 0L;

    private static float ratio() {
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        if (max <= 0) return 0f;
        return (runtime.totalMemory() - runtime.freeMemory()) / (float) max;
    }

    @Override
    public void onTick() {
        float used = ratio();

        if (used * 100f < threshold.get()) {
            // One reading below the line ends it. Recovering is the normal case
            // and should not have to be earned back.
            heldTicks = 0;
            return;
        }

        heldTicks++;
        if (heldTicks < sustain.get() * 20) return;

        long now = System.currentTimeMillis();
        if (now - lastWarned < quiet.get() * 60_000L) return;
        lastWarned = now;
        heldTicks = 0;

        long usedMb = (Runtime.getRuntime().totalMemory()
                - Runtime.getRuntime().freeMemory()) / 1_048_576L;
        long maxMb = Runtime.getRuntime().maxMemory() / 1_048_576L;

        Screens.chat(String.format(
                "[Space Client] Memory is at %d%% (%d / %d MB) and staying there.",
                Math.round(used * 100f), usedMb, maxMb));

        if (suggestGc.get()) {
            Screens.chat("[Space Client] Lower the render distance, or leave and restart "
                    + "before it stutters. More RAM for this instance is set in the launcher.");
        }
    }

    /** What the warning currently sees, for the diagnostics screen. */
    public String status() {
        int percent = Math.round(ratio() * 100f);
        if (!isEnabled()) return "off (" + percent + "% in use)";
        if (heldTicks == 0) return percent + "% in use, below the line";
        return percent + "% in use, held " + (heldTicks / 20) + "s of " + sustain.get() + "s";
    }
}
