//#if FABRIC
package gg.spaceclient.platform;

import gg.spaceclient.SpaceClient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * Fabric's way in: the client entrypoint named in fabric.mod.json. Starts the
 * mod and hands Fabric's events to Hooks.
 */
public class FabricEntry implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        SpaceClient.registerKeys();
        for (KeyMapping key : Hooks.keys()) KeyMappingHelper.registerKeyMapping(key);

        SpaceClient.init();

        ClientTickEvents.END_CLIENT_TICK.register(Hooks::fireTick);

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            Hooks.fireScreenInit(client, screen, width, height);
            ScreenEvents.afterExtract(screen).register(Hooks::fireScreenDrawn);
        });

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(SpaceClient.MOD_ID, "hud"), Hooks::fireBeforeChat);
        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(SpaceClient.MOD_ID, "totem"), Hooks::fireAfterChat);

        ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> Hooks.fireJoin());
    }
}
//#endif
