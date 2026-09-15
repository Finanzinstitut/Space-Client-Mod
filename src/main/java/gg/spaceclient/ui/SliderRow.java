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

        graphics.fill(x1, y1, x2, y2, 0x30FFFFFF);

        // Filled portion up to the handle
        int filled = x1 + (int) (this.value * this.width);
        graphics.fill(x1, y1, filled, y2, Theme.accentDim());
        graphics.fill(filled - 2, y1, filled + 1, y2, Theme.CYAN);

        int border = isHovered() ? Theme.accent() : Theme.BORDER;
        graphics.fill(x1, y1, x2, y1 + 1, border);
        graphics.fill(x1, y2 - 1, x2, y2, border);
        graphics.fill(x1, y1, x1 + 1, y2, border);
        graphics.fill(x2 - 1, y1, x2, y2, border);

        var font = net.minecraft.client.Minecraft.getInstance().font;
        int textY = y1 + (this.height - font.lineHeight) / 2;
        graphics.text(font, name, x1 + 10, textY, Theme.TEXT, false);

        String valueText = String.valueOf(value());
        graphics.text(font, valueText, x2 - font.width(valueText) - 10, textY, Theme.TEXT_DIM, false);
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

        String valueText = String.valueOf(value());
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
