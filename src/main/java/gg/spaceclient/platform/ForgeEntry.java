//#if FORGE
//$$ package gg.spaceclient.platform;
//$$
//$$ import gg.spaceclient.SpaceClient;
//$$
//$$ import net.minecraft.client.KeyMapping;
//$$ import net.minecraft.client.Minecraft;
//$$ import net.minecraft.client.gui.screens.Screen;
//$$ import net.minecraft.resources.Identifier;
//$$
//$$ import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
//$$ import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
//$$ import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
//$$ import net.minecraftforge.client.event.ScreenEvent;
//$$ import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
//$$ import net.minecraftforge.event.TickEvent;
//$$ import net.minecraftforge.fml.common.Mod;
//$$ import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
//$$ import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
//$$
//$$ /**
//$$  * Forge's way in. Like NeoForge's: the constructor only listens, the mod
//$$  * starts on the main thread once the client is set up, and key mappings and
//$$  * HUD layers go in through the events Forge asks for them in.
//$$  */
//$$ @Mod(SpaceClient.MOD_ID)
//$$ public class ForgeEntry {
//$$
//$$     public ForgeEntry(FMLJavaModLoadingContext context) {
//$$         var modBus = context.getModBusGroup();
//$$
        //#if MC >= 26.1
//$$         RegisterKeyMappingsEvent.BUS.addListener(ForgeEntry::keys);
//$$         AddGuiOverlayLayersEvent.BUS.addListener(ForgeEntry::layers);
        //#else
//$$         RegisterKeyMappingsEvent.getBus(modBus).addListener(ForgeEntry::keys);
//$$         AddGuiOverlayLayersEvent.getBus(modBus).addListener(ForgeEntry::layers);
        //#endif
//$$         FMLClientSetupEvent.getBus(modBus).addListener(event -> event.enqueueWork(SpaceClient::init));
//$$
//$$         TickEvent.ClientTickEvent.Post.BUS.addListener(event -> Hooks.fireTick(Minecraft.getInstance()));
//$$
//$$         ScreenEvent.Init.Post.BUS.addListener(event -> {
//$$             Screen screen = event.getScreen();
//$$             Hooks.fireScreenInit(Minecraft.getInstance(), screen, screen.width, screen.height);
//$$         });
//$$
//$$         ScreenEvent.Render.Post.BUS.addListener(event ->
//$$                 Hooks.fireScreenDrawn(event.getScreen(), event.getGuiGraphics(),
//$$                         event.getMouseX(), event.getMouseY(), event.getPartialTick()));
//$$
//$$         ClientPlayerNetworkEvent.LoggingIn.BUS.addListener(event -> Hooks.fireJoin());
//$$     }
//$$
//$$     private static void keys(RegisterKeyMappingsEvent event) {
//$$         SpaceClient.registerKeys();
//$$         for (KeyMapping key : Hooks.keys()) event.register(key);
//$$     }
//$$
//$$     private static void layers(AddGuiOverlayLayersEvent event) {
//$$         ForgeLayeredDraw root = event.getLayeredDraw();
//$$         root.addBelow(Identifier.fromNamespaceAndPath(SpaceClient.MOD_ID, "hud"),
//$$                 ForgeLayeredDraw.CHAT_OVERLAY, Hooks::fireBeforeChat);
//$$         root.addAbove(Identifier.fromNamespaceAndPath(SpaceClient.MOD_ID, "totem"),
//$$                 ForgeLayeredDraw.CHAT_OVERLAY, Hooks::fireAfterChat);
//$$     }
//$$ }
//#endif
