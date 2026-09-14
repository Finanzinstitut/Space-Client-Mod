package gg.spaceclient.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A switch, for a setting that is either on or off.
 *
 * What it replaces was a rectangle with the word OFF written at the right hand
 * end. That is readable, but it is also the shape of every other button on the
 * screen - a group that opens a sub-screen, a mode that cycles, and a thing
 * that is simply on all looked identical, and the only way to tell which was
 * which was to read the text. A switch is recognised before it is read.
 *
 * The knob slides rather than jumping, which is what makes a click feel like it
 * moved something. Frame time drives it, so it takes the same quarter second at
 * thirty frames a second as at two hundred and forty.
 *
 * The description is drawn under the name. Every setting in this client already
 * carries one and until now nothing showed it anywhere, which left names like
 * "Weight" to be understood by trying them.
 */
public class ToggleRow extends Button {

    /** Height of a row with a description under its name. */
    public static final int TALL = 34;

    private final Supplier<String> name;
    private final String description;
    private final BooleanSupplier state;

    private float knob = -1f;
    private float hover = 0f;

    public ToggleRow(int x, int y, int width, int height,
                     Supplier<String> name, String description,
                     BooleanSupplier state, Runnable onPress) {
        super(x, y, width, height, Component.empty(),
                btn -> onPress.run(), DEFAULT_NARRATION);
        this.name = name;
        this.description = description == null ? "" : description;
        this.state = state;
    }

    /** Mouse only, for the same reason as every other widget in this menu. */
    @Override
    public void setFocused(boolean focused) { super.setFocused(false); }

    @Override
    public boolean isFocused() { return false; }

    public net.minecraft.client.gui.ComponentPath nextFocusPath(
            net.minecraft.client.gui.navigation.FocusNavigationEvent event) {
        return null;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean on = state.getAsBoolean();
        float frame = Math.max(delta, 0.1f);

        // Starts where it belongs rather than sliding in from off on the first
        // frame the screen is built, which would make opening a menu look like
        // every switch on it had just been flicked.
        if (knob < 0f) knob = on ? 1f : 0f;
        knob = Ease.approach(knob, on ? 1f : 0f, 0.3f, frame);
        hover = Ease.approach(hover, isHovered() ? 1f : 0f, 0.25f, frame);

        int x1 = getX();
        int y1 = getY();
        int w = this.width;
        int h = this.height;

        float lit = Math.max(hover, knob * 0.35f);
        Glass.panel(graphics, x1, y1, w, h, Ease.color(0x50100D2A, 0x90221C58, lit), 8);

        var font = Minecraft.getInstance().font;

        int trackW = 30;
        int trackH = 14;
        int trackX = x1 + w - trackW - 14;
        int trackY = y1 + (h - trackH) / 2;

        int textRoom = trackX - (x1 + 14) - 10;
        boolean twoLines = !description.isEmpty() && h >= TALL;

        int nameY = twoLines ? y1 + h / 2 - font.lineHeight - 1 : y1 + (h - font.lineHeight) / 2;
        graphics.text(font, fit(font, name.get(), textRoom), x1 + 14, nameY,
                Ease.color(0xFFC9C4EE, 0xFFFFFFFF, Math.max(hover, knob)), false);

        if (twoLines) {
            graphics.text(font, fit(font, description, textRoom), x1 + 14, y1 + h / 2 + 2,
                    Theme.TEXT_DIM, false);
        }

        // The track darkens to the accent as the knob travels, so colour and
        // position say the same thing and a glance at either settles it.
        Glass.panel(graphics, trackX, trackY, trackW, trackH,
                Ease.color(0xFF262046, Theme.accent(), knob), trackH / 2);

        int knobSize = trackH - 4;
        int travel = trackW - knobSize - 4;
        int knobX = trackX + 2 + Math.round(travel * Ease.inOutCubic(knob));
        Glass.panel(graphics, knobX, trackY + 2, knobSize, knobSize,
                Ease.color(0xFF6E679A, Theme.CYAN, knob), knobSize / 2);
    }

    /** Trims to what the row has room for, with an ellipsis where it was cut. */
    static String fit(net.minecraft.client.gui.Font font, String text, int room) {
        if (room <= 8 || text == null || text.isEmpty()) return text == null ? "" : text;
        if (font.width(text) <= room) return text;

        String out = text;
        while (out.length() > 1 && font.width(out + "..") > room) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "..";
    }
}
