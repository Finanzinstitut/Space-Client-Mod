package gg.spaceclient.render;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.platform.Platform;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The typeface of the lyrics in the sky.
 *
 * Mostly the player's own: a font already installed on their computer, which
 * the client reads but never ships. On a Mac that is Apple's SF Pro (and its
 * rounded and serif siblings), the face Apple Music's lyrics are set in; on
 * Windows Segoe UI. Apple's fonts may not be bundled with anything that is
 * not Apple software, but reading the copy the system already has is how
 * every program on a Mac draws text.
 *
 * INTER is the one that ships, for systems with none of the above, and
 * OWN_FILE takes any .ttf or .otf the player drops in as
 * config/spaceclient/sky-font.ttf (or .otf).
 */
public final class SkyFont {

    public static final String SYSTEM = "SYSTEM";
    public static final String ROUNDED = "ROUNDED";
    public static final String SERIF = "SERIF";
    public static final String INTER = "INTER";
    public static final String OWN_FILE = "OWN_FILE";
    public static final java.util.List<String> CHOICES = java.util.List.of(SYSTEM, ROUNDED, SERIF, INTER, OWN_FILE);

    private static final Map<String, Font> cache = new HashMap<>();
    private static Set<String> families;

    private SkyFont() {}

    /** The face for a choice, at size 1; derive the size and weight from it. */
    public static synchronized Font of(String choice) {
        return cache.computeIfAbsent(choice, SkyFont::resolve);
    }

    /** Forgets the faces, so a font file dropped in while playing is read. */
    public static synchronized void reload() {
        cache.clear();
    }

    private static Font resolve(String choice) {
        Font found = switch (choice) {
            case ROUNDED -> first(file("/System/Library/Fonts/SFNSRounded.ttf"),
                    family("SF Pro Rounded"), family("Arial Rounded MT Bold"), family("Nunito"));
            case SERIF -> first(file("/System/Library/Fonts/NewYork.ttf"),
                    family("New York"), family("Georgia"), family("Cambria"), new Font(Font.SERIF, Font.PLAIN, 1));
            case INTER -> bundled();
            case OWN_FILE -> first(file(own("ttf")), file(own("otf")));
            default -> null;
        };
        if (found == null) {
            // SYSTEM, and the fallback for any choice this computer lacks
            found = first(file("/System/Library/Fonts/SFNS.ttf"), family("SF Pro Display"), family("SF Pro"),
                    family("Segoe UI Variable Display"), family("Segoe UI"), bundled());
        }
        return found != null ? found : new Font(Font.SANS_SERIF, Font.PLAIN, 1);
    }

    private static Path own(String extension) {
        return Platform.configDir().resolve("spaceclient").resolve("sky-font." + extension);
    }

    private static Font first(Font... fonts) {
        for (Font f : fonts) if (f != null) return f;
        return null;
    }

    private static Font file(String path) {
        return file(Path.of(path));
    }

    private static Font file(Path path) {
        if (!Files.isRegularFile(path)) return null;
        try (InputStream in = Files.newInputStream(path)) {
            return Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(1f);
        } catch (Throwable t) {
            SpaceClient.LOGGER.warn("Sky lyrics: could not read the font {}: {}", path, t.toString());
            return null;
        }
    }

    private static Font family(String name) {
        try {
            if (families == null) {
                families = Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
            }
            return families.contains(name) ? new Font(name, Font.PLAIN, 1) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Font bundled() {
        try (InputStream in = SkyFont.class.getResourceAsStream("/assets/spaceclient/font/sky.ttf")) {
            return in == null ? null : Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(1f);
        } catch (Throwable t) {
            return null;
        }
    }
}
