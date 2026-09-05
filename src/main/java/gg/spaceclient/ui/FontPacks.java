package gg.spaceclient.ui;

import gg.spaceclient.SpaceClient;

import net.minecraft.client.Minecraft;

/**
 * Turns the bundled Doodle resource pack on or off.
 *
 * The pack itself is registered with Fabric at startup (see the initializer),
 * which makes it appear as a built-in pack the game can enable. This class flips
 * it in the active list and reloads, so the change is total and permanent - it
 * survives restarts because the enabled state is saved with the other packs.
 *
 * The one detail that has to be right on this version is the id Fabric gives a
 * built-in pack, which is namespaced. That is resolved once against the actual
 * pack list rather than assumed, so a wrong guess degrades to "pack not found"
 * instead of doing nothing silently.
 */
public final class FontPacks {

    /** The full id Fabric assigns, discovered from the repository. */
    private static String resolvedId = null;

    private static String findId(Minecraft mc) {
        if (resolvedId != null) return resolvedId;
        try {
            // A built-in pack registered by a mod is listed under an id that
            // ends in the name we gave it. Match on the suffix rather than
            // guessing the prefix, which differs between loader versions.
            for (var pack : mc.getResourcePackRepository().getAvailableIds()) {
                if (pack.endsWith(Fonts.DOODLE_PACK) || pack.contains("spaceclient")) {
                    if (pack.contains(Fonts.DOODLE_PACK)) {
                        resolvedId = pack;
                        return pack;
                    }
                }
            }
        } catch (Throwable t) {
            SpaceClient.LOGGER.warn("Could not scan resource packs: {}", t.getMessage());
        }
        return resolvedId;
    }

    public static void setEnabled(boolean enabled) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        try {
            var repo = mc.getResourcePackRepository();
            String id = findId(mc);
            if (id == null) {
                SpaceClient.LOGGER.warn("Doodle pack not registered yet");
                return;
            }

            var selected = new java.util.ArrayList<>(repo.getSelectedIds());
            boolean present = selected.contains(id);

            if (enabled && !present) {
                selected.add(id);
            } else if (!enabled && present) {
                selected.remove(id);
            } else {
                return; // already in the wanted state
            }

            mc.options.resourcePacks.clear();
            mc.options.resourcePacks.addAll(selected);
            // Keep only real, still-available ids
            mc.options.resourcePacks.removeIf(p -> !repo.getAvailableIds().contains(p));

            repo.setSelected(selected);
            mc.options.save();
            mc.reloadResourcePacks();

        } catch (Throwable t) {
            SpaceClient.LOGGER.warn("Could not toggle the font pack: {}", t.getMessage());
        }
    }

    private FontPacks() {}
}
