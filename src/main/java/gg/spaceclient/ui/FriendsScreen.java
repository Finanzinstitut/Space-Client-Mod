package gg.spaceclient.ui;

import gg.spaceclient.net.Friends;
import gg.spaceclient.util.Screens;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Friends, the people asking to be friends, and the box to ask somebody.
 *
 * <h2>Three lists, one screen</h2>
 *
 * Essential puts requests behind a bell and the search behind a plus, which
 * means two of the three things you come here to do are hidden behind an icon.
 * Here they are three tabs over one list: whichever you are after, it is one
 * click and you can see from the tab row whether the other two have anything
 * waiting. A number on the Requests tab is the whole notification system.
 *
 * <h2>Nothing here waits on the network</h2>
 *
 * Every button hands its work to a background thread and returns at once; the
 * list redraws from whatever the model holds. That is why there is no spinner
 * blocking the screen - the worst case is a row that appears a moment later.
 */
public class FriendsScreen extends Screen {

    private static final int PANEL_W = 340;
    private static final int ROW_H = 30;
    private static final int GAP = 6;

    private enum Tab { FRIENDS, REQUESTS, ADD }

    private final Screen parent;

    /** Kept between openings: coming back to the tab you left is expected. */
    private static Tab tab = Tab.FRIENDS;

    private EditBox nameBox = null;
    private String typed = "";
    private long openedAt = 0L;

    public FriendsScreen(Screen parent) {
        super(Component.literal("Friends"));
        this.parent = parent;
    }

    private int panelLeft() { return (this.width - PANEL_W) / 2; }

    @Override
    protected void init() {
        if (openedAt == 0L) openedAt = System.currentTimeMillis();

        // Asked for on every opening, answered whenever it is answered
        Friends.refreshSoon();

        int left = panelLeft();
        int y = ScreenChrome.TOP;

        buildTabs(left, y);
        y += 34;

        switch (tab) {
            case FRIENDS -> buildFriends(left, y);
            case REQUESTS -> buildRequests(left, y);
            case ADD -> buildAdd(left, y);
        }

        this.addRenderableWidget(new FlatButton(
                left, ScreenChrome.bottomRow(this.height), PANEL_W, 24,
                () -> "Back", () -> false, this::onClose).asAction());
    }

    private void buildTabs(int left, int y) {
        int width = (PANEL_W - GAP * 2) / 3;
        int waiting = Friends.incoming().size();

        add(left, y, width, "Friends", tab == Tab.FRIENDS, () -> switchTo(Tab.FRIENDS));
        add(left + width + GAP, y, width,
                waiting > 0 ? "Requests (" + waiting + ")" : "Requests",
                tab == Tab.REQUESTS, () -> switchTo(Tab.REQUESTS));
        add(left + (width + GAP) * 2, y, PANEL_W - (width + GAP) * 2, "Add",
                tab == Tab.ADD, () -> switchTo(Tab.ADD));
    }

    private void add(int x, int y, int width, String label, boolean on, Runnable press) {
        this.addRenderableWidget(new FlatButton(
                x, y, width, 24, () -> label, () -> on, press).asAction());
    }

    private void switchTo(Tab next) {
        tab = next;
        this.rebuildWidgets();
    }

    private void buildFriends(int left, int y) {
        List<Friends.Person> people = Friends.friends();
        int floor = ScreenChrome.bottomRow(this.height) - ROW_H;

        for (Friends.Person person : people) {
            if (y > floor) break;

            int unread = Friends.unreadFor(person.uuid());
            String label = person.name() + (unread > 0 ? "  (" + unread + ")" : "");

            this.addRenderableWidget(new FlatButton(
                    left, y, PANEL_W - 76, ROW_H,
                    () -> label, () -> unread > 0,
                    () -> Screens.open(new FriendChatScreen(this, person))).asAction());

            this.addRenderableWidget(new FlatButton(
                    left + PANEL_W - 70, y, 70, ROW_H,
                    () -> "Remove", () -> false,
                    () -> {
                        Friends.remove(person.uuid());
                        this.rebuildWidgets();
                    }).asAction());

            y += ROW_H + GAP;
        }
    }

    private void buildRequests(int left, int y) {
        int floor = ScreenChrome.bottomRow(this.height) - ROW_H;

        for (Friends.Person person : Friends.incoming()) {
            if (y > floor) break;

            this.addRenderableWidget(new FlatButton(
                    left, y, PANEL_W - 150, ROW_H,
                    person::name, () -> true, () -> {}).asAction());

            this.addRenderableWidget(new FlatButton(
                    left + PANEL_W - 144, y, 70, ROW_H,
                    () -> "Accept", () -> false,
                    () -> {
                        Friends.accept(person.uuid());
                        this.rebuildWidgets();
                    }).asAction());

            this.addRenderableWidget(new FlatButton(
                    left + PANEL_W - 70, y, 70, ROW_H,
                    () -> "Decline", () -> false,
                    () -> {
                        Friends.remove(person.uuid());
                        this.rebuildWidgets();
                    }).asAction());

            y += ROW_H + GAP;
        }

        // Asked, not yet answered. Shown because "did I actually send that?"
        // is the first thing anybody wonders a minute later.
        for (Friends.Person person : Friends.outgoing()) {
            if (y > floor) break;

            this.addRenderableWidget(new FlatButton(
                    left, y, PANEL_W - 76, ROW_H,
                    () -> person.name() + "  -  waiting", () -> false, () -> {}).asAction());

            this.addRenderableWidget(new FlatButton(
                    left + PANEL_W - 70, y, 70, ROW_H,
                    () -> "Cancel", () -> false,
                    () -> {
                        Friends.remove(person.uuid());
                        this.rebuildWidgets();
                    }).asAction());

            y += ROW_H + GAP;
        }
    }

    private void buildAdd(int left, int y) {
        nameBox = new EditBox(this.font, left + 10, y + 8, PANEL_W - 100, 12,
                Component.literal("Name"));
        nameBox.setBordered(false);
        nameBox.setMaxLength(16);
        nameBox.setTextColor(Theme.TEXT);
        nameBox.setHint(Component.literal("Minecraft name"));
        nameBox.setValue(typed);
        nameBox.setResponder(value -> typed = value);
        this.addRenderableWidget(nameBox);
        this.setInitialFocus(nameBox);

        this.addRenderableWidget(new FlatButton(
                left + PANEL_W - 80, y, 80, ROW_H,
                () -> "Send", () -> !typed.trim().isEmpty(),
                () -> {
                    if (typed.trim().isEmpty()) return;
                    Friends.add(typed.trim());
                    typed = "";
                    this.rebuildWidgets();
                }).asAction());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        ScreenChrome.background(graphics, this.width, this.height, mouseX, mouseY, delta);
        ScreenChrome.header(graphics, this.font, this.width, "Friends",
                Friends.signedIn() ? "Signed in as " + Friends.myName()
                                   : "Connecting to the friends server");

        int left = panelLeft();

        if (tab == Tab.ADD) {
            // The plate behind the name field, drawn here because the field
            // itself has its own border switched off
            Glass.flat(graphics, left, ScreenChrome.TOP + 34, PANEL_W - 90, ROW_H, Theme.CHIP, 6);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);

        String note = emptyNote();
        if (note != null) {
            graphics.text(this.font, note, left, ScreenChrome.TOP + 44, Theme.TEXT_DIM, false);
        }

        // The last thing the model has to say, whether that is a name that does
        // not exist or a server that cannot be reached
        String said = Friends.status();
        if (said != null && !said.isEmpty()) {
            graphics.text(this.font, said, left,
                    ScreenChrome.bottomRow(this.height) - 14,
                    said.contains("fail") || said.contains("could not")
                            ? 0xFFE8C46A : Theme.TEXT_DIM, false);
        }
    }

    /** What to say when a list is empty, or null when it is not. */
    private String emptyNote() {
        return switch (tab) {
            case FRIENDS -> Friends.friends().isEmpty()
                    ? "No friends yet. Add somebody by their name." : null;
            case REQUESTS -> Friends.incoming().isEmpty() && Friends.outgoing().isEmpty()
                    ? "Nothing waiting." : null;
            case ADD -> null;
        };
    }

    @Override
    public void onClose() {
        Screens.open(parent);
    }
}
