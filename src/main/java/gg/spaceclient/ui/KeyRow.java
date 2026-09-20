package gg.spaceclient.ui;

import gg.spaceclient.input.KeyBinds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * A key binding as one row: press the chip, then press the key.
 *
 * <h2>Catching the key</h2>
 *
 * By asking GLFW what is held, not by waiting for a key event. This version
 * reworked key handling onto an event object, so a hand written
 * keyPressed(int, int, int) would compile into a method nothing calls - and the
 * failure would be a binding row that quietly refuses to catch anything, which
 * is the worst kind. Polling is what the rest of this client already does for
 * the keyboard display and the zoom key, and it is known to work here.
 *
 * One rule makes it feel right: nothing is caught until the keyboard has been
 * seen empty once. Without it, opening the row while a key happens to be held -
 * a modifier, or the menu key on the way out - binds that key instantly.
 */
public class KeyRow extends Button {

    public static final int TALL = 34;

    private final String name;
    private final String description;
    private final IntSupplier read;
    private final IntConsumer write;

    /** Whether this row is waiting for a key. */
    private boolean listening = false;

    /** Whether the keyboard has been empty since listening began. */
    private boolean settled = false;

    private float hover = 0f;
    private float glow = 0f;

    public KeyRow(int x, int y, int width, int height,
                  String name, String description,
                  IntSupplier read, IntConsumer write) {
        // Through the constructor's handler and not an onPress override:
        // this version has no such method, and an @Override of one that is not
        // there is a build error while a silent one would be a dead row.
        super(x, y, width, height, Component.empty(),
                btn -> ((KeyRow) btn).toggleListening(), DEFAULT_NARRATION);
        this.name = name;
        this.description = description == null ? "" : description;
        this.read = read;
        this.write = write;
    }

    private void toggleListening() {
        listening = !listening;
        settled = false;
    }

    public boolean isListening() { return listening; }

    /** Mouse only, like every other widget in this menu. */
    @Override
    public void setFocused(boolean focused) { super.setFocused(false); }

    @Override
    public boolean isFocused() { return false; }

    public net.minecraft.client.gui.ComponentPath nextFocusPath(
            net.minecraft.client.gui.navigation.FocusNavigationEvent event) {
        return null;
    }

    /** Asks GLFW what is held and takes the first answer. */
    private void listen() {
        int held = KeyBinds.pressedKey();

        if (!settled) {
            // Wait for the hands to come off whatever they were on
            if (held == KeyBinds.UNBOUND) settled = true;
            return;
        }
        if (held == KeyBinds.UNBOUND) return;

        listening = false;

        if (held == GLFW.GLFW_KEY_ESCAPE) return;                 // leave it alone
        if (held == GLFW.GLFW_KEY_BACKSPACE || held == GLFW.GLFW_KEY_DELETE) {
            write.accept(KeyBinds.UNBOUND);                        // no key at all
            return;
        }
        write.accept(held);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (listening) listen();

        float frame = Math.max(delta, 0.1f);
        hover = Ease.approach(hover, isHovered() ? 1f : 0f, 0.25f, frame);
        glow = Ease.approach(glow, listening ? 1f : 0f, 0.3f, frame);

        int x1 = getX();
        int y1 = getY();
        int w = this.width;
        int h = this.height;

        Glass.pill(graphics, x1, y1, w, h,
                Ease.color(0xCC0F0F12, 0xE61C1C20, Math.max(hover, glow)), 8);

        var font = Minecraft.getInstance().font;

        // The chip is sized to its own text, so "Right Shift" is not cut short
        // and "K" does not sit in a box four times its width
        String shown = listening ? "Press a key" : KeyBinds.label(read.getAsInt());
        int chipW = Math.min(w / 2, font.width(shown) + 18);
        int chipH = 16;
        int chipX = x1 + w - chipW - 14;
        int chipY = y1 + (h - chipH) / 2;

        int textRoom = chipX - (x1 + 14) - 10;
        boolean twoLines = !description.isEmpty() && h >= TALL;

        int nameY = twoLines ? y1 + h / 2 - font.lineHeight - 1 : y1 + (h - font.lineHeight) / 2;
        graphics.text(font, ToggleRow.fit(font, name, textRoom), x1 + 14, nameY,
                Ease.color(0xFFC8C8D0, 0xFFFFFFFF, Math.max(hover, glow)), false);

        if (twoLines) {
            graphics.text(font, ToggleRow.fit(font, description, textRoom),
                    x1 + 14, y1 + h / 2 + 2, Theme.TEXT_DIM, false);
        }

        Glass.pill(graphics, chipX, chipY, chipW, chipH,
                Ease.color(0xFF232327, Theme.accent(), glow), chipH / 2);

        graphics.text(font, shown,
                chipX + (chipW - font.width(shown)) / 2,
                chipY + (chipH - font.lineHeight) / 2 + 1,
                listening ? Theme.TEXT_ON_ACCENT : Theme.TEXT, false);
    }
}
