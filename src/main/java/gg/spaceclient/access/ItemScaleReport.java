package gg.spaceclient.access;

/**
 * Records whether each item scaling hook is actually running.
 *
 * The mixin config sets `defaultRequire: 0`, which is deliberate - a target
 * that has moved should skip its injection rather than crash the game on
 * launch. The cost is that a hook which never applies also never complains, and
 * the symptom is a setting that silently does nothing.
 *
 * Each hook reports the first time it fires. The diagnostics page then answers
 * the question directly instead of leaving it to guesswork over a log.
 *
 * Outside the mixin package, because the diagnostics screen reads it and
 * ordinary code cannot touch a class the mixin config owns.
 */
public final class ItemScaleReport {

    private static volatile boolean ground = false;
    private static volatile boolean hand = false;
    private static volatile boolean hotbar = false;

    /**
     * Whether the render state carries the mod's own fields.
     *
     * The ground case runs over three links: the extraction hook writes the
     * item's id onto the render state, the state mixin gives it somewhere to
     * live, and the drawing hook reads it back. A cast that fails is caught -
     * it has to be, a per-frame exception is not something to let out - and the
     * item then simply draws at its normal size. Which is indistinguishable
     * from a setting that was never made, and that is what made this so hard
     * to place. 0 unknown, 1 yes, -1 no.
     */
    private static volatile int holder = 0;

    /** The last id the extraction hook actually captured. */
    private static volatile String lastId = null;

    public static void sawGround() { ground = true; }

    public static void sawHolder(boolean present) { holder = present ? 1 : -1; }

    public static void sawId(String id) {
        if (id != null && !id.isEmpty()) lastId = id;
    }
    public static void sawHand() { hand = true; }
    public static void sawHotbar() { hotbar = true; }

    /** Whether a dropped item was ever submitted, which is what makes the
     *  culling report's silence readable: no item drawn means nothing to
     *  judge, an item drawn with nothing judged means the hook is dead. */
    public static boolean sawGroundItem() { return ground; }

    /**
     * Reads as a list of what has been seen so far.
     *
     * "not yet" rather than "broken", because a hook only reports once
     * something it applies to has been drawn - no dropped item on screen means
     * no ground report, and that is not a fault.
     */
    public static String status() {
        return "ground " + mark(ground)
                + ", hand " + mark(hand)
                + ", hotbar " + mark(hotbar);
    }

    /**
     * The ground case broken into its three links, because "ground ok" above
     * only means the drawing hook ran - not that it had anything to read.
     */
    public static String groundChain() {
        String fields = switch (holder) {
            case 1 -> "fields ok";
            case -1 -> "fields MISSING";
            default -> "fields not yet";
        };
        return fields + ", id " + (lastId == null ? "never captured" : lastId);
    }

    private static String mark(boolean seen) { return seen ? "ok" : "not yet"; }

    private ItemScaleReport() {}
}
