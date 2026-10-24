package gg.spaceclient.ui;

import gg.spaceclient.skin.SkinImage;
import gg.spaceclient.skin.SkinLibrary;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A saved skin as a card: the figure, its name, and a mark for slim.
 */
public class SkinCard extends Button {

    private final SkinLibrary.Entry entry;
    private final Supplier<Identifier> texture;
    private final BooleanSupplier selected;
    private float hover = 0f;

    public SkinCard(int x, int y, int width, int height, SkinLibrary.Entry entry,
                    Supplier<Identifier> texture, BooleanSupplier selected, Runnable onPress) {
        super(x, y, width, height, Component.empty(), btn -> onPress.run(), DEFAULT_NARRATION);
        this.entry = entry;
        this.texture = texture;
        this.selected = selected;
    }

    /** Mouse driven only, for the same reason FlatButton is. */
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
        hover += ((isHovered() ? 1f : 0f) - hover) * 0.25f;
        int x1 = getX(), y1 = getY(), x2 = x1 + width, y2 = y1 + height;

        graphics.fill(x1, y1, x2, y2, hover > 0.5f ? Theme.CARD_HOVER : Theme.CARD);

        Identifier image = texture.get();
        if (image != null) {
            // Two screen pixels per skin pixel: 32 by 64 at most
            int figureWidth = (entry.slim() ? 14 : 16) * 2;
            SkinImage.drawFigure(graphics, image, x1 + (width - figureWidth) / 2, y1 + 5, 2, entry.slim(), false);
        }

        var font = Minecraft.getInstance().font;
        String name = entry.name();
        while (font.width(name) > width - 8 && name.length() > 1) name = name.substring(0, name.length() - 1);
        if (!name.equals(entry.name())) name = name.substring(0, Math.max(1, name.length() - 1)) + "…";
        graphics.text(font, name, x1 + (width - font.width(name)) / 2, y2 - 12, Theme.TEXT_DIM, false);

        if (entry.slim()) graphics.text(font, "S", x2 - 9, y1 + 4, Theme.TEXT_DIM, false);

        int border = selected.getAsBoolean() ? Theme.accent() : hover > 0.5f ? Theme.BORDER_HOVER : Theme.BORDER;
        graphics.fill(x1, y1, x2, y1 + 1, border);
        graphics.fill(x1, y2 - 1, x2, y2, border);
        graphics.fill(x1, y1, x1 + 1, y2, border);
        graphics.fill(x2 - 1, y1, x2, y2, border);
    }
}
