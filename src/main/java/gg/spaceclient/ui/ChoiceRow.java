package gg.spaceclient.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * A pick-one-of-several setting, shown as the value between two arrows.
 *
 * What it replaces cycled forwards on every click and said so nowhere. With
 * five options, going back one meant clicking four more times and watching for
 * the value you wanted to come round again. Here the left arrow goes back.
 *
 * Which arrow was pressed is worked out from where the mouse was on the last
 * drawn frame, because the press callback carries no coordinates. That is the
 * same route FlatButton already uses for its settings strip, so it is a known
 * quantity rather than a new idea.
 */
public class ChoiceRow extends Button {

    private static final int ARROW = 14;

    private final String name;
    private final String description;
    private final List<String> options;
    private final java.util.function.Supplier<String> current;
    private final Consumer<Integer> onStep;

    private int lastMouseX = 0;
    private float hover = 0f;
    private float leftLit = 0f;
    private float rightLit = 0f;

    public ChoiceRow(int x, int y, int width, int height,
                     String name, String description, List<String> options,
                     java.util.function.Supplier<String> current,
                     Consumer<Integer> onStep) {
        super(x, y, width, height, Component.empty(), btn -> {}, DEFAULT_NARRATION);
        this.name = name;
        this.description = description == null ? "" : description;
        this.options = options;
        this.current = current;
        this.onStep = onStep;
    }

    @Override
    public void onPress() {
        // The chip spans the right hand end; its left half steps back and its
        // right half steps on. A click anywhere else on the row steps on too,
        // which keeps the old one-click habit working.
        int chipLeft = getX() + this.width - chipWidth() - 12;
        int middle = chipLeft + chipWidth() / 2;
        onStep.accept(lastMouseX >= chipLeft && lastMouseX < middle ? -1 : 1);
    }

    private int chipWidth() {
        var font = Minecraft.getInstance().font;
        int widest = 0;
        for (String option : options) widest = Math.max(widest, font.width(option));
        return widest + ARROW * 2 + 16;
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
        lastMouseX = mouseX;
        float frame = Math.max(delta, 0.1f);
        hover = Ease.approach(hover, isHovered() ? 1f : 0f, 0.25f, frame);

        int x1 = getX();
        int y1 = getY();
        int w = this.width;
        int h = this.height;

        Glass.panel(graphics, x1, y1, w, h, Ease.color(0x50100D2A, 0x90221C58, hover), 8);

        var font = Minecraft.getInstance().font;

        int chipW = chipWidth();
        int chipX = x1 + w - chipW - 12;
        int chipH = Math.min(h - 8, 18);
        int chipY = y1 + (h - chipH) / 2;

        int textRoom = chipX - (x1 + 14) - 10;
        boolean twoLines = !description.isEmpty() && h >= ToggleRow.TALL;

        int nameY = twoLines ? y1 + h / 2 - font.lineHeight - 1 : y1 + (h - font.lineHeight) / 2;
        graphics.text(font, ToggleRow.fit(font, name, textRoom), x1 + 14, nameY,
                Ease.color(0xFFC9C4EE, 0xFFFFFFFF, hover), false);
        if (twoLines) {
            graphics.text(font, ToggleRow.fit(font, description, textRoom), x1 + 14,
                    y1 + h / 2 + 2, Theme.TEXT_DIM, false);
        }

        Glass.panel(graphics, chipX, chipY, chipW, chipH,
                Ease.color(0xFF201B42, 0xFF2E2764, hover), chipH / 2);

        boolean overChip = isHovered() && mouseX >= chipX && mouseX < chipX + chipW;
        boolean overLeft = overChip && mouseX < chipX + chipW / 2;
        leftLit = Ease.approach(leftLit, overLeft ? 1f : 0f, 0.3f, frame);
        rightLit = Ease.approach(rightLit, overChip && !overLeft ? 1f : 0f, 0.3f, frame);

        int centreY = chipY + chipH / 2;
        arrow(graphics, chipX + 10, centreY, -1, Ease.color(Theme.OFF, Theme.CYAN, leftLit));
        arrow(graphics, chipX + chipW - 10, centreY, 1, Ease.color(Theme.OFF, Theme.CYAN, rightLit));

        String value = current.get();
        graphics.text(font, value, chipX + chipW / 2 - font.width(value) / 2,
                centreY - font.lineHeight / 2, Theme.TEXT, false);
    }

    /** A small triangle, built from rows because fill is all there is. */
    private static void arrow(GuiGraphicsExtractor graphics, int tipX, int centreY,
                              int direction, int colour) {
        for (int i = 0; i < 4; i++) {
            int x = tipX + direction * i;
            graphics.fill(Math.min(x, x + direction), centreY - i,
                    Math.max(x, x + direction), centreY + i + 1, colour);
        }
    }
}
