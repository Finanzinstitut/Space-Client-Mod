package gg.spaceclient.modules.hud;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.ColorSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;

public class PingModule extends HudModule {
    private final ColorSetting textColor = new ColorSetting(
            "text_color", "Text colour", "Colour of the readout", 0xFFFFFFFF);

    public PingModule() {
        super("ping", "Ping", "Shows your ping to the server", 0.02f, 0.15f);
        addSettings(textColor);
    }

    private String text() {
        if (mc.player == null || mc.getNetworkHandler() == null) return "-- ms";
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return entry != null ? entry.getLatency() + " ms" : "-- ms";
    }

    @Override
    public int getWidth() { return mc.textRenderer.getWidth(text()); }

    @Override
    public int getHeight() { return mc.textRenderer.fontHeight; }

    @Override
    public void render(DrawContext context, int x, int y) {
        context.drawText(mc.textRenderer, text(), x, y, textColor.get(), true);
    }
}
