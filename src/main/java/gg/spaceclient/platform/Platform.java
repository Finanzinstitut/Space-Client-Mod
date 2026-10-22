package gg.spaceclient.platform;

import java.nio.file.Path;

/**
 * The handful of facts the mod needs from whichever loader it runs on: where
 * the config and game folders are, which other mods are installed, and its own
 * version. One branch per loader, picked when the source is preprocessed.
 */
public final class Platform {

    private static final String MOD_ID = "spaceclient";

    private Platform() {}

    /** "fabric", "neoforge" or "forge". */
    public static String loader() {
        //#if FABRIC
        return "fabric";
        //#elseif NEOFORGE
        //$$ return "neoforge";
        //#else
        //$$ return "forge";
        //#endif
    }

    public static Path configDir() {
        //#if FABRIC
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
        //#elseif NEOFORGE
        //$$ return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();
        //#else
        //$$ return net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get();
        //#endif
    }

    public static Path gameDir() {
        //#if FABRIC
        return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
        //#elseif NEOFORGE
        //$$ return net.neoforged.fml.loading.FMLPaths.GAMEDIR.get();
        //#else
        //$$ return net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get();
        //#endif
    }

    /** Whether another mod is installed, once mods have started. */
    public static boolean isModLoaded(String id) {
        try {
            //#if FABRIC
            return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(id);
            //#elseif NEOFORGE
            //$$ net.neoforged.fml.ModList list = net.neoforged.fml.ModList.get();
            //$$ return list != null ? list.isLoaded(id) : isModPresentEarly(id);
            //#elseif MC >= 26.1
            //$$ return net.minecraftforge.fml.ModList.isLoaded(id);
            //#else
            //$$ net.minecraftforge.fml.ModList list = net.minecraftforge.fml.ModList.get();
            //$$ return list != null ? list.isLoaded(id) : isModPresentEarly(id);
            //#endif
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Whether another mod is installed, asked before any mod has started - from
     * a mixin plugin, say, where the finished mod list does not exist yet.
     */
    public static boolean isModPresentEarly(String id) {
        try {
            //#if FABRIC
            return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(id);
            //#elseif NEOFORGE
            //$$ return net.neoforged.fml.loading.LoadingModList.get().getModFileById(id) != null;
            //#elseif MC >= 26.1
            //$$ return net.minecraftforge.fml.loading.LoadingModList.getModFileById(id) != null;
            //#else
            //$$ return net.minecraftforge.fml.loading.FMLLoader.getLoadingModList().getModFileById(id) != null;
            //#endif
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** This mod's own version, as the loader read it from the jar. */
    public static String modVersion() {
        //#if FABRIC
        return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
        //#elseif NEOFORGE
        //$$ var file = net.neoforged.fml.loading.LoadingModList.get().getModFileById(MOD_ID);
        //$$ return file == null ? "dev" : file.getMods().get(0).getVersion().toString();
        //#elseif MC >= 26.1
        //$$ var file = net.minecraftforge.fml.loading.LoadingModList.getModFileById(MOD_ID);
        //$$ return file == null ? "dev" : file.getMods().get(0).getVersion().toString();
        //#else
        //$$ var file = net.minecraftforge.fml.loading.FMLLoader.getLoadingModList().getModFileById(MOD_ID);
        //$$ return file == null ? "dev" : file.getMods().get(0).getVersion().toString();
        //#endif
    }
}
