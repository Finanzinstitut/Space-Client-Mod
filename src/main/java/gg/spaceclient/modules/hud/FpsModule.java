package gg.spaceclient.modules.hud;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.ColorSetting;
import net.minecraft.client.gui.GuiGraphics;

public class FpsModule extends HudModule {
    private final ColorSetting textColor = new ColorSetting(
            "text_color", "Text colour", "Colour of the counter", 0xFFFFFFFF);

    public FpsModule() {
        super("fps", "FPS", "Shows your current frames per second", 0.02f, 0.05f);
        addSettings(textColor);
    }

    private String text() {
        return mc.getFps() + " FPS";
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
