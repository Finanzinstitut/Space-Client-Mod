package gg.spaceclient.ui;

import gg.spaceclient.util.Screens;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.BiConsumer;

/**
 * Adding a server, editing one, and typing an address to join once.
 *
 * <h2>Why this exists at all</h2>
 *
 * It used to be the game's own EditServerScreen, opened through reflection.
 * When that construction missed - and on this version it did - the button fell
 * through to the game's multiplayer list, so pressing "Add server" left the
 * client entirely. What you typed there went into the game's copy of the
 * server list, and this client was still holding the copy it had read on the
 * way in, so the server was saved and invisible: entered for nothing.
 *
 * Two fixes, and this is the first. A screen built from the same pieces as the
 * rest of the client cannot be missing on a version, cannot open onto dead
 * buttons, and hands back plain text rather than writing into an object whose
 * shape has to be guessed. The second fix is in ServersScreen: the list is
 * re-read whenever the screen is opened, so anything added anywhere shows up.
 *
 * <h2>No Enter to confirm</h2>
 *
 * Deliberate. This version reworked key handling onto an event object, and a
 * hand written keyPressed(int, int, int) would compile into a method nothing
 * calls - a shortcut that silently does nothing is worse than no shortcut, so
 * the buttons are the whole interface. Escape still works because the game
 * routes it through onClose, which is a method this screen really does
 * override.
 */
public class ServerEditScreen extends Screen {

    public enum Mode {
        /** A new entry, saved to the list. */
        ADD,
        /** An entry that already exists. */
        EDIT,
        /** An address typed to join once, saved nowhere. */
        DIRECT
    }

    private static final int PANEL_W = 320;
    private static final int FIELD_H = 20;

    private final Screen parent;
    private final Mode mode;

    private final String startName;
    private final String startAddress;

    /** Given the finished name and address, in that order. */
    private final BiConsumer<String, String> onConfirm;

    private EditBox nameBox;
    private EditBox addressBox;

    /** Live copies, so the buttons can judge them without asking a widget. */
    private String name;
    private String address;

    private long openedAt = 0L;

    public ServerEditScreen(Screen parent, Mode mode,
                            String name, String address,
                            BiConsumer<String, String> onConfirm) {
        super(Component.literal(title(mode)));
        this.parent = parent;
        this.mode = mode;
        this.startName = name == null ? "" : name;
        this.startAddress = address == null ? "" : address;
        this.name = this.startName;
        this.address = this.startAddress;
        this.onConfirm = onConfirm;
    }

    private static String title(Mode mode) {
        return switch (mode) {
            case ADD -> "Add server";
            case EDIT -> "Edit server";
            case DIRECT -> "Direct connect";
        };
    }

    private int left() { return (this.width - PANEL_W) / 2; }

    private boolean hasName() { return mode != Mode.DIRECT; }

    /**
     * The whole card, measured before anything is placed.
     *
     * Everything below is an offset from its top, which is what lets the card
     * sit in the middle of the window rather than at a fixed height - a form
     * with one field and a form with two are different sizes, and both should
     * look deliberate.
     */
    private int cardHeight() {
        return (hasName() ? 138 : 84) + FIELD_H + 76;
    }

    private int cardTop() {
        return Math.max(20, (this.height - cardHeight()) / 2);
    }

    private int firstFieldY() { return cardTop() + 84; }

    private int addressFieldY() {
        return hasName() ? firstFieldY() + 54 : firstFieldY();
    }

    /** Under the note that sits below the address. */
    private int buttonsY() {
        return addressFieldY() + FIELD_H + 36;
    }

    @Override
    protected void init() {
        if (openedAt == 0L) openedAt = System.currentTimeMillis();

        int x = left();

        if (hasName()) {
            nameBox = field(x, firstFieldY(), startName, "Minecraft Server", 48,
                    value -> name = value);
            if (name.isEmpty()) name = nameBox.getValue();
        }

        addressBox = field(x, addressFieldY(), startAddress, "play.example.net", 128,
                value -> address = value);

        // Inside the card, not pinned to the bottom of the window. A form is
        // one thing to fill in and then confirm, and putting its two buttons
        // two hundred pixels below the last field makes them look like they
        // belong to the screen rather than to the form.
        int y = buttonsY();
        int half = (PANEL_W - 8) / 2;

        this.addRenderableWidget(new FlatButton(
                x, y, half, 24,
                () -> mode == Mode.DIRECT ? "Join server" : "Done",
                this::ready,
                this::confirm).asAction());

        this.addRenderableWidget(new FlatButton(
                x + half + 8, y, PANEL_W - half - 8, 24,
                () -> "Cancel", () -> false, this::onClose).asAction());

        // The address is what the screen is for; the name is a label people
        // mostly leave alone, so the caret starts where the typing starts.
        this.setInitialFocus(addressBox);
    }

    /**
     * One field: a vanilla EditBox with its own border switched off.
     *
     * Vanilla because typing goes through the keyboard API and the widget
     * already handles it; unbordered because the plate behind it is drawn by
     * this screen, in the same shapes as everything else on it.
     */
    private EditBox field(int x, int y, String value, String hint, int max,
                          java.util.function.Consumer<String> onChange) {
        EditBox box = new EditBox(this.font, x + 8, y + (FIELD_H - 8) / 2,
                PANEL_W - 16, 12, Component.literal(hint));
        box.setBordered(false);
        box.setMaxLength(max);
        box.setTextColor(Theme.TEXT);
        box.setHint(Component.literal(hint));
        // Value first, responder second: setValue fires the responder, and a
        // responder that rebuilds would run mid init and stack a second widget.
        box.setValue(value);
        box.setResponder(onChange);
        this.addRenderableWidget(box);
        return box;
    }

    /** Whether there is enough here to do anything with. */
    private boolean ready() {
        return address != null && !address.trim().isEmpty();
    }

    private void confirm() {
        if (!ready()) return;

        String finalName = name == null ? "" : name.trim();
        if (finalName.isEmpty()) finalName = "Minecraft Server";

        onConfirm.accept(finalName, address.trim());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (!MenuWallpaper.draw(graphics, this.width, this.height, mouseX, mouseY, delta)) {
            Backdrop.draw(graphics, this.width, this.height);
        }
        graphics.fill(0, 0, this.width, this.height, 0x60000000);

        int x = left();
        float appear = Ease.clamp01((System.currentTimeMillis() - openedAt) / 220f);
        int rise = Math.round((1f - Ease.outCubic(appear)) * 10f);

        // One plate behind the whole form, so the two fields read as one thing
        // to fill in rather than as two widgets floating on the wallpaper.
        int top = cardTop() - rise;
        Glass.panel(graphics, x - 18, top, PANEL_W + 36, cardHeight(), Theme.PANEL, 10);

        JupiterIcon.draw(graphics, x, top + 10, 24);
        graphics.text(this.font, title(mode).toUpperCase(java.util.Locale.ROOT),
                x + 34, top + 14, Theme.CYAN, false);
        graphics.text(this.font, subtitle(), x + 34, top + 26, Theme.TEXT_DIM, false);
        graphics.fill(x, top + 50, x + PANEL_W, top + 51, Theme.BORDER);

        if (hasName()) plate(graphics, x, firstFieldY(), "NAME", nameBox);
        plate(graphics, x, addressFieldY(), "ADDRESS", addressBox);

        // Said once, under the field it applies to, rather than after the fact
        // in a screen you have already left.
        String note = mode == Mode.DIRECT
                ? "Joined once - this address is not saved to your list."
                : "An address looks like play.example.net or 1.2.3.4:25565.";
        graphics.text(this.font, note, x, addressFieldY() + FIELD_H + 10,
                Theme.TEXT_DIM, false);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private String subtitle() {
        return switch (mode) {
            case ADD -> "Saved to your server list";
            case EDIT -> "Changes are saved when you press Done";
            case DIRECT -> "Connect without saving anything";
        };
    }

    /** The label above a field and the plate behind it. */
    private void plate(GuiGraphicsExtractor graphics, int x, int y, String label, EditBox box) {
        graphics.text(this.font, label, x, y - 12, Theme.TEXT_DIM, false);

        boolean focused = box != null && box.isFocused();
        Glass.flat(graphics, x, y, PANEL_W, FIELD_H,
                focused ? Theme.CHIP_HOVER : Theme.CHIP, 6);

        // A line under a focused field, which is the smallest mark that still
        // says where the typing is going.
        if (focused) {
            graphics.fill(x + 6, y + FIELD_H - 2, x + PANEL_W - 6, y + FIELD_H - 1,
                    Theme.accent());
        }
    }

    @Override
    public void onClose() {
        Screens.open(parent);
    }
}
