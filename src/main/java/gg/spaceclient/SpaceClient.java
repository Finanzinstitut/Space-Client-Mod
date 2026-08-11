package gg.spaceclient;

import gg.spaceclient.config.ConfigManager;
import gg.spaceclient.module.HudModule;
import gg.spaceclient.module.ModuleManager;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpaceClient implements ClientModInitializer {
    public static final String MOD_ID = "spaceclient";
    public static final String VERSION = "0.1.0";
    public static final Logger LOGGER = LoggerFactory.getLogger("Space Client");

    private static ModuleManager moduleManager;
    private static ConfigManager configManager;

    public static ModuleManager getModuleManager() { return moduleManager; }
    public static ConfigManager getConfigManager() { return configManager; }

    @Override
    public void onInitializeClient() {
        moduleManager = new ModuleManager();
        configManager = new ConfigManager();
        configManager.load();

        ClientTickEvents.END_CLIENT_TICK.register(client -> moduleManager.onTick());

        // Our elements draw just before the chat, so the HUD API handles layering.
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "hud"),
                SpaceClient::renderHud
        );

        LOGGER.info("Space Client {} ready", VERSION);
    }

    private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();

        for (HudModule module : moduleManager.getHudModules()) {
            if (!module.isEnabled()) continue;
            module.render(graphics, module.getX(width), module.getY(height));
        }
    }
}
