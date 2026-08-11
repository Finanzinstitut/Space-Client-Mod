package gg.spaceclient.modules.hud;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.ColorSetting;
import net.minecraft.client.gui.DrawContext;

public class FpsModule extends HudModule {
    private final ColorSetting textColor = new ColorSetting(
            "text_color", "Text colour", "Colour of the counter", 0xFFFFFFFF);

    public FpsModule() {
        super("fps", "FPS", "Shows your current frames per second", 0.02f, 0.05f);
        addSettings(textColor);
    }

    private String text() {
        return mc.getCurrentFps() + " FPS";
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
