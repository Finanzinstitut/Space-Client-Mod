package gg.spaceclient.prank;

/**
 * The prank effects, and which one is running.
 *
 * Every effect here is local and visual. Nothing touches the network, nothing
 * is sent to the server, and nothing reaches another player - what plays is a
 * drawing on your own screen. That boundary is the whole point: a "crash" here
 * is a picture of a crash, not a crash, and it happens to you and only you.
 *
 * A video where it looks like a friend was hit works because your screen is
 * what the camera sees. The friend's game carries on untouched, which is also
 * what keeps this off every server's ban list.
 *
 * One effect at a time, so triggering a second replaces the first rather than
 * stacking into a mess. Everything is driven from here so the trigger screen,
 * the overlay and the input hooks all read one source of truth.
 */
public final class Pranks {

    public enum Effect {
        NONE,
        FAKE_CRASH,
        FAKE_KICK,
        FAKE_LAG,
        FAKE_BAN,
        REVERSED_CONTROLS,
        SCREEN_EFFECT,
        FAKE_CHAT
    }

    /** Which screen-effect variant SCREEN_EFFECT is showing. */
    public enum Screen {
        SHAKE, FLIP, STATIC, CRACKED
    }

    private static Effect active = Effect.NONE;
    private static Screen screenKind = Screen.SHAKE;
    private static long startedAt = 0;
    private static long durationMs = 0;

    /** Free text the ban and kick screens show, set from the trigger menu. */
    private static String reason = "Cheating";
    private static String banDuration = "permanently";

    /** A fake chat line waiting to be shown, and when to show it. */
    private static String pendingChat = "";

    public static Effect active() { return active; }
    public static Screen screenKind() { return screenKind; }
    public static String reason() { return reason; }
    public static String banDuration() { return banDuration; }
    public static String pendingChat() { return pendingChat; }

    public static void setReason(String value) { reason = value; }
    public static void setBanDuration(String value) { banDuration = value; }

    /**
     * Starts an effect for a set time.
     *
     * A duration of zero means "until dismissed", which the full-screen ones
     * (crash, kick, ban) use - they wait for a key rather than a clock.
     */
    public static void start(Effect effect, long ms) {
        active = effect;
        durationMs = ms;
        startedAt = System.currentTimeMillis();
    }

    public static void startScreen(Screen kind, long ms) {
        screenKind = kind;
        start(Effect.SCREEN_EFFECT, ms);
    }

    public static void queueChat(String line) {
        pendingChat = line;
        start(Effect.FAKE_CHAT, 100);
    }

    /** Ends whatever is running. The full-screen effects call this on a key. */
    public static void clear() {
        active = Effect.NONE;
        durationMs = 0;
        pendingChat = "";
    }

    /** Seconds since the current effect began, for animation. */
    public static float elapsedSeconds() {
        return (System.currentTimeMillis() - startedAt) / 1000f;
    }

    /**
     * Whether a timed effect has run its course.
     *
     * Called each frame; ends the effect once its clock is up. Effects with a
     * zero duration never expire this way and wait for a key instead.
     */
    public static boolean expired() {
        if (active == Effect.NONE) return true;
        if (durationMs <= 0) return false;
        return System.currentTimeMillis() - startedAt > durationMs;
    }

    /** True while an effect that swallows the whole screen is up. */
    public static boolean isFullScreen() {
        return active == Effect.FAKE_CRASH
                || active == Effect.FAKE_KICK
                || active == Effect.FAKE_BAN;
    }

    private Pranks() {}
}
