package gg.spaceclient.ui;

/**
 * What happened the last time a world was opened or created.
 *
 * Both paths go through reflection - the world creator is reached by a static
 * method whose name has moved between versions, and opening a save goes through
 * an object this mod has never compiled against. Both already handled a miss
 * gracefully, and both handled it by writing a line to the log and returning.
 *
 * From the outside that is a button that does nothing. No message, no crash,
 * nothing on screen; you press Singleplayer, press the button, and the game
 * sits there. The log says why, and nobody reads the log.
 *
 * So the attempt is recorded here and shown on the diagnostics page, together
 * with the static methods the class actually offers on this version - which is
 * the answer to "what should it have been called", without anybody having to
 * run javap against the jar.
 */
public final class WorldReport {

    public static final String CREATE_SCREEN =
            "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen";

    private static String lastCreate = null;
    private static String lastOpen = null;

    private WorldReport() {}

    public static void created(String how) {
        lastCreate = "opened through " + how;
    }

    public static void createFailed() {
        lastCreate = "no usable method - offers: " + Construct.describeStatics(CREATE_SCREEN, null);
    }

    public static void opened() {
        lastOpen = "world opened";
    }

    public static void openFailed(String why) {
        lastOpen = why;
    }

    public static boolean createWorks() {
        return lastCreate == null || lastCreate.startsWith("opened");
    }

    public static boolean openWorks() {
        return lastOpen == null || lastOpen.startsWith("world opened");
    }

    public static String createStatus() {
        if (lastCreate != null) return lastCreate;
        // Answered before anybody presses it, because a button that has never
        // been pressed and one that silently failed look the same otherwise.
        return "not pressed yet - class offers: "
                + Construct.describeStatics(CREATE_SCREEN, null);
    }

    public static String openStatus() {
        return lastOpen == null ? "no world opened yet" : lastOpen;
    }
}
