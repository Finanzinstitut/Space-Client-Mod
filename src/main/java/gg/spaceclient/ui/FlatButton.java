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
 * It extends Button so press handling comes for free; only the drawing is
 * replaced. That keeps the menu independent of the mouse event API, which
 * changed in this version.
 */
public class FlatButton extends Button {
    private final Supplier<String> label;
    private final BooleanSupplier active;

    /** When the last click happened, for the white flash. */
    private long clickedAt = 0;
    private static final long FLASH_MS = 220;

    /**
     * Where the mouse was on the last frame. Button's press callback gives no
     * coordinates, so remembering them here is what lets a click on the right
     * hand edge open settings instead of toggling.
     */
    private int lastMouseX = 0;

    /** Whether to draw the settings dots on the right hand edge. */
    private boolean showGear = false;

    /**
     * Action buttons have no on/off state, and drawing one on top of the label
     * is what made "Space Client" read as "Space ClieOFF".
     */
    private boolean showState = true;

    public FlatButton(int x, int y, int width, int height,
                      Supplier<String> label,
                      BooleanSupplier active,
                      Runnable onPress) {
        super(x, y, width, height, Component.empty(),
                btn -> onPress.run(), DEFAULT_NARRATION);
        this.label = label;
        this.active = active;
    }

    public int lastMouseX() { return lastMouseX; }

    public FlatButton withGear() {
        this.showGear = true;
        return this;
    }

    /** For buttons that do something rather than toggle something. */
    public FlatButton asAction() {
        this.showState = false;
        return this;
    }

    /**
     * These buttons are driven by the mouse only.
     *
     * Taking keyboard focus is actively harmful where they overlap the game:
     * in the chat, the arrow keys are for walking back through sent messages
     * and Enter is for sending. A focusable widget steals both, so a press of
     * Enter would skip the track instead of posting the message.
     */
    @Override
    public void setFocused(boolean focused) {
        super.setFocused(false);
    }

    @Override
    public boolean isFocused() {
        return false;
    }

    /**
     * Keeps the widget out of tab and arrow key navigation entirely.
     *
     * Refusing focus was not enough on its own: the screen walks its widgets
     * looking for a focus path, and a widget that merely reports itself
     * unfocused can still be handed one. Returning nothing here takes it out of
     * that walk, which is what stops the arrow keys from landing on it.
     *
     * Deliberately without @Override - if the navigation type is ever renamed,
     * this quietly becomes an unused method rather than a compile error.
     */
    public net.minecraft.client.gui.ComponentPath nextFocusPath(
            net.minecraft.client.gui.navigation.FocusNavigationEvent event) {
        return null;
    }

    /** Starts the flash. Call this from the press handler. */
    public void flash() {
        clickedAt = System.currentTimeMillis();
    }

    /**
     * AbstractButton draws the vanilla sprite in extractWidgetRenderState, which
     * is final, and then calls this. Filling the whole bounds here paints over
     * that sprite completely, which is how the flat look is achieved without
     * touching the mouse event API.
     */
    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        lastMouseX = mouseX;

        boolean on = active.getAsBoolean();
        boolean hovered = isHovered();

        int x1 = getX();
        int y1 = getY();
        int x2 = x1 + this.width;
        int y2 = y1 + this.height;

        // A rounded plate with a soft edge, not a rectangle with a border.
        // The state is carried by how bright the plate is rather than by a
        // coloured outline: an outline on every button is what made the menu
        // read as a grid of boxes.
        int background = on ? 0xF01E1E22 : (hovered ? 0xE6161619 : 0xCC0F0F12);
        Glass.pill(graphics, x1, y1, this.width, this.height, background,
                Math.min(10, this.height / 2));

        // One short bar on the left for the active state. It is the only place
        // the accent appears on a button, which is what keeps it meaning
        // something.
        if (on) {
            int inset = Math.max(3, this.height / 4);
            graphics.fill(x1 + 3, y1 + inset, x1 + 5, y2 - inset, Theme.accent());
        }

        var font = Minecraft.getInstance().font;
        int textY = y1 + (this.height - font.lineHeight) / 2;

        String state = on ? "ON" : "OFF";
        int stateWidth = showState ? font.width(state) : 0;

        // The label has to stop before the state text and the settings dots,
        // otherwise long names run straight through them.
        int reserved = stateWidth + (showGear ? 46 : (showState ? 24 : 12));
        int available = this.width - 12 - reserved;
        String text = label.get();
        if (available > 8 && font.width(text) > available) {
            while (text.length() > 1 && font.width(text + "..") > available) {
                text = text.substring(0, text.length() - 1);
            }
            text = text + "..";
        }
        graphics.text(font, text, x1 + 14, textY, on ? Theme.TEXT : Theme.TEXT_DIM, false);
        if (showState) {
            graphics.text(font, state, x2 - stateWidth - 34, textY,
                    on ? Theme.CYAN : Theme.OFF, false);
        }

        if (showGear) {
            // Three dots marking the strip that opens settings
            int gearX = x2 - 22;
            int dotY = y1 + this.height / 2 - 1;
            int dotColor = hovered && mouseX >= x2 - 34 ? Theme.CYAN : Theme.OFF;
            for (int i = 0; i < 3; i++) {
                graphics.fill(gearX + i * 5, dotY, gearX + i * 5 + 2, dotY + 2, dotColor);
            }
        }

        // A white wipe that sweeps across on click and fades out. Drawn last so
        // it covers the whole row.
        long since = System.currentTimeMillis() - clickedAt;
        if (since < FLASH_MS) {
            float progress = since / (float) FLASH_MS;
            int alpha = (int) (110 * (1.0f - progress));
            int sweep = (int) (this.width * Math.min(1.0f, progress * 1.6f));
            if (alpha > 2) {
                // Inset by a pixel so the wipe does not paint over the rounded
                // corners and square the button off for a fifth of a second.
                graphics.fill(x1 + 1, y1 + 1, Math.min(x2 - 1, x1 + sweep), y2 - 1,
                        (alpha << 24) | 0xFFFFFF);
            }
        }
    }
}
