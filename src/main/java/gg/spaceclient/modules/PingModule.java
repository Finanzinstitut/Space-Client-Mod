package gg.spaceclient.modules;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.ColorSetting;
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
    public int getWidth() { return mc.font.width(cached(this::text)); }

    @Override
    public int getHeight() { return mc.font.lineHeight; }

    private String text() {
        if (mc.player == null || mc.getConnection() == null) return "-- ms";
        PlayerInfo entry = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return entry != null ? entry.getLatency() + " ms" : "-- ms";
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.text(mc.font, cached(this::text), x, y, textColor.get(), true);
    }
}
