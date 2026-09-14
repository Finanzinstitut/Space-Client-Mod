package gg.spaceclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import gg.spaceclient.SpaceClient;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Which HUD layout belongs to which server.
 *
 * The comment on Profiles already names the problem it could not finish
 * solving: the way somebody wants their screen arranged is not one arrangement,
 * and rebuilding it by hand for each activity is something nobody actually
 * keeps doing - so they settle on one cluttered layout that is wrong for
 * everything. Profiles made the arrangements switchable. This makes the switch
 * happen on its own, which is the half that decides whether any of it gets used.
 *
 * <h2>Why this one can do what the launcher's mod profiles cannot</h2>
 *
 * Mods are fixed at startup: Fabric reads the folder once, weaves mixins as
 * classes load, and nothing can take that back - so switching a mod set means
 * restarting, and the launcher is the only place that can do it. HUD modules
 * are the opposite. They are this mod's own objects, switched on and off at
 * runtime all day by the menu. Doing it on connect costs nothing and needs
 * nobody's permission.
 *
 * The key is the address the game holds, not a name: names are edited, and an
 * entry renamed in the server list should not quietly lose its layout.
 */
public final class HudServerProfiles {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Stands in for singleplayer and the menus, which are also a context. */
    public static final String OFFLINE = "";

    private static JsonObject entries = new JsonObject();
    private static boolean loaded = false;

    private HudServerProfiles() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("spaceclient-server-layouts.json");
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            Path path = file();
            if (!Files.exists(path)) return;
            JsonObject parsed = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            entries = parsed;
        } catch (Throwable t) {
            SpaceClient.LOGGER.warn("Could not read the server layout assignments", t);
            entries = new JsonObject();
        }
    }

    private static void write() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(entries));
        } catch (Throwable t) {
            SpaceClient.LOGGER.warn("Could not save the server layout assignments", t);
        }
    }

    private static String key(String address) {
        return address == null ? OFFLINE : address;
    }

    /** The layout assigned to this address, or null when there is none. */
    public static String profileFor(String address) {
        ensureLoaded();
        var value = entries.get(key(address));
        if (value == null || value.isJsonNull()) return null;
        String name = value.getAsString();
        // An assignment to a layout that has since been deleted is worse than
        // none: it would switch to something that no longer exists.
        return Profiles.list().contains(name) ? name : null;
    }

    public static void assign(String address, String profile) {
        ensureLoaded();
        entries.addProperty(key(address), profile);
        write();
    }

    public static void clear(String address) {
        ensureLoaded();
        entries.remove(key(address));
        write();
    }

    /** Every assignment, for the diagnostics screen. */
    public static List<String> describe() {
        ensureLoaded();
        List<String> out = new ArrayList<>();
        for (String address : entries.keySet()) {
            String shown = address.isEmpty() ? "singleplayer" : address;
            out.add(shown + " -> " + entries.get(address).getAsString());
        }
        return out;
    }
}
