package gg.spaceclient.modules;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.ColorSetting;
import gg.spaceclient.ui.Rolling;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class FpsModule extends HudModule {

    /**
     * The displayed figure trails the real one.
     *
     * Frames per second changes every frame, so printing it raw is a flicker
     * rather than a number - the eye follows the movement and never lands on a
     * value. Easing settles the last digits and makes it readable while it is
     * still moving.
     */
    private final Rolling shown = new Rolling();

    private final ColorSetting textColor = new ColorSetting(
            "text_color", "Text colour", "Colour of the counter", 0xFFFFFFFF);

    public FpsModule() {
        super("fps", "FPS", "Shows your current frames per second", 0.02f, 0.05f, true);
        addSettings(textColor);
    }

    @Override
    public int getWidth() { return mc.font.width(shown.value() + " FPS"); }

    @Override
    public int getHeight() { return mc.font.lineHeight; }

    @Override
    public void render(GuiGraphicsExtractor graphics, int x, int y) {
        int value = shown.update(mc.getFps());
        graphics.text(mc.font, value + " FPS", x, y, textColor.get(), true);
    }
}
