package gg.spaceclient;

import gg.spaceclient.config.ConfigManager;
import gg.spaceclient.module.HudModule;
import gg.spaceclient.module.ModuleManager;
import gg.spaceclient.ui.SpaceMenuScreen;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpaceClient implements ClientModInitializer {
    public static final String MOD_ID = "spaceclient";
    public static final Logger LOGGER = LoggerFactory.getLogger("Space Client");

    private static ModuleManager moduleManager;
    private static ConfigManager configManager;
    private static KeyBinding menuKey;

    public static ModuleManager getModuleManager() { return moduleManager; }
    public static ConfigManager getConfigManager() { return configManager; }

    @Override
    public void onInitializeClient() {
        moduleManager = new ModuleManager();
        configManager = new ConfigManager();
        configManager.load();

        // Pulls the published list of Space Client users for the Jupiter badge
        gg.spaceclient.badge.UserRegistry.refresh();

        // Right Shift opens the menu. Registered as a keybind so players can
        // rebind it in vanilla controls if they want.
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spaceclient.menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "category.spaceclient"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.wasPressed()) {
                client.setScreen(new SpaceMenuScreen());
            }
            moduleManager.onTick();
        });

        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.options.hudHidden) return;
            // The HUD editor draws its own preview, so skip the normal pass there
            if (client.currentScreen instanceof gg.spaceclient.ui.HudEditorScreen) return;

            int width = client.getWindow().getScaledWidth();
            int height = client.getWindow().getScaledHeight();

            for (HudModule module : moduleManager.getHudModules()) {
                if (!module.isEnabled()) continue;

                int x = module.getX(width);
                int y = module.getY(height);
                float scale = module.getScale();

                if (scale != 1.0f) {
                    context.getMatrices().push();
                    context.getMatrices().translate(x, y, 0);
                    context.getMatrices().scale(scale, scale, 1.0f);
                    module.render(context, 0, 0);
                    context.getMatrices().pop();
                } else {
                    module.render(context, x, y);
                }
            }
        });

        LOGGER.info("Space Client ready - press Right Shift");
    }
}
