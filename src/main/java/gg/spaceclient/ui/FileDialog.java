package gg.spaceclient.ui;

import gg.spaceclient.SpaceClient;

import java.lang.reflect.Method;

/**
 * The system's file chooser, through the LWJGL file dialog the game ships.
 *
 * By name, because not every version ships it: 1.21.11 to 26.2 do, 26.3 no
 * longer does. Where it is missing, callers fall back to something else.
 */
public final class FileDialog {

    private static Method open;
    private static boolean looked;

    private FileDialog() {}

    private static synchronized Method method() {
        if (!looked) {
            looked = true;
            try {
                Class<?> dialogs = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
                Class<?> pointers = Class.forName("org.lwjgl.PointerBuffer");
                open = dialogs.getMethod("tinyfd_openFileDialog",
                        CharSequence.class, CharSequence.class, pointers, CharSequence.class, boolean.class);
            } catch (Throwable t) {
                open = null;
            }
        }
        return open;
    }

    public static boolean available() {
        return method() != null;
    }

    /**
     * Asks for PNG files. Blocks until the dialog closes.
     *
     * @return the chosen paths separated by "|", or null if nothing was chosen
     *         or there is no dialog on this version
     */
    public static String openPngs(String title) {
        Method m = method();
        if (m == null) return null;
        try {
            // No filter list: building one needs native memory, and a PNG
            // check happens afterwards anyway
            Object picked = m.invoke(null, title, "", null, null, true);
            return picked == null ? null : picked.toString();
        } catch (Throwable t) {
            SpaceClient.LOGGER.warn("The file dialog failed: {}", t.toString());
            return null;
        }
    }
}
