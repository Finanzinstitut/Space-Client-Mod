package gg.spaceclient.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The two small controls the new layout needs: sidebar entries and the
 * category chips above the grid.
 *
 * Both are the same shape underneath - a label, a selected state, an easing
 * hover - so they share a class and differ only in how they paint.
 */
public class NavButton extends Button {

    public enum Style { SIDEBAR, CHIP }

    private static final float SPEED = 0.22f;

    private final Supplier<String> label;
    private final BooleanSupplier selected;
    private final Style style;

    private float hover = 0f;
    private float pick = 0f;

    public NavButton(int x, int y, int width, int height, Style style,
                     Supplier<String> label, BooleanSupplier selected, Runnable onPress) {
        super(x, y, width, height, Component.empty(), btn -> onPress.run(), DEFAULT_NARRATION);
        this.label = label;
        this.selected = selected;
        this.style = style;
        this.pick = selected.getAsBoolean() ? 1f : 0f;
    }

    @Override
    public void setFocused(boolean focused) { super.setFocused(false); }

    @Override
    public boolean isFocused() { return false; }

    public net.minecraft.client.gui.ComponentPath nextFocusPath(
            net.minecraft.client.gui.navigation.FocusNavigationEvent event) {
        return null;
    }

    private static float approach(float c, float t, float s) { return c + (t - c) * s; }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        hover = approach(hover, isHovered() ? 1f : 0f, SPEED);
        pick = approach(pick, selected.getAsBoolean() ? 1f : 0f, SPEED);

        int x1 = getX(), y1 = getY(), x2 = x1 + width, y2 = y1 + height;
        var font = Minecraft.getInstance().font;
        String text = label.get();

        if (style == Style.CHIP) {
            // The picked chip takes the full accent rather than the dimmed one:
            // dimmed sat below the unselected fill and read as switched off.
            // A capsule. The picked one is simply lighter - a filled accent
            // chip beside five dark ones was the loudest thing on the screen.
            int bg = pick > 0.02f
                    ? 0xFF2E2E34
                    : (hover > 0.02f ? Theme.CHIP_HOVER : Theme.CHIP);
            Glass.pill(graphics, x1, y1, width, height, bg, height / 2);

            // Without a shadow now: the label sits on a plate dark enough to
            // carry plain text, and the shadow only muddied it.
            graphics.text(font, text, x1 + (width - font.width(text)) / 2, y1 + (height - 8) / 2,
                    pick > 0.5f ? Theme.TEXT : Theme.TEXT_DIM, false);
            return;
        }

        // Sidebar: the selected entry keeps a bar on its left edge, and the
        // hover tint slides in behind it rather than replacing it
        if (hover > 0.02f || pick > 0.02f) {
            int tint = pick > 0.02f ? Theme.SIDEBAR_PICK : Theme.CARD_HOVER;
            Glass.pill(graphics, x1, y1, width, height, tint, Math.min(9, height / 2));
        }
        if (pick > 0.02f) {
            int barHeight = Math.round((y2 - y1 - 10) * pick);
            int mid = (y1 + y2) / 2;
            graphics.fill(x1 + 3, mid - barHeight / 2, x1 + 5, mid + barHeight / 2, Theme.accent());
        }

        graphics.text(font, text, x1 + 14, y1 + (height - 8) / 2,
                pick > 0.5f ? Theme.TEXT : Theme.TEXT_DIM, false);
    }
}
