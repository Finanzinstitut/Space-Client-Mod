package gg.spaceclient.config;

import com.google.gson.*;
import gg.spaceclient.SpaceClient;
import gg.spaceclient.module.Module;
import gg.spaceclient.modules.KeystrokesModule;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Reads and writes the single JSON config in the instance's config folder. */
public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;

    public ConfigManager() {
        this.file = FabricLoader.getInstance().getConfigDir().resolve("spaceclient.json");
    }

    public void save() {
        JsonObject root = new JsonObject();

        JsonObject interfaceJson = new JsonObject();
        SpaceClient.getSettings().save(interfaceJson);
        root.add("interface", interfaceJson);

        JsonObject modules = new JsonObject();

        for (Module module : SpaceClient.getModuleManager().getAll()) {
            JsonObject moduleJson = new JsonObject();
            module.save(moduleJson);
            if (module instanceof KeystrokesModule ks) {
                moduleJson.addProperty("custom_keys", ks.getCustomKeys());
            }
            modules.add(module.getId(), moduleJson);
        }
        root.add("modules", modules);

        JsonObject itemSizes = new JsonObject();
        ItemSizes.save(itemSizes);
        root.add("itemsizes", itemSizes);

        JsonObject profiles = new JsonObject();
        Profiles.saveState(profiles);
        root.add("profiles", profiles);

        JsonObject streamer = new JsonObject();
        gg.spaceclient.ui.StreamerMode.save(streamer);
        root.add("streamer", streamer);

        try {
            Files.createDirectories(file.getParent());
            // Written beside the real file and then swapped in. Writing in
            // place meant a crash mid-save left half a file, the next start
            // could not read it, fell back to defaults - and the next save
            // made the loss permanent. That is how item sizes "reset by
            // themselves". The last good copy is kept as .bak as well.
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(root));
            if (loadedCleanly && Files.exists(file)) {
                Files.copy(file, backup(), StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            SpaceClient.LOGGER.error("Could not save config", e);
        }
    }

    /** Whether the file on disk was read without trouble, so it is worth backing up. */
    private boolean loadedCleanly = false;

    private Path backup() {
        return file.resolveSibling(file.getFileName() + ".bak");
    }

    /** One part of the config, read on its own so a bad part cannot take the rest with it. */
    private void section(String name, Runnable read) {
        try {
            read.run();
        } catch (Exception e) {
            loadedCleanly = false;
            SpaceClient.LOGGER.warn("Could not read the {} part of the config, keeping its defaults", name, e);
        }
    }

    public void load() {
        if (!Files.exists(file)) {
            loadedCleanly = true;
            save();
            return;
        }
        JsonObject root = parse(file);
        if (root == null) {
            // The file is unreadable - most likely cut off by a crash while it
            // was being written. Kept as .broken so nothing is lost, and the
            // last good copy is read instead.
            try {
                Files.copy(file, file.resolveSibling(file.getFileName() + ".broken"),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // Nothing more to keep
            }
            root = Files.exists(backup()) ? parse(backup()) : null;
            if (root == null) {
                SpaceClient.LOGGER.error("Could not read config or its backup, using defaults");
                return;
            }
            SpaceClient.LOGGER.warn("Config was unreadable, restored the last good copy");
        }
        loadedCleanly = true;
        JsonObject data = root;

        if (data.has("interface")) {
            section("interface", () -> SpaceClient.getSettings().load(data.getAsJsonObject("interface")));
        }
        if (data.has("itemsizes")) {
            section("item sizes", () -> ItemSizes.load(data.getAsJsonObject("itemsizes")));
        }
        if (data.has("profiles")) {
            section("profiles", () -> Profiles.loadState(data.getAsJsonObject("profiles")));
        }
        if (data.has("streamer")) {
            section("streamer", () -> gg.spaceclient.ui.StreamerMode.load(data.getAsJsonObject("streamer")));
        }
        if (data.has("modules")) {
            JsonObject modules = data.getAsJsonObject("modules");
            for (Module module : SpaceClient.getModuleManager().getAll()) {
                if (!modules.has(module.getId())) continue;
                // Each module on its own: one that cannot read its part keeps
                // its defaults, and every module after it still loads
                section("module " + module.getId(), () -> {
                    JsonObject moduleJson = modules.getAsJsonObject(module.getId());
                    module.load(moduleJson);
                    if (module instanceof KeystrokesModule ks && moduleJson.has("custom_keys")) {
                        ks.setCustomKeys(moduleJson.get("custom_keys").getAsString());
                    }
                });
            }
        }
        // After the modules, not before: streamer mode is read first, so
        // anything it switched off would be switched straight back on by
        // the saved module states a moment later.
        section("streamer", gg.spaceclient.ui.StreamerMode::reapply);
    }

    private static JsonObject parse(Path path) {
        try {
            JsonElement element = JsonParser.parseString(Files.readString(path));
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
