package gg.spaceclient.modules;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.ColorSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Session uptime with an optional break reminder after a few hours.
 * Playtime trackers are common; one that actually nudges you is not.
 */
public class SessionModule extends HudModule {
    private static final long REMINDER_AFTER_MS = 2L * 60 * 60 * 1000;

    private final BooleanSetting breakReminder = new BooleanSetting(
            "break_reminder", "Break reminder", "Show a reminder after two hours", true);

    private final ColorSetting textColor = new ColorSetting(
            "text_color", "Text colour", "Colour of the readout", 0xFFFFFFFF);

    private final long startedAt = System.currentTimeMillis();

    public SessionModule() {
        super("session", "Session", "Uptime and an optional break reminder", 0.90f, 0.10f, false);
        addSettings(breakReminder, textColor);
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int x, int y) {
        long elapsedMs = System.currentTimeMillis() - startedAt;
        long elapsed = elapsedMs / 1000;
        String text = String.format("%d:%02d:%02d",
                elapsed / 3600, (elapsed % 3600) / 60, elapsed % 60);
        graphics.text(mc.font, text, x, y, textColor.get(), true);

        if (breakReminder.get() && elapsedMs > REMINDER_AFTER_MS) {
            long hours = elapsedMs / (60 * 60 * 1000);
            graphics.text(mc.font, hours + "h - time for a break?",
                    x, y + mc.font.lineHeight + 2, 0xFF38E0FF, true);
        }
    }
}
