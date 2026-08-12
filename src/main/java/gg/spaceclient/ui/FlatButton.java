package gg.spaceclient.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A flat, modern button drawn entirely from rectangles - no Minecraft button
 * texture, no bevel.
 *
 * It extends Button rather than AbstractWidget so the press handling comes for
 * free; only the drawing is replaced. That keeps the menu independent of the
 * mouse event API, which changed in this version.
 */
public class FlatButton extends Button {
    private final Supplier<String> label;
    private final BooleanSupplier active;

    public FlatButton(int x, int y, int width, int height,
                      Supplier<String> label,
                      BooleanSupplier active,
                      Runnable onPress) {
        super(x, y, width, height, Component.empty(),
                btn -> onPress.run(), DEFAULT_NARRATION);
        this.label = label;
        this.active = active;
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean on = active.getAsBoolean();
        boolean hovered = isHovered();

        int x1 = getX();
        int y1 = getY();
        int x2 = x1 + this.width;
        int y2 = y1 + this.height;

        int background = on ? Theme.accentDim() : (hovered ? Theme.PANEL_ALT : 0x30FFFFFF);
        graphics.fill(x1, y1, x2, y2, background);

        // A single accent bar on the left marks the active state, which reads
        // faster than filling the whole row with colour.
        if (on) {
            graphics.fill(x1, y1, x1 + 3, y2, Theme.accent());
        } else if (hovered) {
            graphics.fill(x1, y1, x1 + 3, y2, Theme.OFF);
        }

        // Cyan when active, violet on hover - the launcher's two accents
        int border = on ? Theme.CYAN : (hovered ? Theme.accent() : Theme.BORDER);
        graphics.fill(x1, y1, x2, y1 + 1, border);
        graphics.fill(x1, y2 - 1, x2, y2, border);
        graphics.fill(x1, y1, x1 + 1, y2, border);
        graphics.fill(x2 - 1, y1, x2, y2, border);

        var font = Minecraft.getInstance().font;
        int textY = y1 + (this.height - font.lineHeight) / 2;
        graphics.text(font, label.get(), x1 + 12, textY, on ? Theme.TEXT : Theme.TEXT_DIM, false);

        String state = on ? "ON" : "OFF";
        int stateWidth = font.width(state);
        graphics.text(font, state, x2 - stateWidth - 12, textY, on ? Theme.CYAN : Theme.OFF, false);
    }
}
