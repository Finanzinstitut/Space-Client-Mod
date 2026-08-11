package gg.spaceclient.modules.hud;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.ColorSetting;
import net.minecraft.client.gui.GuiGraphics;

public class CoordinatesModule extends HudModule {
    private final BooleanSetting showDirection = new BooleanSetting(
            "show_direction", "Show facing", "Append the direction you are looking", true);

    private final ColorSetting textColor = new ColorSetting(
            "text_color", "Text colour", "Colour of the readout", 0xFFFFFFFF);

    public CoordinatesModule() {
        super("coordinates", "Coordinates", "Shows your current coordinates", 0.02f, 0.10f);
        addSettings(showDirection, textColor);
    }

    private String text() {
        if (mc.player == null) return "-- -- --";
        String base = String.format("%.0f, %.0f, %.0f",
                mc.player.getX(), mc.player.getY(), mc.player.getZ());
        if (showDirection.get()) {
            base += " " + mc.player.getDirection().asString().toUpperCase();
        }
        return base;
    }

    @Override
    public int getWidth() { return mc.font.width(text()); }

    @Override
    public int getHeight() { return mc.font.lineHeight; }

    @Override
    public void render(GuiGraphics context, int x, int y) {
        context.drawString(mc.font, text(), x, y, textColor.get(), true);
    }
}
