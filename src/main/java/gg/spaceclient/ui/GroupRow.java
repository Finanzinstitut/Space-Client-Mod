package gg.spaceclient.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * A row that opens a sub-screen.
 *
 * Its own shape, because it is the one row on the settings screen that does not
 * change a setting - it goes somewhere. It used to be a button reading
 * "Colours  >", which put it in the same visual class as everything that does
 * change a setting, and the difference lived in one character at the end.
 *
 * The chevron leans into the direction of travel on hover, which is a small
 * thing that answers "will this do something here, or take me away" before the
 * click rather than after it.
 */
public class GroupRow extends Button {

    private final String name;
    private final String description;

    private float hover = 0f;

    public GroupRow(int x, int y, int width, int height,
                    String name, String description, Runnable onPress) {
        super(x, y, width, height, Component.empty(),
                btn -> onPress.run(), DEFAULT_NARRATION);
        this.name = name;
        this.description = description == null ? "" : description;
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
        hover = Ease.approach(hover, isHovered() ? 1f : 0f, 0.25f, Math.max(delta, 0.1f));

        int x1 = getX();
        int y1 = getY();
        int w = this.width;
        int h = this.height;

        Glass.panel(graphics, x1, y1, w, h, Ease.color(0x50100D2A, 0x90221C58, hover), 8);

        // A bar down the left, brightening on hover. Groups are the only rows
        // that carry one, so the eye can pick the way out of a long list.
        graphics.fill(x1 + 1, y1 + 6, x1 + 4, y1 + h - 6,
                Ease.color(0xFF3A3560, Theme.CYAN, hover));

        var font = Minecraft.getInstance().font;
        int room = w - 56;
        boolean twoLines = !description.isEmpty() && h >= ToggleRow.TALL;

        int nameY = twoLines ? y1 + h / 2 - font.lineHeight - 1 : y1 + (h - font.lineHeight) / 2;
        graphics.text(font, ToggleRow.fit(font, name, room), x1 + 16, nameY,
                Ease.color(0xFFC9C4EE, 0xFFFFFFFF, hover), false);
        if (twoLines) {
            graphics.text(font, ToggleRow.fit(font, description, room), x1 + 16,
                    y1 + h / 2 + 2, Theme.TEXT_DIM, false);
        }

        int tipX = x1 + w - 16 + Math.round(hover * 3f);
        int centreY = y1 + h / 2;
        int ink = Ease.color(Theme.OFF, Theme.CYAN, hover);
        for (int i = 0; i < 4; i++) {
            graphics.fill(tipX - i, centreY - i, tipX - i + 2, centreY + i + 1, ink);
        }
    }
}
