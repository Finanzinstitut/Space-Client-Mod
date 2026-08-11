package gg.spaceclient.modules;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.ColorSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class CoordinatesModule extends HudModule {
    private final ColorSetting textColor = new ColorSetting(
            "text_color", "Text colour", "Colour of the readout", 0xFFFFFFFF);

    public CoordinatesModule() {
        super("coordinates", "Coordinates", "Shows your current coordinates", 0.02f, 0.15f, true);
        addSettings(textColor);
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int x, int y) {
        if (mc.player == null) return;
        String text = String.format("%.0f, %.0f, %.0f",
                mc.player.getX(), mc.player.getY(), mc.player.getZ());
        graphics.text(mc.font, text, x, y, textColor.get(), true);
    }
}
