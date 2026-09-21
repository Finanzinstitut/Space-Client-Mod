package gg.spaceclient.ui;

import gg.spaceclient.net.Friends;
import gg.spaceclient.util.Screens;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * One conversation.
 *
 * <h2>Why there is no send button worth speaking of</h2>
 *
 * There is one, because a screen where the only way to do the thing is a key
 * nobody told you about is a screen people call broken. But the field takes
 * Enter as well, because that is what everybody's hands do, and a chat window
 * that ignores Enter feels wrong in a way that is hard to name and easy to
 * notice.
 *
 * <h2>Reading the thread</h2>
 *
 * Newest at the bottom, the way every chat has worked since talk(1). Lines
 * from you sit right and lines from them sit left: the same trick every
 * messenger uses, and it means the eye can follow a conversation without
 * reading a single name.
 */
public class FriendChatScreen extends Screen {

    private static final int PANEL_W = 360;
    private static final int INPUT_H = 22;

    private final Screen parent;
    private final Friends.Person friend;

    private EditBox input = null;
    private String typed = "";

    /** How far up the thread has been scrolled, in lines. */
    private int scroll = 0;

    public FriendChatScreen(Screen parent, Friends.Person friend) {
        super(Component.literal("Chat"));
        this.parent = parent;
        this.friend = friend;
    }

    private int panelLeft() { return (this.width - PANEL_W) / 2; }

    @Override
    protected void init() {
        Friends.markRead(friend.uuid());
        Friends.refreshSoon();

        int left = panelLeft();
        int y = inputY();

        input = new EditBox(this.font, left + 10, y + (INPUT_H - 8) / 2, PANEL_W - 90, 12,
                Component.literal("Message"));
        input.setBordered(false);
        input.setMaxLength(256);
        input.setTextColor(Theme.TEXT);
        input.setHint(Component.literal("Write to " + friend.name()));
        input.setValue(typed);
        input.setResponder(value -> typed = value);
        this.addRenderableWidget(input);
        this.setInitialFocus(input);

        this.addRenderableWidget(new FlatButton(
                left + PANEL_W - 72, y, 72, INPUT_H,
                () -> "Send", () -> !typed.trim().isEmpty(), this::send).asAction());

        this.addRenderableWidget(new FlatButton(
                left, ScreenChrome.bottomRow(this.height), PANEL_W, 24,
                () -> "Back", () -> false, this::onClose).asAction());
    }

    private int inputY() { return ScreenChrome.bottomRow(this.height) - INPUT_H - 10; }

    private void send() {
        String text = typed.trim();
        if (text.isEmpty()) return;

        Friends.send(friend.uuid(), text);
        typed = "";
        scroll = 0;
        input.setValue("");
    }

    /**
     * Enter sends.
     *
     * Not an override: this version put key handling on an event object, and
     * naming the old signature would be a method nothing calls. The key is
     * read the way the rest of this client reads keys - straight from GLFW -
     * and only while this screen is the one on display.
     */
    @Override
    public void tick() {
        boolean down = gg.spaceclient.input.RawKeyboard.isDown(
                org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);

        // On the edge, not while held: a held Enter would send the same line
        // twenty times a second
        if (down && !enterWasDown) send();
        enterWasDown = down;
    }

    private boolean enterWasDown = false;

    // Two shapes, neither annotated, for the same reason as everywhere else
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return scrollBy(scrollY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return scrollBy(amount);
    }

    private boolean scrollBy(double amount) {
        List<Friends.Message> lines = Friends.thread(friend.uuid());
        int room = visibleLines();
        int max = Math.max(0, lines.size() - room);

        scroll = Math.max(0, Math.min(max, scroll + (int) Math.signum(amount)));
        return true;
    }

    private int visibleLines() {
        int top = ScreenChrome.TOP + 6;
        int bottom = inputY() - 8;
        return Math.max(1, (bottom - top) / (this.font.lineHeight + 8));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        ScreenChrome.background(graphics, this.width, this.height, mouseX, mouseY, delta);
        ScreenChrome.header(graphics, this.font, this.width, friend.name(), "Friend");

        int left = panelLeft();

        Glass.flat(graphics, left, inputY(), PANEL_W - 80, INPUT_H, Theme.CHIP, 6);

        List<Friends.Message> lines = Friends.thread(friend.uuid());
        int room = visibleLines();

        int first = Math.max(0, lines.size() - room - scroll);
        int last = Math.min(lines.size(), first + room);

        int y = ScreenChrome.TOP + 6;
        int step = this.font.lineHeight + 8;

        for (int i = first; i < last; i++) {
            Friends.Message line = lines.get(i);
            drawLine(graphics, left, y, line);
            y += step;
        }

        if (lines.isEmpty()) {
            graphics.text(this.font, "Nothing yet. Say something.",
                    left, ScreenChrome.TOP + 6, Theme.TEXT_DIM, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    /** One line: theirs on the left, yours on the right, each on its own plate. */
    private void drawLine(GuiGraphicsExtractor graphics, int left, int y, Friends.Message line) {
        String text = clip(line.text(), PANEL_W - 60);
        int width = this.font.width(text) + 16;
        int x = line.mine() ? left + PANEL_W - width : left;

        Glass.flat(graphics, x, y - 2, width, this.font.lineHeight + 6,
                line.mine() ? 0xFF24242A : Theme.CHIP, 5);

        graphics.text(this.font, text, x + 8, y + 1,
                line.mine() ? Theme.TEXT : 0xFFD6D6DE, false);

        // A line that has not reached the server yet is dimmed rather than
        // hidden: it is there, it is just not certain yet
        if (line.id() < 0) {
            graphics.fill(x, y - 2, x + 2, y + this.font.lineHeight + 4, 0x66FFFFFF);
        }
    }

    private String clip(String text, int room) {
        if (this.font.width(text) <= room) return text;
        String out = text;
        while (out.length() > 1 && this.font.width(out + "..") > room) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "..";
    }

    @Override
    public void onClose() {
        Friends.markRead(friend.uuid());
        Screens.open(parent);
    }
}
