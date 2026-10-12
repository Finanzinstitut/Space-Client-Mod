package gg.spaceclient.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What each HUD element costs per frame, measured where it is drawn.
 *
 * Reported on the diagnostics page. "The HUD costs frames" has been true for a
 * while, and which of the elements it is has been a guess - this makes it a
 * number. Each element's draw is timed with nanoTime, and the figure is the
 * average over roughly the last few hundred frames, in microseconds, so one
 * hitch does not stand in for the element.
 */
public final class HudCost {

    /** [0] nanoseconds summed, [1] frames counted. Render thread writes only. */
    private static final Map<String, long[]> TOTALS = new ConcurrentHashMap<>();

    /** Halved past this, so old frames fade out instead of lingering forever. */
    private static final long WINDOW = 600;

    private HudCost() {}

    public static void record(String id, long nanos) {
        long[] total = TOTALS.computeIfAbsent(id, key -> new long[2]);
        total[0] += nanos;
        total[1]++;
        if (total[1] >= WINDOW) {
            total[0] /= 2;
            total[1] /= 2;
        }
    }

    /** Average microseconds per frame for one element, or 0 if never drawn. */
    public static double micros(String id) {
        long[] total = TOTALS.get(id);
        if (total == null || total[1] == 0) return 0;
        return total[0] / (double) total[1] / 1000.0;
    }

    /** The costliest elements, worst first, as one line for the diagnostics page. */
    public static String report() {
        if (TOTALS.isEmpty()) return "no HUD element drawn yet";

        List<Map.Entry<String, long[]>> all = new ArrayList<>(TOTALS.entrySet());
        all.sort((a, b) -> Double.compare(micros(b.getKey()), micros(a.getKey())));

        double sum = 0;
        for (var entry : all) sum += micros(entry.getKey());

        StringBuilder out = new StringBuilder();
        out.append(String.format("all %.0f us/frame", sum));
        int shown = 0;
        for (var entry : all) {
            double us = micros(entry.getKey());
            if (us < 5 || shown >= 6) break;
            out.append(shown == 0 ? " - " : ", ")
                    .append(entry.getKey()).append(' ')
                    .append(String.format("%.0f", us));
            shown++;
        }
        return out.toString();
    }

    /** Whether the HUD as a whole stays under a millisecond a frame. */
    public static boolean cheap() {
        double sum = 0;
        for (String id : TOTALS.keySet()) sum += micros(id);
        return sum < 1000;
    }
}
