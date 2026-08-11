package gg.spaceclient;

import gg.spaceclient.config.ConfigManager;
import gg.spaceclient.module.HudModule;
import gg.spaceclient.module.ModuleManager;
import gg.spaceclient.ui.SpaceMenuScreen;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyMappingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpaceClient implements ClientModInitializer {
    public static final String MOD_ID = "spaceclient";
    public static final String VERSION = "0.1.0";
    public static final Logger LOGGER = LoggerFactory.getLogger("Space Client");

    private static ModuleManager moduleManager;
    private static ConfigManager configManager;
    private static KeyMapping menuKey;

    public static ModuleManager getModuleManager() { return moduleManager; }
    public static ConfigManager getConfigManager() { return configManager; }

    @Override
    public void onInitializeClient() {
        moduleManager = new ModuleManager();
        configManager = new ConfigManager();
        configManager.load();

        // Registers this account with the badge service and pulls the user list
        gg.spaceclient.badge.UserRegistry.registerAndRefresh();

        // Right Shift opens the menu. Registered as a keybind so players can
        // rebind it in vanilla controls if they want.
        menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.spaceclient.menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "category.spaceclient"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.consumeClick()) {
                client.setScreen(new SpaceMenuScreen());
            }
            moduleManager.onTick();
        });

        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.options.hideGui) return;
            // The HUD editor draws its own preview, so skip the normal pass there
            if (client.currentScreen instanceof gg.spaceclient.ui.HudEditorScreen) return;

            int width = client.getWindow().getGuiScaledWidth();
            int height = client.getWindow().getGuiScaledHeight();

            for (HudModule module : moduleManager.getHudModules()) {
                if (!module.isEnabled()) continue;

                int x = module.getX(width);
                int y = module.getY(height);
                float scale = module.getScale();

                if (scale != 1.0f) {
                    context.pose().push();
                    context.pose().translate(x, y, 0);
                    context.pose().scale(scale, scale, 1.0f);
                    module.render(context, 0, 0);
                    context.pose().pop();
                } else {
                    module.render(context, x, y);
                }
            }
        });

        LOGGER.info("Space Client ready - press Right Shift");
    }
}
