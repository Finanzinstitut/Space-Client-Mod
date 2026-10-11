package gg.spaceclient.ui;

/**
 * Says what became of the opening sequence.
 *
 * The greeting stopped appearing and there was no way to tell which of two
 * things had eaten it, because both end with a finished menu and no
 * introduction.
 *
 * One: the menu is built more than once on the way up. The screen is replaced
 * from inside the game's own init event, and a screen that is built and then
 * thrown away used to be enough to mark the introduction as already shown - so
 * the copy you actually got skipped it.
 *
 * Two: the menu is drawn behind the loading overlay. The overlay covers the
 * screen while the menu underneath renders as normal, so a clock started on the
 * first frame spends the whole greeting on frames nobody can see.
 *
 * Both are now handled, and this line says which one was in play, so the next
 * report about this does not start from scratch.
 */
public final class IntroReport {

    private static volatile boolean played = false;
    private static volatile boolean skipped = false;
    private static volatile int overlayFrames = 0;

    private IntroReport() {}

    /** A frame was drawn while the loading overlay still covered the menu. */
    public static void waitedForOverlay() { overlayFrames++; }

    public static void played() { played = true; }

    public static void skipped() { skipped = true; }

    public static boolean ok() { return played || !skipped; }

    public static String status() {
        String waited = overlayFrames == 0
                ? ""
                : " (held " + overlayFrames + " frames for the loading overlay)";

        if (played) return "played on this start" + waited;
        if (skipped) return "skipped - already played since the game was opened" + waited;
        return "main menu not opened yet" + waited;
    }
}
