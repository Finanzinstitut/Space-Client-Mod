package gg.spaceclient.ui;

import gg.spaceclient.setting.ColorSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * A hue/saturation wheel with a brightness bar beside it.
 *
 * Picking a colour by eye beats typing a hex code, so the wheel is the primary
 * control and the numeric value is only shown for reference.
 *
 * It extends Button purely to get click handling; the cursor position is
 * remembered while drawing, because the press callback carries no coordinates.
 *
 * Clicking does not set a colour and stop. It starts tracking: the colour then
 * follows the pointer until a second click keeps it. Single clicks made picking
 * a shade a matter of aiming, which is the wrong gesture for a continuous
 * value - you want to see the thing change as you look for it.
 *
 * The cursor is not literally captured. Grabbing it needs the mouse handler,
 * whose API changed in this version, and capturing it would achieve nothing the
 * tracking does not: while tracking, pointer movement is the only thing that
 * matters and where the pointer happens to sit does not. The same reason the
 * HUD editor picks up and puts down rather than dragging.
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

    /** True while the colour follows the pointer. */
    private boolean tracking = false;

    /** Eased toward the pointer, so the colour arrives rather than jumps. */
    private float shownHue;
    private float shownSaturation;
    private float shownBrightness;
    private boolean primed = false;

    public ColorWheel(int x, int y, int size, ColorSetting setting, Runnable onChange) {
        // The callback receives the button, so the wheel can read the cursor
        // position it recorded while drawing.
        super(x, y, size + BAR_W + PADDING, size, Component.empty(),
                btn -> ((ColorWheel) btn).toggleTracking(), DEFAULT_NARRATION);
        this.size = size;
        this.setting = setting;
        this.onChange = onChange;
        this.brightness = brightnessOf(setting.get());
    }

    private static float brightnessOf(int argb) {
        int max = Math.max(Math.max((argb >> 16) & 0xFF, (argb >> 8) & 0xFF), argb & 0xFF);
        return Math.max(0.15f, max / 255f);
    }

    /**
     * Starts or finishes tracking.
     *
     * Saving happens on the way out rather than on every frame: writing the
     * config file sixty times a second while somebody sweeps across the wheel
     * would be a lot of disk for one decision.
     */
    public void toggleTracking() {
        tracking = !tracking;
        if (!tracking) onChange.run();
    }

    public boolean isTracking() { return tracking; }

    /** Reads the target under the cursor. Does not write anything. */
    private void trackCursor() {
        int wheelX = getX();
        int wheelY = getY();
        int radius = size / 2;
        int cx = wheelX + radius;
        int cy = wheelY + radius;

        int barX = wheelX + size + PADDING;

        // Beside the wheel means the pointer is setting brightness
        if (lastMouseX >= barX - 4) {
            float pct = 1.0f - (lastMouseY - wheelY) / (float) size;
            targetBrightness = Math.max(0.05f, Math.min(1.0f, pct));
            return;
        }

        double dx = lastMouseX - cx;
        double dy = lastMouseY - cy;
        double distance = Math.sqrt(dx * dx + dy * dy);

        // Beyond the edge the hue still follows the angle, clamped to full
        // saturation. Stopping at the rim would make the outer colours the
        // hardest to reach, when they are the ones people go looking for.
        targetHue = (float) ((Math.toDegrees(Math.atan2(dy, dx)) + 360) % 360) / 360f;
        targetSaturation = (float) Math.min(1.0, distance / radius);
    }

    private float targetHue;
    private float targetSaturation;
    private float targetBrightness = 1.0f;

    /**
     * Moves the shown colour toward the target.
     *
     * Hue wraps, so it is eased the short way round: without this, dragging
     * from just above red to just below it would sweep the whole spectrum
     * backwards instead of crossing a boundary that is not really there.
     */
    private void ease(float delta) {
        float difference = targetHue - shownHue;
        if (difference > 0.5f) shownHue += 1f;
        else if (difference < -0.5f) shownHue -= 1f;

        shownHue = Ease.approach(shownHue, targetHue, 0.4f, delta);
        if (shownHue < 0f) shownHue += 1f;
        if (shownHue > 1f) shownHue -= 1f;

        shownSaturation = Ease.approach(shownSaturation, targetSaturation, 0.4f, delta);
        shownBrightness = Ease.approach(shownBrightness, targetBrightness, 0.4f, delta);

        brightness = shownBrightness;
        recolour(shownHue, shownSaturation);
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

        if (!primed) {
            float[] start = toHsb(setting.get());
            shownHue = targetHue = start[0];
            shownSaturation = targetSaturation = start[1];
            shownBrightness = targetBrightness = brightness;
            primed = true;
        }

        if (tracking) {
            trackCursor();
            ease(delta);
        }

        int wheelX = getX();
        int wheelY = getY();
        int radius = size / 2;
        int cx = wheelX + radius;
        int cy = wheelY + radius;

        // The disc comes from a texture made once per brightness step. Drawing
        // it as small squares meant some 1800 fills and as many colour
        // conversions per wheel per frame - with four or five colour settings
        // on one page (Now Playing has that many) the settings screen alone
        // was enough to stutter a weaker machine.
        Identifier disc = discTexture(brightness);
        if (disc == null || !Textures.draw(graphics, disc, wheelX, wheelY, size, size)) {
            drawDiscBySquares(graphics, wheelX, wheelY, radius);
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

        if (tracking) {
            // A frame around the whole control, so it is obvious the pointer is
            // driving something and that another click ends it
            int right = barX + BAR_W + 2;
            int bottom = wheelY + size + 2;
            int accent = Theme.accent();
            graphics.fill(wheelX - 3, wheelY - 3, right + 1, wheelY - 2, accent);
            graphics.fill(wheelX - 3, bottom, right + 1, bottom + 1, accent);
            graphics.fill(wheelX - 3, wheelY - 3, wheelX - 2, bottom + 1, accent);
            graphics.fill(right, wheelY - 3, right + 1, bottom + 1, accent);

            Minecraft mc = Minecraft.getInstance();
            graphics.text(mc.font, "move to pick, click to keep",
                    wheelX - 3, bottom + 4, Theme.TEXT_DIM, false);
        }
    }

    /** Brightness steps a disc texture is made for; finer than the eye can tell apart. */
    private static final int LEVELS = 48;
    private static final int TEXTURE = 96;
    private static final Identifier[] DISCS = new Identifier[LEVELS + 1];
    private static boolean texturesFailed = false;

    private static Identifier discTexture(float brightness) {
        if (texturesFailed) return null;
        int level = Math.round(Math.max(0f, Math.min(1f, brightness)) * LEVELS);
        if (DISCS[level] != null) return DISCS[level];
        try {
            com.mojang.blaze3d.platform.NativeImage image =
                    new com.mojang.blaze3d.platform.NativeImage(TEXTURE, TEXTURE, false);
            float value = level / (float) LEVELS;
            double radius = TEXTURE / 2.0;
            for (int y = 0; y < TEXTURE; y++) {
                for (int x = 0; x < TEXTURE; x++) {
                    double dx = x + 0.5 - radius;
                    double dy = y + 0.5 - radius;
                    double distance = Math.sqrt(dx * dx + dy * dy);
                    // One pixel of soft edge instead of a staircase
                    double alpha = Math.max(0, Math.min(1, radius - distance));
                    if (alpha <= 0) {
                        image.setPixel(x, y, 0);
                        continue;
                    }
                    float hue = (float) ((Math.toDegrees(Math.atan2(dy, dx)) + 360) % 360) / 360f;
                    float saturation = (float) Math.min(1, distance / radius);
                    int rgb = fromHsb(hue, saturation, value);
                    image.setPixel(x, y, ((int) Math.round(alpha * 255) << 24) | rgb);
                }
            }
            Identifier id = Identifier.fromNamespaceAndPath("spaceclient", "colour_wheel/" + level);
            var texture = new net.minecraft.client.renderer.texture.DynamicTexture(() -> "Space Client colour wheel", image);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            DISCS[level] = id;
            return id;
        } catch (Throwable t) {
            texturesFailed = true;
            gg.spaceclient.SpaceClient.LOGGER.warn("Colour wheel texture unavailable, drawing it by hand", t);
            return null;
        }
    }

    /** The old way, kept for a version where the texture cannot be made. */
    private void drawDiscBySquares(GuiGraphicsExtractor graphics, int wheelX, int wheelY, int radius) {
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
    }
}
