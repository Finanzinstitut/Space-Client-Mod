package gg.spaceclient.modules;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.ColorSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class FpsModule extends HudModule {
    private final ColorSetting textColor = new ColorSetting(
            "text_color", "Text colour", "Colour of the counter", 0xFFFFFFFF);

    public FpsModule() {
        super("fps", "FPS", "Shows your current frames per second", 0.02f, 0.05f, true);
        addSettings(textColor);
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.text(mc.font, mc.getFps() + " FPS", x, y, textColor.get(), true);
    }
}
