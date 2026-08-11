package gg.spaceclient.modules.hud;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.ColorSetting;
import net.minecraft.client.gui.DrawContext;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ClockModule extends HudModule {
    private final BooleanSetting showSeconds = new BooleanSetting(
            "show_seconds", "Show seconds", "Include seconds in the time", false);

    private final ColorSetting textColor = new ColorSetting(
            "text_color", "Text colour", "Colour of the clock", 0xFFFFFFFF);

    public ClockModule() {
        super("clock", "Clock", "Displays the current real-world time", 0.90f, 0.05f);
        addSettings(showSeconds, textColor);
    }

    private String text() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern(showSeconds.get() ? "HH:mm:ss" : "HH:mm");
        return LocalTime.now().format(fmt);
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
