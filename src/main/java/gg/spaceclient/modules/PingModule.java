package gg.spaceclient.modules;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.ColorSetting;
import gg.spaceclient.ui.Rolling;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;

public class PingModule extends HudModule {
    private final ColorSetting textColor = new ColorSetting(
            "text_color", "Text colour", "Colour of the readout", 0xFFFFFFFF);

    public PingModule() {
        super("ping", "Ping", "Shows your ping to the server", 0.02f, 0.20f, false);
        addSettings(textColor);
    }

    @Override
    public int getWidth() { return mc.font.width(text()); }

    @Override
    public int getHeight() { return mc.font.lineHeight; }

    /**
     * Not cached any more, and not for style.
     *
     * cachedText rebuilds on a timer, which would step the number in jumps and
     * undo the easing. The animation needs a value every frame; the reading it
     * animates toward still only changes when the server says so.
     */
    private final Rolling shown = new Rolling();

    private int latency() {
        if (mc.player == null || mc.getConnection() == null) return -1;
        PlayerInfo entry = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return entry != null ? entry.getLatency() : -1;
    }

    private String text() {
        int ping = latency();
        if (ping < 0) {
            shown.reset();
            return "-- ms";
        }
        return shown.update(ping) + " ms";
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.text(mc.font, text(), x, y, textColor.get(), true);
    }
}
