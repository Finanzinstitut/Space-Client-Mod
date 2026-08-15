package gg.spaceclient.ui;

import gg.spaceclient.setting.ColorSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * A hue/saturation wheel with a brightness bar beside it.
 *
 * Picking a colour by eye beats typing a hex code, so the wheel is the primary
 * control and the numeric value is only shown for reference.
 *
 * It extends Button purely to get click handling; the cursor position is
 * remembered while drawing, because the press callback carries no coordinates.
 */
public class ColorWheel extends Button {
    private static final int STEP = 2;
    private static final int BAR_W = 14;
    private static final int PADDING = 8;

    private final ColorSetting setting;
    private final Runnable onChange;
    private final int size;

    private int lastMouseX;
    private int lastMouseY;

    private float brightness = 1.0f;

    public ColorWheel(int x, int y, int size, ColorSetting setting, Runnable onChange) {
        // The callback receives the button, so the wheel can read the cursor
        // position it recorded while drawing.
        super(x, y, size + BAR_W + PADDING, size, Component.empty(),
                btn -> ((ColorWheel) btn).applyFromCursor(), DEFAULT_NARRATION);
        this.size = size;
        this.setting = setting;
        this.onChange = onChange;
        this.brightness = brightnessOf(setting.get());
    }

    private static float brightnessOf(int argb) {
        int max = Math.max(Math.max((argb >> 16) & 0xFF, (argb >> 8) & 0xFF), argb & 0xFF);
        return Math.max(0.15f, max / 255f);
    }

    /** Applies whatever sits under the cursor. Called from the press callback. */
    public void applyFromCursor() {
        int wheelX = getX();
        int wheelY = getY();
        int radius = size / 2;
        int cx = wheelX + radius;
        int cy = wheelY + radius;

        int barX = wheelX + size + PADDING;

        // Brightness bar
        if (lastMouseX >= barX && lastMouseX <= barX + BAR_W) {
            float pct = 1.0f - (lastMouseY - wheelY) / (float) size;
            brightness = Math.max(0.05f, Math.min(1.0f, pct));
            recolour(currentHue(), currentSaturation());
            return;
        }

        double dx = lastMouseX - cx;
        double dy = lastMouseY - cy;
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance > radius) return;

        float hue = (float) ((Math.toDegrees(Math.atan2(dy, dx)) + 360) % 360) / 360f;
        float saturation = (float) (distance / radius);
        recolour(hue, saturation);
    }

    private float currentHue() {
        return toHsb(setting.get())[0];
    }

    private float currentSaturation() {
        return toHsb(setting.get())[1];
    }

    private void recolour(float hue, float saturation) {
        int rgb = fromHsb(hue, saturation, brightness);
        setting.setComponents(setting.getAlpha(),
                (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
        onChange.run();
    }

    // --- colour maths, written out rather than pulled from java.awt, which
    // lives in a module that is not guaranteed to be on the runtime image ---

    /** @return hue, saturation and brightness, each 0 to 1 */
    private static float[] toHsb(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;

        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;

        float hue = 0;
        if (delta > 0.0001f) {
            if (max == r) hue = ((g - b) / delta) % 6;
            else if (max == g) hue = (b - r) / delta + 2;
            else hue = (r - g) / delta + 4;
            hue /= 6;
            if (hue < 0) hue += 1;
        }
        float saturation = max <= 0.0001f ? 0 : delta / max;
        return new float[]{hue, saturation, max};
    }

    private static int fromHsb(float hue, float saturation, float value) {
        float h = (hue % 1f) * 6f;
        int sector = (int) Math.floor(h);
        float f = h - sector;

        float p = value * (1 - saturation);
        float q = value * (1 - saturation * f);
        float t = value * (1 - saturation * (1 - f));

        float r, g, b;
        switch (sector % 6) {
            case 0 -> { r = value; g = t; b = p; }
            case 1 -> { r = q; g = value; b = p; }
            case 2 -> { r = p; g = value; b = t; }
            case 3 -> { r = p; g = q; b = value; }
            case 4 -> { r = t; g = p; b = value; }
            default -> { r = value; g = p; b = q; }
        }
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        int wheelX = getX();
        int wheelY = getY();
        int radius = size / 2;
        int cx = wheelX + radius;
        int cy = wheelY + radius;

        // The disc is drawn as small squares: hue around, saturation outward
        for (int py = 0; py < size; py += STEP) {
            for (int px = 0; px < size; px += STEP) {
                double dx = px - radius;
                double dy = py - radius;
                double distance = Math.sqrt(dx * dx + dy * dy);
                if (distance > radius) continue;

                float hue = (float) ((Math.toDegrees(Math.atan2(dy, dx)) + 360) % 360) / 360f;
                float saturation = (float) (distance / radius);
                int rgb = fromHsb(hue, saturation, brightness);

                graphics.fill(wheelX + px, wheelY + py,
                        wheelX + px + STEP, wheelY + py + STEP, 0xFF000000 | rgb);
            }
        }

        // Marker on the currently selected colour
        float[] hsb = toHsb(setting.get());
        double angle = hsb[0] * 2 * Math.PI;
        int markerX = cx + (int) (Math.cos(angle) * hsb[1] * radius);
        int markerY = cy + (int) (Math.sin(angle) * hsb[1] * radius);
        graphics.fill(markerX - 3, markerY - 1, markerX + 3, markerY + 1, 0xFFFFFFFF);
        graphics.fill(markerX - 1, markerY - 3, markerX + 1, markerY + 3, 0xFFFFFFFF);

        // Brightness bar
        int barX = wheelX + size + PADDING;
        for (int py = 0; py < size; py += STEP) {
            float value = 1.0f - py / (float) size;
            int rgb = fromHsb(hsb[0], hsb[1], value);
            graphics.fill(barX, wheelY + py, barX + BAR_W, wheelY + py + STEP, 0xFF000000 | rgb);
        }

        int handleY = wheelY + (int) ((1.0f - brightness) * size);
        graphics.fill(barX - 2, handleY - 1, barX + BAR_W + 2, handleY + 1, 0xFFFFFFFF);
    }
}
