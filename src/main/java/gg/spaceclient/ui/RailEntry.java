package gg.spaceclient.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * One line in the menu's left rail.
 *
 * The rail carries two different kinds of thing and the difference matters:
 * a filter, which changes what the grid shows and stays selected, and a
 * destination, which leaves for another screen and never looks selected. They
 * are drawn the same because they are reached the same way - the point of the
 * rebuild was that everything lives in one list - but a filter carries a count
 * on its right and a destination carries a chevron, which is enough to tell
 * you which one you are about to press.
 *
 * The selected marker is deliberately not drawn here. The screen draws a
 * single bar that slides between entries, which reads as one indicator moving
 * rather than one light going out as another comes on; a widget can only know
 * about itself, so it cannot do that.
 */
public class RailEntry extends Button {

    private static final float SPEED = 0.30f;

    private final Supplier<String> label;
    private final BooleanSupplier selected;

    /** Null for a destination. A filter always has a number to show. */
    private final IntSupplier count;

    private float hover = 0f;
    private float pick = 0f;

    public RailEntry(int x, int y, int width, int height,
                     Supplier<String> label, BooleanSupplier selected,
                     IntSupplier count, Runnable onPress) {
        super(x, y, width, height, Component.empty(), btn -> onPress.run(), DEFAULT_NARRATION);
        this.label = label;
        this.selected = selected;
        this.count = count;
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

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        hover = Ease.approach(hover, isHovered() ? 1f : 0f, SPEED, delta);
        pick = Ease.approach(pick, selected.getAsBoolean() ? 1f : 0f, SPEED, delta);

        int x1 = getX();
        int y1 = getY();
        Font font = Minecraft.getInstance().font;

        if (hover > 0.02f || pick > 0.02f) {
            int tint = Ease.color(0x00FFFFFF, 0x18FFFFFF, Math.max(hover, pick));
            Glass.pill(graphics, x1, y1, width, height, tint, Math.min(9, height / 2));
        }

        // The label slides a pixel to the right under the pointer. It is the
        // smallest movement in the menu and it is the one that makes the rail
        // feel like it is answering rather than repainting.
        int textX = x1 + 14 + Math.round(hover * 2f);
        int colour = Ease.color(Theme.TEXT_DIM, Theme.TEXT, Math.max(hover, pick));

        String text = label.get();
        int room = width - 22 - (count == null ? 8 : 24);
        graphics.text(font, ToggleRow.fit(font, text, room), textX, y1 + (height - 8) / 2, colour, false);

        if (count != null) {
            String number = String.valueOf(count.getAsInt());
            graphics.text(font, number, x1 + width - 10 - font.width(number),
                    y1 + (height - 8) / 2, Ease.color(Theme.OFF, Theme.TEXT_DIM, hover), false);
            return;
        }

        // A chevron, so a line that leaves the menu looks like it leaves.
        int tipX = x1 + width - 12;
        int midY = y1 + height / 2;
        int arrow = Ease.color(Theme.OFF, Theme.TEXT_DIM, hover);
        for (int step = 0; step < 3; step++) {
            graphics.fill(tipX - 3 + step, midY - 3 + step, tipX - 2 + step, midY - 2 + step, arrow);
            graphics.fill(tipX - 3 + step, midY + 2 - step, tipX - 2 + step, midY + 3 - step, arrow);
        }
    }
}
