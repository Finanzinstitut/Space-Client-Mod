package gg.spaceclient.modules.hud;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.ColorSetting;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Movement speed in blocks per second.
 *
 * The twist: it shows horizontal speed as a percentage of your theoretical
 * maximum for the current state (walking, sprinting, sprint-jumping), so you can
 * see directly whether your movement technique is actually efficient rather than
 * just reading a raw number.
 */
public class SpeedometerModule extends HudModule {
    private final BooleanSetting showEfficiency = new BooleanSetting(
            "show_efficiency", "Show efficiency", "Compare against the theoretical maximum", true);

    private final BooleanSetting includeVertical = new BooleanSetting(
            "include_vertical", "Include vertical", "Count falling and flying speed too", false);

    private final ColorSetting textColor = new ColorSetting(
            "text_color", "Text colour", "Colour of the readout", 0xFFFFFFFF);

    private double lastX, lastY, lastZ;
    private double speed = 0;

    public SpeedometerModule() {
        super("speedometer", "Speedometer", "Displays your in-game speed in m/s", 0.02f, 0.45f);
        addSettings(showEfficiency, includeVertical, textColor);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        double dx = mc.player.getX() - lastX;
        double dy = mc.player.getY() - lastY;
        double dz = mc.player.getZ() - lastZ;

        double distance = includeVertical.get()
                ? Math.sqrt(dx * dx + dy * dy + dz * dz)
                : Math.sqrt(dx * dx + dz * dz);

        // 20 ticks per second, smoothed so the readout does not flicker
        speed = speed * 0.6 + (distance * 20) * 0.4;

        lastX = mc.player.getX();
        lastY = mc.player.getY();
        lastZ = mc.player.getZ();
    }

    /** Rough theoretical maxima for the player's current state. */
    private double theoreticalMax() {
        if (mc.player == null) return 5.6;
        if (mc.player.isFallFlying()) return 33.0;
        if (mc.player.getAbilities().flying) return 10.9;
        if (mc.player.isSprinting()) return mc.player.onGround() ? 5.6 : 7.1;
        return 4.3;
    }

    private String text() {
        String base = String.format("%.1f m/s", speed);
        if (showEfficiency.get()) {
            double pct = Math.min(999, (speed / theoreticalMax()) * 100);
            base += String.format("  %.0f%%", pct);
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
