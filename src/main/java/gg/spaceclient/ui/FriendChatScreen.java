package gg.spaceclient.ui;

import gg.spaceclient.net.Friends;
import gg.spaceclient.util.Screens;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * One conversation: with a friend, or in a group.
 *
 * <h2>Why there is no send button worth speaking of</h2>
 *
 * There is one, because a screen where the only way to do the thing is a key
 * nobody told you about is a screen people call broken. But the field takes
 * Enter as well, because that is what everybody's hands do.
 *
 * <h2>Reading the thread</h2>
 *
 * Newest at the bottom. Lines from you sit right and lines from them sit left,
 * so the eye can follow a conversation without reading a single name - and in
 * a group, where there is more than one "them", each of their lines carries
 * the name of whoever said it.
 */
public class FriendChatScreen extends Screen {

    private static final int PANEL_W = 360;
    private static final int INPUT_H = 22;

    private final Screen parent;

    /** The friend, when this is a conversation between two. */
    private final Friends.Person friend;

    /** The group's id, when this is a group; -1 otherwise. */
    private final long groupId;

    private EditBox input = null;
    private String typed = "";

    /** How far up the thread has been scrolled, in lines. */
    private int scroll = 0;

    private boolean enterWasDown = true;
    private long seenRevision = -1;

    private FriendChatScreen(Screen parent, Friends.Person friend, long groupId) {
        super(Component.literal("Chat"));
        this.parent = parent;
        this.friend = friend;
        this.groupId = groupId;
    }

    public static FriendChatScreen with(Screen parent, Friends.Person friend) {
        return new FriendChatScreen(parent, friend, -1);
    }

    public static FriendChatScreen with(Screen parent, Friends.Group group) {
        return new FriendChatScreen(parent, null, group.id());
    }

    private boolean isGroup() { return groupId >= 0; }

    private String key() { return isGroup() ? Friends.groupKey(groupId) : friend.uuid(); }

    /** The group as the model has it now, or null once it is gone. */
    private Friends.Group group() { return isGroup() ? Friends.group(groupId) : null; }

    private String title() {
        if (!isGroup()) return friend.name();
        Friends.Group group = group();
        return group == null ? "Group" : group.name();
    }

    private String subtitle() {
        if (!isGroup()) return "Friend";
        Friends.Group group = group();
        if (group == null) return "You are no longer in this group";
        StringBuilder names = new StringBuilder();
        for (Friends.Person member : group.members()) {
            if (names.length() > 0) names.append(", ");
            names.append(member.name());
        }
        String all = names.toString();
        return all.length() > 70 ? all.substring(0, 68) + ".." : all;
    }

    private int panelLeft() { return (this.width - PANEL_W) / 2; }

    @Override
    protected void init() {
        Friends.markRead(key());
        Friends.refreshSoon();

        int left = panelLeft();
        int y = inputY();

        input = new EditBox(this.font, left + 10, y + (INPUT_H - 8) / 2, PANEL_W - 90, 12,
                Component.literal("Message"));
        input.setBordered(false);
        input.setMaxLength(256);
        input.setTextColor(Theme.TEXT);
        input.setHint(Component.literal(isGroup() ? "Write to the group" : "Write to " + friend.name()));
        input.setValue(typed);
        input.setResponder(value -> typed = value);
        this.addRenderableWidget(input);
        this.setInitialFocus(input);

        this.addRenderableWidget(new FlatButton(
                left + PANEL_W - 72, y, 72, INPUT_H,
                () -> "Send", () -> !typed.trim().isEmpty(), this::send).asAction());

        if (isGroup()) {
            // Two small buttons under the header: who else, and out
            // Between the member list (y 56) and the first line (TOP + 6)
            int top = ScreenChrome.TOP - 21;
            this.addRenderableWidget(new FlatButton(
                    left + PANEL_W - 150, top, 72, 18,
                    () -> "Invite", () -> false,
                    () -> {
                        Friends.Group group = group();
                        if (group != null) Screens.open(GroupPickScreen.invite(this, group));
                    }).asAction());
            this.addRenderableWidget(new FlatButton(
                    left + PANEL_W - 72, top, 72, 18,
                    () -> "Leave", () -> false,
                    () -> {
                        Friends.leave(groupId);
                        onClose();
                    }).asAction());
        }

        this.addRenderableWidget(new FlatButton(
                left, ScreenChrome.bottomRow(this.height), PANEL_W, 24,
                () -> "Back", () -> false, this::onClose).asAction());
    }

    private int inputY() { return ScreenChrome.bottomRow(this.height) - INPUT_H - 14; }

    private void send() {
        String text = typed.trim();
        if (text.isEmpty()) return;

        if (isGroup()) Friends.sendToGroup(groupId, text);
        else Friends.send(friend.uuid(), text);
        typed = "";
        scroll = 0;
        input.setValue("");
    }

    /**
     * Enter sends.
     *
     * Not an override: key handling moved onto an event object in these
     * versions, and naming one signature would be a method nothing calls on
     * the others. The key is read straight from the window, on the edge so a
     * held Enter sends once.
     */
    @Override
    public void tick() {
        boolean down = gg.spaceclient.input.RawKeyboard.isDown(
                com.mojang.blaze3d.platform.InputConstants.KEY_RETURN);
        if (down && !enterWasDown) send();
        enterWasDown = down;

        // Read while open, so the count on the list does not climb for a
        // conversation that is on screen
        if (Friends.revision() != seenRevision) {
            seenRevision = Friends.revision();
            Friends.markRead(key());
        }
    }

    // Two shapes, neither annotated, for the same reason as everywhere else
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return scrollBy(scrollY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return scrollBy(amount);
    }

    private boolean scrollBy(double amount) {
        List<Friends.Message> lines = Friends.thread(key());
        int max = Math.max(0, lines.size() - visibleLines());
        scroll = Math.max(0, Math.min(max, scroll + (int) Math.signum(amount)));
        return true;
    }

    private int lineStep() { return this.font.lineHeight + 7; }

    private int visibleLines() {
        int top = ScreenChrome.TOP + 6;
        int bottom = inputY() - 8;
        return Math.max(1, (bottom - top) / lineStep());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        ScreenChrome.background(graphics, this.width, this.height, mouseX, mouseY, delta);
        ScreenChrome.header(graphics, this.font, this.width, title(), subtitle());

        int left = panelLeft();

        Glass.flat(graphics, left, inputY(), PANEL_W - 80, INPUT_H, Theme.CHIP, 6);

        List<Friends.Message> lines = Friends.thread(key());
        int room = visibleLines();

        int first = Math.max(0, lines.size() - room - scroll);
        int last = Math.min(lines.size(), first + room);

        int y = ScreenChrome.TOP + 6;
        for (int i = first; i < last; i++) {
            drawLine(graphics, left, y, lines.get(i));
            y += lineStep();
        }

        if (lines.isEmpty()) {
            graphics.text(this.font, "Nothing yet. Say something.",
                    left, ScreenChrome.TOP + 6, Theme.TEXT_DIM, false);
        }

        // A send that did not go through says so beside the box
        String error = Friends.sendError(key());
        if (!error.isEmpty()) {
            String shown = error;
            while (shown.length() > 4 && this.font.width(shown) > PANEL_W) {
                shown = shown.substring(0, shown.length() - 4) + "..";
            }
            graphics.text(this.font, shown, left, inputY() + INPUT_H + 3, 0xFFE8C46A, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    /**
     * One line: theirs on the left, yours on the right, each on its own plate.
     * In a group their lines start with the speaker's name, dimmed, inside the
     * plate - a name on a row of its own halved how much of the conversation
     * fit on a normal window.
     */
    private void drawLine(GuiGraphicsExtractor graphics, int left, int y, Friends.Message line) {
        String who = isGroup() && !line.mine() ? line.fromName() + "  " : "";
        int whoWidth = this.font.width(who);

        String text = clip(line.text(), PANEL_W - 60 - whoWidth);
        int width = whoWidth + this.font.width(text) + 16;
        int x = line.mine() ? left + PANEL_W - width : left;

        Glass.flat(graphics, x, y - 2, width, this.font.lineHeight + 5,
                line.mine() ? 0xFF24242A : Theme.CHIP, 5);

        if (!who.isEmpty()) graphics.text(this.font, who, x + 8, y + 1, 0xFF8E8E98, false);
        graphics.text(this.font, text, x + 8 + whoWidth, y + 1,
                line.mine() ? Theme.TEXT : 0xFFD6D6DE, false);

        // A line that has not reached the server yet is marked rather than
        // hidden: it is there, it is just not certain yet
        if (line.id() < 0) {
            graphics.fill(x, y - 2, x + 2, y + this.font.lineHeight + 3, 0x66FFFFFF);
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
        Friends.markRead(key());
        Screens.open(parent);
    }
}
