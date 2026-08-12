package gg.spaceclient;

import gg.spaceclient.config.ClientSettings;
import gg.spaceclient.config.ConfigManager;
import gg.spaceclient.module.HudModule;
import gg.spaceclient.module.ModuleManager;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import com.mojang.blaze3d.platform.InputConstants;

import gg.spaceclient.session.SessionWatcher;
import gg.spaceclient.ui.SpaceMenuScreen;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
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
    private static ClientSettings settings;
    private static KeyMapping menuKey;
    /**
     * Minecraft has no default binding for the middle mouse button, so the
     * Mouse Tracker cannot read it without one. Registering it as a MOUSE-type
     * mapping also lets the player rebind it in the vanilla controls screen.
     */
    private static KeyMapping middleClickKey;

    public static ModuleManager getModuleManager() { return moduleManager; }
    public static ConfigManager getConfigManager() { return configManager; }
    public static ClientSettings getSettings() { return settings; }

    /** True while the middle mouse button is held. */
    public static boolean isMiddleClickDown() {
        return middleClickKey != null && middleClickKey.isDown();
    }

    @Override
    public void onInitializeClient() {
        moduleManager = new ModuleManager();
        settings = new ClientSettings();
        configManager = new ConfigManager();
        configManager.load();

        // Key mappings now take a registered Category object rather than a
        // translation key string.
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MOD_ID, "main"));

        menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.spaceclient.menu",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_RSHIFT,
                category
        ));

        middleClickKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.spaceclient.middleclick",
                InputConstants.Type.MOUSE,
                2, // GLFW middle mouse button
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.consumeClick()) {
                client.gui.setScreen(new SpaceMenuScreen());
            }
            moduleManager.onTick();
            SessionWatcher.tick(client);
        });

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
            module.draw(graphics, module.getX(width), module.getY(height));
        }
    }
}
