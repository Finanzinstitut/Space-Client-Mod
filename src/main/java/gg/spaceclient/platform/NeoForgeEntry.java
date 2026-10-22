//#if NEOFORGE
//$$ package gg.spaceclient.platform;
//$$
//$$ import gg.spaceclient.SpaceClient;
//$$ import gg.spaceclient.modules.OverlayModule;
//$$
//$$ import net.minecraft.client.KeyMapping;
//$$ import net.minecraft.client.Minecraft;
//$$ import net.minecraft.client.gui.screens.Screen;
//$$ import net.minecraft.resources.Identifier;
//$$
//$$ import net.neoforged.api.distmarker.Dist;
//$$ import net.neoforged.bus.api.IEventBus;
//$$ import net.neoforged.fml.ModContainer;
//$$ import net.neoforged.fml.common.Mod;
//$$ import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
//$$ import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
//$$ import net.neoforged.neoforge.client.event.ClientTickEvent;
//$$ import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
//$$ import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
//$$ import net.neoforged.neoforge.client.event.ScreenEvent;
//$$ import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
//$$ import net.neoforged.neoforge.common.NeoForge;
//$$
//$$ /**
//$$  * NeoForge's way in. The constructor runs while mods are still loading,
//$$  * possibly off the main thread, so it only listens; the mod itself starts on
//$$  * the main thread once the client is set up. Key mappings and HUD layers are
//$$  * handed over in the events NeoForge asks for them in.
//$$  */
//$$ @Mod(value = SpaceClient.MOD_ID, dist = Dist.CLIENT)
//$$ public class NeoForgeEntry {
//$$
//$$     public NeoForgeEntry(IEventBus modBus, ModContainer container) {
//$$         modBus.addListener((RegisterKeyMappingsEvent event) -> {
//$$             SpaceClient.registerKeys();
//$$             event.registerCategory(SpaceClient.keyCategory());
//$$             for (KeyMapping key : Hooks.keys()) event.register(key);
//$$         });
//$$
//$$         modBus.addListener((RegisterGuiLayersEvent event) -> {
//$$             event.registerBelow(VanillaGuiLayers.CHAT,
//$$                     Identifier.fromNamespaceAndPath(SpaceClient.MOD_ID, "hud"), Hooks::fireBeforeChat);
//$$             event.registerAbove(VanillaGuiLayers.CHAT,
//$$                     Identifier.fromNamespaceAndPath(SpaceClient.MOD_ID, "totem"), Hooks::fireAfterChat);
//$$             // The hotbar block, from the hotbar to the held item's name, is a
//$$             // run of layers here; the Overlay module scales it as one
//$$             event.registerBelow(VanillaGuiLayers.HOTBAR,
//$$                     Identifier.fromNamespaceAndPath(SpaceClient.MOD_ID, "hotbar_begin"), OverlayModule::hotbarBegin);
//$$             event.registerAbove(VanillaGuiLayers.SPECTATOR_TOOLTIP,
//$$                     Identifier.fromNamespaceAndPath(SpaceClient.MOD_ID, "hotbar_end"), OverlayModule::hotbarEnd);
//$$         });
//$$
//$$         modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(SpaceClient::init));
//$$
//$$         NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) ->
//$$                 Hooks.fireTick(Minecraft.getInstance()));
//$$
//$$         NeoForge.EVENT_BUS.addListener((ScreenEvent.Init.Post event) -> {
//$$             Screen screen = event.getScreen();
//$$             Hooks.fireScreenInit(Minecraft.getInstance(), screen, screen.width, screen.height);
//$$         });
//$$
//$$         NeoForge.EVENT_BUS.addListener((ScreenEvent.Render.Post event) ->
//$$                 Hooks.fireScreenDrawn(event.getScreen(), event.getGuiGraphics(),
//$$                         event.getMouseX(), event.getMouseY(), event.getPartialTick()));
//$$
//$$         NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> Hooks.fireJoin());
//$$     }
//$$ }
//#endif
