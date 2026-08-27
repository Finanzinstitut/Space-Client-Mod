package gg.spaceclient.modules;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.ColorSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ClockModule extends HudModule {
    private final BooleanSetting showSeconds = new BooleanSetting(
            "show_seconds", "Show seconds", "Include seconds in the time", false);

    private final ColorSetting textColor = new ColorSetting(
            "text_color", "Text colour", "Colour of the clock", 0xFFFFFFFF);

    public ClockModule() {
        super("clock", "Clock", "Displays the current real-world time", 0.90f, 0.05f, false);
        addSettings(showSeconds, textColor);
    }

    @Override
    public int getWidth() { return mc.font.width(cached(this::text)); }

    @Override
    public int getHeight() { return mc.font.lineHeight; }

    private String text() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern(showSeconds.get() ? "HH:mm:ss" : "HH:mm");
        return LocalTime.now().format(fmt);
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.text(mc.font, cached(this::text), x, y, textColor.get(), true);
    }
}
