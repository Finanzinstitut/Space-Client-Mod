package gg.spaceclient.modules.hud;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.ColorSetting;

import net.minecraft.client.gui.DrawContext;

/**
 * Draws a mouse and lights up whichever button is being pressed.
 * The shape is drawn from primitives rather than a texture, so the colours stay
 * fully configurable and there is no asset to ship.
 */
public class MouseTrackerModule extends HudModule {
    private static final int BODY_W = 44;
    private static final int BODY_H = 64;

    private final ColorSetting pressedColor = new ColorSetting(
            "pressed_color", "Pressed colour", "Colour a button lights up in", 0xFF38E0FF);

    private final ColorSetting bodyColor = new ColorSetting(
            "body_color", "Body colour", "Colour of the mouse body", 0xB0101028);

    private final ColorSetting outlineColor = new ColorSetting(
            "outline_color", "Outline colour", "Colour of the outlines", 0xFF7C5CFF);

    private final BooleanSetting showCps = new BooleanSetting(
            "show_cps", "Show CPS", "Print the click rate under the mouse", true);

    private final BooleanSetting trackMovement = new BooleanSetting(
            "track_movement", "Show movement dot", "A dot inside the mouse follows your aim movement", true);

    // Smoothed aim delta, used for the movement dot
    private double dotX = 0;
    private double dotY = 0;
    private double lastYaw = 0;
    private double lastPitch = 0;

    public MouseTrackerModule() {
        super("mousetracker", "Mouse Tracker", "Input overlay that visualises your mouse", 0.02f, 0.30f);
        addSettings(pressedColor, bodyColor, outlineColor, showCps, trackMovement);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        // Movement is derived from how far the view turned this tick, which is
        // the closest we can get to raw mouse deltas without hooking the device.
        double yaw = mc.player.getYaw();
        double pitch = mc.player.getPitch();
        double deltaYaw = yaw - lastYaw;
        double deltaPitch = pitch - lastPitch;
        lastYaw = yaw;
        lastPitch = pitch;

        // Wrapping around 360 would otherwise fling the dot across the pad
        if (deltaYaw > 180) deltaYaw -= 360;
        if (deltaYaw < -180) deltaYaw += 360;

        dotX += deltaYaw * 0.6;
        dotY += deltaPitch * 0.6;

        // Spring back to centre so the dot settles when you stop moving
        dotX *= 0.75;
        dotY *= 0.75;

        double limit = BODY_W / 2.0 - 6;
        dotX = Math.max(-limit, Math.min(limit, dotX));
        dotY = Math.max(-limit, Math.min(limit, dotY));
    }

    private void outlineRect(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        context.fill(x1, y1, x2, y1 + 1, color);
        context.fill(x1, y2 - 1, x2, y2, color);
        context.fill(x1, y1, x1 + 1, y2, color);
        context.fill(x2 - 1, y1, x2, y2, color);
    }

    @Override
    public int getWidth() { return BODY_W; }

    @Override
    public int getHeight() { return showCps.get() ? BODY_H + 12 : BODY_H; }

    @Override
    public void render(DrawContext context, int x, int y) {
        boolean left = mc.options != null && mc.options.attackKey.isPressed();
        boolean right = mc.options != null && mc.options.useKey.isPressed();
        boolean middle = false; // reserved: no vanilla binding maps to MMB by default

        int body = bodyColor.get();
        int outline = outlineColor.get();
        int lit = pressedColor.get();

        int splitY = y + 26;      // where the buttons end and the body begins
        int midX1 = x + BODY_W / 2 - 5;
        int midX2 = x + BODY_W / 2 + 5;

        // Body
        context.fill(x, y, x + BODY_W, y + BODY_H, body);

        // Left button
        context.fill(x + 1, y + 1, midX1, splitY, left ? lit : body);
        // Right button
        context.fill(midX2, y + 1, x + BODY_W - 1, splitY, right ? lit : body);
        // Scroll wheel
        context.fill(midX1 + 1, y + 6, midX2 - 1, y + 22, middle ? lit : outline);

        // Outlines last so they sit on top of the fills
        outlineRect(context, x, y, x + BODY_W, y + BODY_H, outline);
        outlineRect(context, x + 1, y + 1, midX1, splitY, outline);
        outlineRect(context, midX2, y + 1, x + BODY_W - 1, splitY, outline);
        context.fill(x + 1, splitY, x + BODY_W - 1, splitY + 1, outline);

        // Movement dot
        if (trackMovement.get()) {
            int centerX = x + BODY_W / 2;
            int centerY = splitY + (BODY_H - 26) / 2;
            int dx = centerX + (int) dotX;
            int dy = centerY + (int) dotY;
            context.fill(dx - 2, dy - 2, dx + 2, dy + 2, lit);
        }

        if (showCps.get()) {
            CpsModule cps = CpsModule.getInstance();
            String text = cps != null
                    ? cps.getLeftCps() + " | " + cps.getRightCps() + " CPS"
                    : "0 | 0 CPS";
            int textX = x + (BODY_W - mc.textRenderer.getWidth(text)) / 2;
            context.drawText(mc.textRenderer, text, textX, y + BODY_H + 3, 0xFFFFFFFF, true);
        }
    }
}
