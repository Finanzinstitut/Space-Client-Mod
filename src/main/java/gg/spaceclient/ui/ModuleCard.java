package gg.spaceclient.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * One module in the menu's grid: name, what it does, a switch, and a way into
 * its settings.
 *
 * This replaces the single-column list the menu used to be. A list puts every
 * module on its own line, so twenty-six modules are a page and a half of
 * scrolling whatever the window size; a grid uses the width the panel already
 * has and shows most of the client at once. That is the whole point - a menu
 * you can see the shape of is one you can find things in without searching.
 *
 * <h2>Clipping</h2>
 *
 * A card knows the band it is allowed to paint in and drops anything outside
 * it. That is what makes smooth scrolling safe here: the grid moves by pixels,
 * so at any moment two rows of cards are half out of the viewport, and without
 * clipping they would paint across the header and the footer. Covering the
 * overflow afterwards does not work, because the panel underneath is not fully
 * opaque and a strip drawn over it is as see-through as what it is hiding.
 *
 * <h2>Why hover is worked out here</h2>
 *
 * isHovered() is the widget system's answer and it only knows the rectangle.
 * A card scrolled up behind the header still occupies its rectangle, so the
 * widget system would report it hovered while the pointer is over the search
 * field. Hover and clickability are therefore both decided against the clip
 * band as well as the rectangle.
 */
public class ModuleCard extends Button {

    /** The size a card wants. The grid gives it the width that actually fits. */
    public static final int WIDTH = 168;
    public static final int HEIGHT = 52;

    private static final float HOVER_SPEED = 0.30f;
    private static final float STATE_SPEED = 0.22f;

    /** The switch on the right. */
    private static final int PILL_W = 24;
    private static final int PILL_H = 12;

    private final Supplier<String> label;
    private final String description;
    private final BooleanSupplier active;
    private final boolean hasSettings;

    private float hover = 0f;
    private float state = 0f;

    /** How far this card has arrived in the grid's staggered entrance. */
    private float appear = 0f;

    private int clipTop = Glass.NO_CLIP_TOP;
    private int clipBottom = Glass.NO_CLIP_BOTTOM;

    private int lastMouseX = 0;
    private int lastMouseY = 0;

    public ModuleCard(int x, int y, int width, int height,
                      Supplier<String> label, String description,
                      BooleanSupplier active, boolean hasSettings,
                      Runnable onPress) {
        // Guarded in the handler rather than by clearing the widget's enabled
        // flag: that flag's name on this version has not been proven by a
        // compile, and the handler is given the button anyway.
        super(x, y, width, height, Component.empty(),
                btn -> { if (((ModuleCard) btn).pressable()) onPress.run(); },
                DEFAULT_NARRATION);
        this.label = label;
        this.description = description == null ? "" : description;
        this.active = active;
        this.hasSettings = hasSettings;
        this.state = active.getAsBoolean() ? 1f : 0f;
    }

    public void setClip(int top, int bottom) {
        this.clipTop = top;
        this.clipBottom = bottom;
    }

    public void setAppear(float value) {
        this.appear = Ease.clamp01(value);
    }

    /** True when a click here should count: inside the card and inside the band. */
    public boolean pressable() {
        return appear > 0.5f
                && lastMouseY >= clipTop && lastMouseY < clipBottom
                && lastMouseX >= getX() && lastMouseX < getX() + width
                && lastMouseY >= getY() && lastMouseY < getY() + height;
    }

    /**
     * True when the pointer is over the settings dots rather than the body.
     *
     * One press handler and the pointer's last position decides what the click
     * meant, which is how the rest of this package avoids the mouse event
     * signatures that changed in this version.
     */
    public boolean overGear() {
        return hasSettings
                && lastMouseX >= getX() + width - 30
                && lastMouseY >= getY() + height - 20;
    }

    /** Pointed at, not tabbed to, like every other widget in this menu. */
    @Override
    public void setFocused(boolean focused) { super.setFocused(false); }

    @Override
    public boolean isFocused() { return false; }

    public net.minecraft.client.gui.ComponentPath nextFocusPath(
            net.minecraft.client.gui.navigation.FocusNavigationEvent event) {
        return null;
    }

    /** Scales a colour's alpha, so a card can fade in without a second palette. */
    private static int fade(int argb, float amount) {
        int alpha = Math.round(((argb >>> 24) & 0xFF) * Ease.clamp01(amount));
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        if (appear <= 0.01f) return;

        boolean over = pressable();
        hover = Ease.approach(hover, over ? 1f : 0f, HOVER_SPEED, delta);
        state = Ease.approach(state, active.getAsBoolean() ? 1f : 0f, STATE_SPEED, delta);

        Font font = Minecraft.getInstance().font;

        // Rises the last few pixels into place rather than only fading, so the
        // grid assembles instead of materialising. The rectangle does not move
        // with it - a hitbox that chases the drawing is a hitbox that misses.
        float eased = Ease.outCubic(appear);
        int rise = Math.round((1f - eased) * 12f);
        int lift = Math.round(hover * 2f);

        int x1 = getX();
        int y1 = getY() + rise - lift;
        int x2 = x1 + width;
        int y2 = y1 + height;

        // Hover and "switched on" both lighten the plate. On is the stronger of
        // the two, because it has to be readable across the whole grid at a
        // glance, and hover only has to be readable where the pointer is.
        int plate = Ease.color(Theme.CARD, Theme.CARD_HOVER, Math.max(hover, state * 0.7f));
        Glass.pill(graphics, x1, y1, width, height,
                fade(plate, eased), 8, clipTop, clipBottom);

        // A bar down the left edge marks what is on. It grows from the middle
        // out, so switching a module on has a direction rather than simply
        // being a different colour the next frame.
        if (state > 0.01f) {
            int barHeight = Math.round((height - 16) * Ease.inOutCubic(state));
            int middle = (y1 + y2) / 2;
            Glass.band(graphics, x1 + 3, middle - barHeight / 2, x1 + 5, middle + barHeight / 2,
                    fade(Theme.accent(), eased), clipTop, clipBottom);
        }

        int pillLeft = x2 - 12 - PILL_W;

        String name = ToggleRow.fit(font, label.get(), pillLeft - (x1 + 12) - 6);
        text(graphics, font, name, x1 + 12, y1 + 11,
                fade(Ease.color(Theme.TEXT_DIM, Theme.TEXT, Math.max(hover, state)), eased));

        String detail = ToggleRow.fit(font, description, width - 24 - (hasSettings ? 22 : 0));
        text(graphics, font, detail, x1 + 12, y1 + 25, fade(Theme.TEXT_DIM, eased * 0.85f));

        // The switch: a track that fills and a knob that slides across it.
        int pillTop = y1 + 10;
        Glass.pill(graphics, pillLeft, pillTop, PILL_W, PILL_H,
                fade(Ease.color(Theme.CHIP, Theme.accent(), state), eased), PILL_H / 2,
                clipTop, clipBottom);

        int knob = pillLeft + 1 + Math.round((PILL_W - PILL_H) * Ease.inOutCubic(state));
        Glass.pill(graphics, knob, pillTop + 1, PILL_H - 2, PILL_H - 2,
                fade(state > 0.5f ? Theme.TEXT_ON_ACCENT : Theme.TEXT_DIM, eased),
                (PILL_H - 2) / 2, clipTop, clipBottom);

        // Settings live in the bottom right corner as three dots, lit only when
        // the pointer is actually on them - so the card says what a click will
        // do before the click happens.
        if (hasSettings) {
            int dot = fade(overGear() ? Theme.accent() : Theme.OFF, eased);
            int dotY = y2 - 13;
            for (int i = 0; i < 3; i++) {
                Glass.band(graphics, x2 - 26 + i * 6, dotY, x2 - 23 + i * 6, dotY + 2,
                        dot, clipTop, clipBottom);
            }
        }
    }

    /**
     * Text, or nothing at all if the line would be cut by the band.
     *
     * A string is drawn by the font as one call and there is no way to stop it
     * halfway down a glyph, so a line that does not fit is left out. The plate
     * under it is still clipped row by row, which is what carries the edge; a
     * half-drawn word would only look broken.
     */
    private void text(GuiGraphicsExtractor graphics, Font font, String value, int x, int y, int colour) {
        if (value == null || value.isEmpty()) return;
        if (y < clipTop || y + 8 > clipBottom) return;
        graphics.text(font, value, x, y, colour, false);
    }
}
