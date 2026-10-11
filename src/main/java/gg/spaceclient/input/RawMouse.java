package gg.spaceclient.input;

/**
 * Watches the scroll wheel without taking it away from the game.
 *
 * The wheel reaches the game through MouseHandler.onScroll; MouseScrollMixin
 * looks at it there first. Normally it passes straight on - the hotbar needs
 * it - but while the zoom is active the scroll belongs to the zoom, and the
 * hotbar is left alone deliberately.
 *
 * This used to chain a GLFW scroll callback. Since 26.3 the game runs on SDL3
 * and there is no callback to chain, so the game's own handler is the place.
 */
public final class RawMouse {

    /** Accumulated wheel movement since it was last read. */
    private static volatile double pending = 0;

    /** Set by whoever wants the wheel; the game keeps it otherwise. */
    private static volatile boolean capture = false;

    /** Set once the mixin has seen a scroll, for the diagnostics screen. */
    private static volatile boolean seen = false;

    public static void setCapture(boolean value) { capture = value; }

    /** The hook is part of the game's own handler, so it is always in place. */
    public static boolean isInstalled() { return true; }

    public static boolean hasSeenScroll() { return seen; }

    /** Wheel movement since the last call, in notches. */
    public static int consumeSteps() {
        double value = pending;
        pending = 0;
        return (int) Math.round(value);
    }

    /**
     * Called by MouseScrollMixin for every wheel movement.
     * @return true when the scroll was taken and the game should not see it
     */
    public static boolean onScroll(double yOffset) {
        seen = true;
        if (!capture) return false;
        pending += yOffset;
        return true;
    }

    /** Kept so callers from before 26.3 still compile; nothing to install now. */
    public static void install() {}

    private RawMouse() {}
}
