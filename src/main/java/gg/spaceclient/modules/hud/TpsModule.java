package gg.spaceclient.modules.hud;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.ColorSetting;
import net.minecraft.client.gui.DrawContext;

/**
 * Estimates server ticks per second from how fast world time advances.
 *
 * The addition: a small sparkline of the last thirty samples, so a server that
 * is stuttering in bursts is obvious, where a single averaged number would just
 * read "19.4" and look fine.
 */
public class TpsModule extends HudModule {
    private static final int HISTORY = 30;

    private final BooleanSetting showGraph = new BooleanSetting(
            "show_graph", "Show graph", "Draw a sparkline of recent TPS", true);

    private final ColorSetting textColor = new ColorSetting(
            "text_color", "Text colour", "Colour of the readout", 0xFFFFFFFF);

    private final float[] history = new float[HISTORY];
    private int historyIndex = 0;

    private long lastSampleTime = 0;
    private long lastWorldTime = -1;
    private float tps = 20.0f;

    public TpsModule() {
        super("tps", "TPS", "Displays the server's ticks per second", 0.02f, 0.35f);
        addSettings(showGraph, textColor);
    }

    @Override
    public void onTick() {
        if (mc.world == null) return;

        long now = System.currentTimeMillis();
        long worldTime = mc.world.getTime();

        if (lastWorldTime < 0 || now - lastSampleTime < 1000) {
            if (lastWorldTime < 0) {
                lastWorldTime = worldTime;
                lastSampleTime = now;
            }
            return;
        }

        long ticksPassed = worldTime - lastWorldTime;
        double seconds = (now - lastSampleTime) / 1000.0;

        if (seconds > 0 && ticksPassed >= 0) {
            // Clamp: a chunk reload can briefly make this nonsense
            tps = (float) Math.min(20.0, ticksPassed / seconds);
            history[historyIndex % HISTORY] = tps;
            historyIndex++;
        }

        lastWorldTime = worldTime;
        lastSampleTime = now;
    }

    private String text() {
        return String.format("%.1f TPS", tps);
    }

    private int colorForTps(float value) {
        if (value >= 19.0f) return 0xFF4ADE80;
        if (value >= 15.0f) return 0xFFFFD9A0;
        return 0xFFFF6B81;
    }

    @Override
    public int getWidth() { return Math.max(60, mc.textRenderer.getWidth(text())); }

    @Override
    public int getHeight() { return mc.textRenderer.fontHeight + (showGraph.get() ? 16 : 0); }

    @Override
    public void render(DrawContext context, int x, int y) {
        context.drawText(mc.textRenderer, text(), x, y, colorForTps(tps), true);

        if (!showGraph.get()) return;

        int graphY = y + mc.textRenderer.fontHeight + 2;
        int graphH = 12;
        context.fill(x, graphY, x + HISTORY * 2, graphY + graphH, 0x40000000);

        for (int i = 0; i < HISTORY; i++) {
            float value = history[(historyIndex + i) % HISTORY];
            if (value <= 0) continue;
            int barH = Math.max(1, (int) (graphH * (value / 20.0f)));
            int barX = x + i * 2;
            context.fill(barX, graphY + graphH - barH, barX + 2, graphY + graphH, colorForTps(value));
        }
    }
}
