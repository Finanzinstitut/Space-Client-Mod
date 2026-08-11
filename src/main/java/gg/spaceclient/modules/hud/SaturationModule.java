package gg.spaceclient.modules.hud;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.ColorSetting;
import net.minecraft.client.gui.DrawContext;

/**
 * Saturation, the hidden stat behind the hunger bar.
 *
 * Beyond showing the number, this predicts how many minutes of sprinting you
 * have left before hunger actually starts dropping - the thing saturation is
 * for. No client I know of turns it into a usable estimate.
 */
public class SaturationModule extends HudModule {
    private final BooleanSetting showEstimate = new BooleanSetting(
            "show_estimate", "Show sprint estimate", "Estimate remaining sprint time", true);

    private final ColorSetting textColor = new ColorSetting(
            "text_color", "Text colour", "Colour of the readout", 0xFFFFD9A0);

    public SaturationModule() {
        super("saturation", "Saturation", "Shows saturation and what it buys you", 0.02f, 0.40f);
        addSettings(showEstimate, textColor);
    }

    private String text() {
        if (mc.player == null) return "-.- sat";

        float saturation = mc.player.getHungerManager().getSaturationLevel();
        String base = String.format("%.1f sat", saturation);

        if (showEstimate.get()) {
            // Sprinting costs 0.1 exhaustion per metre, roughly 5.6 m/s.
            // 4 exhaustion consumes 1 saturation point.
            float exhaustionPerSecond = 0.1f * 5.6f;
            float secondsOfSprint = (saturation * 4.0f) / exhaustionPerSecond;
            base += String.format("  ~%.0fs sprint", secondsOfSprint);
        }
        return base;
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
