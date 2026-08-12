package gg.spaceclient.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * A flat, modern button drawn entirely from rectangles - no Minecraft button
 * texture, no bevel. Clicks are handled by AbstractWidget itself, which is why
 * the menu never has to touch the mouse event API.
 */
public class FlatButton extends AbstractWidget {
    private final Runnable onPress;
    private final java.util.function.Supplier<String> label;
    private final java.util.function.BooleanSupplier active;

    public FlatButton(int x, int y, int width, int height,
                      java.util.function.Supplier<String> label,
                      java.util.function.BooleanSupplier active,
                      Runnable onPress) {
        super(x, y, width, height, Component.empty());
        this.label = label;
        this.active = active;
        this.onPress = onPress;
    }

    @Override
    public void onPress() {
        onPress.run();
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean on = active.getAsBoolean();
        boolean hovered = isHovered();

        int x1 = getX();
        int y1 = getY();
        int x2 = x1 + this.width;
        int y2 = y1 + this.height;

        int background = on ? Theme.accentDim() : (hovered ? Theme.PANEL_ALT : 0x30FFFFFF);
        graphics.fill(x1, y1, x2, y2, background);

        // A single accent bar on the left marks the active state, which reads
        // faster than a full colour fill.
        if (on) {
            graphics.fill(x1, y1, x1 + 3, y2, Theme.accent());
        } else if (hovered) {
            graphics.fill(x1, y1, x1 + 3, y2, Theme.OFF);
        }

        // Hairline border
        // Cyan on hover, violet when active - the launcher's two accents
        int border = on ? Theme.CYAN : (hovered ? Theme.accent() : Theme.BORDER);
        graphics.fill(x1, y1, x2, y1 + 1, border);
        graphics.fill(x1, y2 - 1, x2, y2, border);
        graphics.fill(x1, y1, x1 + 1, y2, border);
        graphics.fill(x2 - 1, y1, x2, y2, border);

        var font = net.minecraft.client.Minecraft.getInstance().font;
        int textY = y1 + (this.height - font.lineHeight) / 2;
        graphics.text(font, label.get(), x1 + 12, textY, on ? Theme.TEXT : Theme.TEXT_DIM, false);

        // State pill on the right
        String state = on ? "ON" : "OFF";
        int stateWidth = font.width(state);
        graphics.text(font, state, x2 - stateWidth - 12, textY,
                on ? Theme.CYAN : Theme.OFF, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        // Narration is not implemented for this widget.
    }
}
