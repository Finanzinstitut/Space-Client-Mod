package gg.spaceclient.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * A flat slider for the colour channels. Extending AbstractSliderButton means
 * dragging is handled by the widget itself.
 */
public class SliderRow extends AbstractSliderButton {
    private final String name;
    private final Consumer<Integer> onChange;
    private final int max;

    /** Shown under the name where the row is tall enough to carry it. */
    private String description = "";

    /**
     * How the number is written out.
     *
     * A slider's stored value and the number a person is thinking of are not
     * always the same. The HUD editor's size slider runs from zero to two
     * hundred and fifty because a slider's knob sits at value over maximum, so
     * a range starting at fifty would put the knob in the wrong place - but
     * nobody wants to read "90" for an element drawn at 140%. The formatter
     * keeps the arithmetic honest and the label readable.
     */
    private java.util.function.IntFunction<String> format = String::valueOf;

    /** Writes the value some other way, for a slider whose range is not its label. */
    public SliderRow withFormat(java.util.function.IntFunction<String> formatter) {
        if (formatter != null) this.format = formatter;
        return this;
    }

    private String valueText() { return format.apply(value()); }

    private float hover = 0f;

    public SliderRow(int x, int y, int width, int height,
                     String name, int initial, int max, Consumer<Integer> onChange) {
        super(x, y, width, height, Component.empty(), initial / (double) max);
        this.name = name;
        this.max = max;
        this.onChange = onChange;
    }

    /**
     * Adds the setting's own description under its name.
     *
     * Opt in rather than always, because the compact drawing is still the right
     * one where a slider is one of several in a tight column - the item shower
     * has three in a row and there is nothing to explain about any of them.
     */
    public SliderRow withDescription(String text) {
        this.description = text == null ? "" : text;
        return this;
    }

    public int value() {
        return (int) Math.round(this.value * max);
    }

    @Override
    protected void updateMessage() {
        // The label is drawn manually below.
    }

    @Override
    protected void applyValue() {
        onChange.accept(value());
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (this.height >= 34) {
            drawTall(graphics, delta);
            return;
        }
        int x1 = getX();
        int y1 = getY();
        int x2 = x1 + this.width;
        int y2 = y1 + this.height;

        // A capsule with a track through it, not a bordered box that fills up
        // to the handle. The filled-box version was the last square-cornered
        // control left in the client, and a row that is half one colour reads
        // as a row that is half something else rather than as a slider.
        int radius = Math.min(9, this.height / 2);
        Glass.flat(graphics, x1, y1, this.width, this.height,
                isHovered() ? 0xF01E1E22 : 0xE6141418, radius);

        var font = net.minecraft.client.Minecraft.getInstance().font;
        int textY = y1 + (this.height - font.lineHeight) / 2 + 1;

        String valueText = valueText();
        int valueW = font.width(valueText);

        int trackX = x1 + 12 + font.width(name) + 8;
        int trackW = Math.max(12, x2 - 12 - valueW - 8 - trackX);
        int trackY = y1 + this.height / 2 - 2;

        Glass.flat(graphics, trackX, trackY, trackW, 3, 0xFF2A2A30, 1);
        int filled = Math.max(0, Math.round((float) (this.value * trackW)));
        if (filled > 0) Glass.flat(graphics, trackX, trackY, filled, 3, Theme.accent(), 1);

        int knob = trackX + filled;
        Glass.flat(graphics, knob - 4, trackY - 3, 8, 9,
                isHovered() ? Theme.TEXT : Theme.TEXT_DIM, 4);

        graphics.text(font, name, x1 + 12, textY, Theme.TEXT_DIM, false);
        graphics.text(font, valueText, x2 - valueW - 12, textY, Theme.TEXT, false);
    }

    /**
     * The roomy drawing: name and description on the left, value on the right,
     * and a thin track along the bottom with a knob on it.
     *
     * The compact one fills the whole row up to the handle, which makes the
     * row itself the bar. That reads well in a tight column and badly in a
     * settings list, where a half filled row looks like a row that is half
     * something else.
     */
    private void drawTall(GuiGraphicsExtractor graphics, float delta) {
        hover = Ease.approach(hover, isHovered() ? 1f : 0f, 0.25f, Math.max(delta, 0.1f));

        int x1 = getX();
        int y1 = getY();
        int w = this.width;
        int h = this.height;

        Glass.pill(graphics, x1, y1, w, h, Ease.color(0xCC0F0F12, 0xE61C1C20, hover), 8);

        var font = net.minecraft.client.Minecraft.getInstance().font;

        String valueText = valueText();
        int valueW = font.width(valueText);
        int room = w - 28 - valueW - 10;

        graphics.text(font, ToggleRow.fit(font, name, room), x1 + 14, y1 + 6,
                Ease.color(0xFFC8C8D0, 0xFFFFFFFF, hover), false);
        graphics.text(font, valueText, x1 + w - 14 - valueW, y1 + 6, Theme.CYAN, false);

        if (!description.isEmpty() && h >= 44) {
            graphics.text(font, ToggleRow.fit(font, description, w - 28),
                    x1 + 14, y1 + 6 + font.lineHeight + 2, Theme.TEXT_DIM, false);
        }

        int trackX = x1 + 14;
        int trackW = w - 28;
        int trackY = y1 + h - 12;

        Glass.pill(graphics, trackX, trackY, trackW, 4, 0xFF232327, 2);

        int filled = Math.max(0, Math.round((float) (this.value * trackW)));
        if (filled > 0) {
            Glass.pill(graphics, trackX, trackY, filled, 4,
                    Ease.color(Theme.accent(), Theme.CYAN, hover), 2);
        }

        int knobX = trackX + Math.min(trackW - 8, Math.max(0, filled - 4));
        Glass.pill(graphics, knobX, trackY - 3, 8, 10,
                Ease.color(0xFF8E8E98, Theme.CYAN, hover), 4);
    }

    /** Mouse only, for the same reason as the buttons. */
    @Override
    public void setFocused(boolean focused) {
        super.setFocused(false);
    }

    @Override
    public boolean isFocused() {
        return false;
    }

    /** Out of tab and arrow key navigation, for the same reason. */
    public net.minecraft.client.gui.ComponentPath nextFocusPath(
            net.minecraft.client.gui.navigation.FocusNavigationEvent event) {
        return null;
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput builder) {
        // Narration is not implemented for this widget.
    }
}
