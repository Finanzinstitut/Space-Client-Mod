package gg.spaceclient.font;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.util.Reflect;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Switching the game's font, by being a resource pack rather than by fighting
 * one.
 *
 * Each style ships inside the mod jar as a finished zip. Choosing one writes it
 * into the instance's resourcepacks folder and puts its id at the end of the
 * selected list, which is the highest priority position - so a Space Client
 * font wins over whatever font the player's own resource pack sets, which is
 * the whole point of the feature.
 *
 * Doing it this way rather than by mixing into the font system is a deliberate
 * trade. The font pipeline was reworked in 26.2 (Style now takes a
 * FontDescription rather than a plain id), so a mixin into it would be a guess
 * at a signature, and a wrong guess is a failed CI build. The pack repository
 * is older, plainer and shared with the server, and everything here that could
 * still have been renamed goes through Reflect - so the worst case is a font
 * that does not change, not a client that does not build.
 *
 * Selection is not stored twice. Minecraft writes the selected packs into
 * options.txt itself after a reload, so a font chosen once is still there next
 * launch without this class doing anything on startup. The config entry exists
 * so the menu can show which one is picked, not to drive the game.
 */
public final class FontPacks {

    /**
     * Prefix on every file this class writes.
     *
     * It is also how a pack is recognised as ours when the selected list is
     * rebuilt: anything starting with this is removed before the new choice is
     * appended, which is what stops two fonts from stacking.
     */
    private static final String PREFIX = "spaceclient-font-";

    /** Vanilla's FolderRepositorySource names packs from that folder this way. */
    private static final String ID_PREFIX = "file/";

    private static boolean synced = false;
    private static boolean warned = false;

    /** File name of the zip for a style, e.g. spaceclient-font-smooth.zip */
    private static String fileName(FontStyle style) {
        return PREFIX + style.file() + ".zip";
    }

    /** Pack id as the repository will report it. */
    private static String packId(FontStyle style) {
        return ID_PREFIX + fileName(style);
    }

    private static Path packFolder() {
        return FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
    }

    /**
     * Puts the zip on disk if it is not already there.
     *
     * Compared by size rather than by existence alone, so a pack updated in a
     * new build of the mod replaces the old copy instead of being ignored
     * forever. Content hashing would be stricter, but these files only ever
     * change when the mod ships new ones, and a size difference catches that.
     */
    private static boolean extract(FontStyle style) {
        String resource = "/assets/spaceclient/fonts/" + style.file() + ".zip";
        try (InputStream in = FontPacks.class.getResourceAsStream(resource)) {
            if (in == null) {
                SpaceClient.LOGGER.warn("Font pack {} is missing from the jar", resource);
                return false;
            }
            byte[] bytes = in.readAllBytes();

            Path folder = packFolder();
            Files.createDirectories(folder);
            Path target = folder.resolve(fileName(style));

            if (Files.exists(target) && Files.size(target) == bytes.length) return true;

            Files.write(target, bytes);
            SpaceClient.LOGGER.info("Installed font pack {}", target.getFileName());
            return true;

        } catch (Exception e) {
            SpaceClient.LOGGER.error("Could not install font pack {}", style.id(), e);
            return false;
        }
    }

    private static Object repository(Minecraft mc) {
        return Reflect.call(mc, "getResourcePackRepository");
    }

    private static List<String> selectedIds(Object repository) {
        Object ids = Reflect.call(repository, "getSelectedIds");
        if (!(ids instanceof Collection<?> collection)) return null;

        List<String> out = new ArrayList<>();
        for (Object id : collection) {
            if (id instanceof String text) out.add(text);
        }
        return out;
    }

    /** Every entry that is one of ours, whichever style it belongs to. */
    private static boolean isOurs(String id) {
        return id.startsWith(ID_PREFIX + PREFIX);
    }

    /**
     * Called once, on the first client tick.
     *
     * Deliberately not from onInitializeClient: the pack repository is not
     * necessarily built yet at that point, and a reload triggered during
     * startup would be a reload of things that are about to be loaded anyway.
     *
     * In the normal case this does nothing at all. Minecraft already restored
     * the selection from options.txt, so the desired state and the actual state
     * match and no reload is asked for - the font is simply already right.
     */
    public static void sync() {
        if (synced) return;
        synced = true;
        apply(SpaceClient.getSettings().fontStyle());
    }

    /** Switches to a style. Reloads resources only when something changes. */
    public static void apply(String styleId) {
        try {
            FontStyle style = FontStyle.byId(styleId);

            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;

            Object repository = repository(mc);
            List<String> selected = repository == null ? null : selectedIds(repository);
            if (selected == null) {
                if (!warned) {
                    warned = true;
                    SpaceClient.LOGGER.warn(
                            "Resource pack repository could not be reached - "
                                    + "the font setting will have no effect");
                }
                return;
            }

            // Nothing to install for the vanilla entry: it is the absence of a
            // pack, not a pack of its own.
            if (!style.isVanilla() && !extract(style)) return;

            List<String> target = new ArrayList<>();
            for (String id : selected) {
                if (!isOurs(id)) target.add(id);
            }
            // Last means highest priority, which is what puts this font above
            // the player's own resource packs.
            if (!style.isVanilla()) target.add(packId(style));

            if (target.equals(selected)) return;

            // Rescan the folder first, or a zip written a moment ago is not yet
            // something the repository knows how to select.
            Reflect.call(repository, "reload");
            Reflect.callWith(repository, "setSelected", target);

            // Minecraft writes the new selection back into options.txt when
            // this finishes, so the choice survives a restart on its own.
            Reflect.call(mc, "reloadResourcePacks");

            SpaceClient.LOGGER.info("Font set to {}", style.label());

        } catch (Throwable e) {
            // A font is never worth a crash
            SpaceClient.LOGGER.error("Could not apply font style {}", styleId, e);
        }
    }

    private FontPacks() {}
}
