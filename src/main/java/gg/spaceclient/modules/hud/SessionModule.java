package gg.spaceclient.modules.hud;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.ColorSetting;
import net.minecraft.client.gui.DrawContext;

/**
 * Session uptime and in-game day count in one element.
 *
 * The part that is genuinely new: an optional break reminder. After a
 * configurable number of hours it starts showing a gentle prompt in the corner.
 * Playtime trackers are common; one that actually nudges you is not.
 */
public class SessionModule extends HudModule {
    private final BooleanSetting showDay = new BooleanSetting(
            "show_day", "Show in-game day", "Include the world's day number", true);

    private final BooleanSetting breakReminder = new BooleanSetting(
            "break_reminder", "Break reminder", "Show a reminder after a few hours", true);

    private final ColorSetting textColor = new ColorSetting(
            "text_color", "Text colour", "Colour of the readout", 0xFFFFFFFF);

    private final long startedAt = System.currentTimeMillis();
    private static final long REMINDER_AFTER_MS = 2L * 60 * 60 * 1000; // two hours

    public SessionModule() {
        super("session", "Session", "Uptime, in-game day and an optional break reminder", 0.85f, 0.05f);
        addSettings(showDay, breakReminder, textColor);
    }

    private long elapsedMs() {
        return System.currentTimeMillis() - startedAt;
    }

    private String text() {
        long elapsed = elapsedMs() / 1000;
        String base = String.format("%d:%02d:%02d", elapsed / 3600, (elapsed % 3600) / 60, elapsed % 60);

        if (showDay.get() && mc.world != null) {
            base += "  Day " + (mc.world.getTimeOfDay() / 24000L);
        }
        return base;
    }

    private boolean reminderDue() {
        return breakReminder.get() && elapsedMs() > REMINDER_AFTER_MS;
    }

    @Override
    public int getWidth() { return Math.max(90, mc.textRenderer.getWidth(text())); }

    @Override
    public int getHeight() { return mc.textRenderer.fontHeight * (reminderDue() ? 2 : 1) + 2; }

    @Override
    public void render(DrawContext context, int x, int y) {
        context.drawText(mc.textRenderer, text(), x, y, textColor.get(), true);

        if (reminderDue()) {
            long hours = elapsedMs() / (60 * 60 * 1000);
            String note = hours + "h - time for a break?";
            context.drawText(mc.textRenderer, note, x, y + mc.textRenderer.fontHeight + 2, 0xFF38E0FF, true);
        }
    }
}
