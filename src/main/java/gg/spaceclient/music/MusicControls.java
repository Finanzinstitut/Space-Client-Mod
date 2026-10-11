package gg.spaceclient.music;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.modules.MusicModule;
import gg.spaceclient.ui.FlatButton;
import gg.spaceclient.ui.ScreenInjector;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Puts playback buttons over the Now Playing element while the chat is open.
 *
 * The HUD itself cannot take clicks - it draws underneath everything and the
 * mouse is captured by the game. Once a screen has the cursor, though, ordinary
 * widgets work, so three small buttons are added to the chat screen exactly
 * where the element sits. Nothing is drawn there when the module is off or
 * nothing is playing.
 */
public final class MusicControls {

    public static void attach(Screen screen) {
        MusicModule module = (MusicModule) SpaceClient.getModuleManager().get("music");
        if (module == null || !module.isEnabled()) return;
        if (module.track().isEmpty()) return;
        if (!MusicWatcher.isSupported()) return;

        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        // Exactly over the three squares the card draws, so the card's own
        // controls are what gets clicked - scaled with the element, since the
        // HUD draws it scaled and these sit on the screen unscaled
        float scale = module.getScale();
        int x = module.getX(width) + Math.round(MusicModule.controlsX() * scale);
        int y = module.getY(height) + Math.round(MusicModule.controlsY() * scale);
        int size = Math.max(8, Math.round(MusicModule.CONTROL * scale));
        int step = Math.round((MusicModule.CONTROL + MusicModule.CONTROL_GAP) * scale);

        Runnable[] actions = {MusicWatcher::previous, MusicWatcher::playPause, MusicWatcher::next};
        for (int i = 0; i < 3; i++) {
            int kind = i;
            ScreenInjector.addWidget(screen, new FlatButton(
                    x + step * i, y, size, size, () -> "", () -> false, actions[i]) {
                @Override
                protected void extractContents(net.minecraft.client.gui.GuiGraphicsExtractor graphics,
                                               int mouseX, int mouseY, float delta) {
                    // Painted like the square underneath, only lit on hover.
                    // Filling the whole button also covers the vanilla sprite
                    // AbstractButton draws first.
                    var playing = module.track();
                    MusicModule.drawControl(graphics, getX(), getY(), this.width, kind,
                            playing.playing(), module.palette(playing), isHovered());
                }
            }.asAction());
        }
    }

    private MusicControls() {}
}
