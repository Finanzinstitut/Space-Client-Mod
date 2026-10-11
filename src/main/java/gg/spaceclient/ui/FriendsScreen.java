package gg.spaceclient.ui;

import gg.spaceclient.net.Friends;
import gg.spaceclient.util.Screens;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Friends, group chats, the people asking to be friends, and the box to ask
 * somebody.
 *
 * <h2>Tabs over one list</h2>
 *
 * Essential puts requests behind a bell and the search behind a plus, which
 * means two of the things you come here to do are hidden behind an icon. Here
 * they are tabs over one list: whichever you are after, it is one click, and
 * the tab row says whether the others have anything waiting.
 *
 * <h2>Nothing here waits on the network - and nothing goes stale</h2>
 *
 * Every button hands its work to a background thread and returns at once. The
 * screen used to rebuild right then, before the server had answered, and never
 * again - so a request that went through, or a friend who accepted, did not
 * show until the screen was opened again. It now watches the model's revision
 * on every tick and rebuilds the moment anything it shows has changed.
 */
public class FriendsScreen extends Screen {

    private static final int PANEL_W = 400;
    private static final int ROW_H = 24;
    private static final int GAP = 4;

    private enum Tab { FRIENDS, GROUPS, REQUESTS, ADD }

    private final Screen parent;

    /** Kept between openings: coming back to the tab you left is expected. */
    private static Tab tab = Tab.FRIENDS;

    private EditBox nameBox = null;
    private static String typed = "";

    /** What the model looked like when the widgets were last built. */
    private long builtRevision = -1;
    private boolean builtSignedIn = false;

    /** Rows scrolled past at the top of a long list. */
    private int scroll = 0;

    private boolean enterWasDown = true;

    public FriendsScreen(Screen parent) {
        super(Component.literal("Friends"));
        this.parent = parent;
    }

    private int panelLeft() { return (this.width - PANEL_W) / 2; }

    private int listTop() { return ScreenChrome.TOP + 30; }

    /** The lowest a row may start, above the status line and the buttons. */
    private int listFloor() {
        return ScreenChrome.bottomRow(this.height) - (Friends.signedIn() ? 16 : 46) - ROW_H;
    }

    @Override
    protected void init() {
        // Asked for on every opening, answered whenever it is answered
        Friends.refreshSoon();
        builtRevision = Friends.revision();
        builtSignedIn = Friends.signedIn();

        int left = panelLeft();
        buildTabs(left, ScreenChrome.TOP);

        switch (tab) {
            case FRIENDS -> buildFriends(left, listTop());
            case GROUPS -> buildGroups(left, listTop());
            case REQUESTS -> buildRequests(left, listTop());
            case ADD -> buildAdd(left, listTop());
        }

        // Only while the sign-in is stuck: otherwise there is a screen that
        // waits and nothing to press
        if (!Friends.signedIn()) {
            this.addRenderableWidget(new FlatButton(
                    left, ScreenChrome.bottomRow(this.height) - 30, PANEL_W, 24,
                    () -> "Try again now", () -> true,
                    () -> {
                        Friends.tryAgainNow();
                        this.rebuildWidgets();
                    }).asAction());
        }

        if (tab == Tab.GROUPS) {
            // Beside Back rather than above the list: on a normal window the
            // list has room for a handful of rows, and this would take one
            int half = (PANEL_W - GAP) / 2;
            this.addRenderableWidget(new FlatButton(
                    left, ScreenChrome.bottomRow(this.height), half, 24,
                    () -> "Back", () -> false, this::onClose).asAction());
            this.addRenderableWidget(new FlatButton(
                    left + half + GAP, ScreenChrome.bottomRow(this.height), PANEL_W - half - GAP, 24,
                    () -> "+ New group", () -> true,
                    () -> Screens.open(GroupPickScreen.create(this))).asAction());
            return;
        }

        this.addRenderableWidget(new FlatButton(
                left, ScreenChrome.bottomRow(this.height), PANEL_W, 24,
                () -> "Back", () -> false, this::onClose).asAction());
    }

    private void buildTabs(int left, int y) {
        int width = (PANEL_W - GAP * 3) / 4;
        int waiting = Friends.incoming().size();
        int groupUnread = 0;
        for (Friends.Group group : Friends.groups()) groupUnread += Friends.unreadFor(group.key());
        int friendUnread = 0;
        for (Friends.Person person : Friends.friends()) friendUnread += Friends.unreadFor(person.uuid());

        add(left, y, width, friendUnread > 0 ? "Friends (" + friendUnread + ")" : "Friends",
                tab == Tab.FRIENDS, () -> switchTo(Tab.FRIENDS));
        add(left + (width + GAP), y, width, groupUnread > 0 ? "Groups (" + groupUnread + ")" : "Groups",
                tab == Tab.GROUPS, () -> switchTo(Tab.GROUPS));
        add(left + (width + GAP) * 2, y, width, waiting > 0 ? "Requests (" + waiting + ")" : "Requests",
                tab == Tab.REQUESTS, () -> switchTo(Tab.REQUESTS));
        add(left + (width + GAP) * 3, y, PANEL_W - (width + GAP) * 3, "Add",
                tab == Tab.ADD, () -> switchTo(Tab.ADD));
    }

    private void add(int x, int y, int width, String label, boolean on, Runnable press) {
        this.addRenderableWidget(new FlatButton(
                x, y, width, 24, () -> label, () -> on, press).asAction());
    }

    private void switchTo(Tab next) {
        tab = next;
        scroll = 0;
        this.rebuildWidgets();
    }

    /** Whether a row at this height still fits, and the list is past its scroll. */
    private boolean fits(int y) { return y <= listFloor(); }

    private void buildFriends(int left, int y) {
        List<Friends.Person> people = Friends.friends();
        for (int i = Math.min(scroll, Math.max(0, people.size() - 1)); i < people.size(); i++) {
            if (!fits(y)) break;
            Friends.Person person = people.get(i);

            int unread = Friends.unreadFor(person.uuid());
            String label = person.name() + (unread > 0 ? "  (" + unread + ")" : "");

            this.addRenderableWidget(new FlatButton(
                    left, y, PANEL_W - 76, ROW_H,
                    () -> label, () -> unread > 0,
                    () -> Screens.open(FriendChatScreen.with(this, person))).asAction());

            this.addRenderableWidget(new FlatButton(
                    left + PANEL_W - 70, y, 70, ROW_H,
                    () -> "Remove", () -> false,
                    () -> Friends.remove(person.uuid())).asAction());

            y += ROW_H + GAP;
        }
    }

    private void buildGroups(int left, int y) {
        List<Friends.Group> groups = Friends.groups();
        for (int i = Math.min(scroll, Math.max(0, groups.size() - 1)); i < groups.size(); i++) {
            if (!fits(y)) break;
            Friends.Group group = groups.get(i);

            int unread = Friends.unreadFor(group.key());
            String label = group.name() + "  -  " + group.members().size() + " people"
                    + (unread > 0 ? "  (" + unread + ")" : "");

            this.addRenderableWidget(new FlatButton(
                    left, y, PANEL_W, ROW_H,
                    () -> label, () -> unread > 0,
                    () -> Screens.open(FriendChatScreen.with(this, group))).asAction());

            y += ROW_H + GAP;
        }
    }

    private void buildRequests(int left, int y) {
        for (Friends.Person person : Friends.incoming()) {
            if (!fits(y)) break;

            this.addRenderableWidget(new FlatButton(
                    left, y, PANEL_W - 150, ROW_H,
                    person::name, () -> true, () -> {}).asAction());

            this.addRenderableWidget(new FlatButton(
                    left + PANEL_W - 144, y, 70, ROW_H,
                    () -> "Accept", () -> false,
                    () -> Friends.accept(person.uuid())).asAction());

            this.addRenderableWidget(new FlatButton(
                    left + PANEL_W - 70, y, 70, ROW_H,
                    () -> "Decline", () -> false,
                    () -> Friends.remove(person.uuid())).asAction());

            y += ROW_H + GAP;
        }

        // Asked, not yet answered. Shown because "did I actually send that?"
        // is the first thing anybody wonders a minute later.
        for (Friends.Person person : Friends.outgoing()) {
            if (!fits(y)) break;

            this.addRenderableWidget(new FlatButton(
                    left, y, PANEL_W - 76, ROW_H,
                    () -> person.name() + "  -  waiting", () -> false, () -> {}).asAction());

            this.addRenderableWidget(new FlatButton(
                    left + PANEL_W - 70, y, 70, ROW_H,
                    () -> "Cancel", () -> false,
                    () -> Friends.remove(person.uuid())).asAction());

            y += ROW_H + GAP;
        }
    }

    private void buildAdd(int left, int y) {
        nameBox = new EditBox(this.font, left + 10, y + (ROW_H - 8) / 2, PANEL_W - 100, 12,
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
                () -> "Send", () -> !typed.trim().isEmpty(), this::sendRequest).asAction());
    }

    private void sendRequest() {
        String name = typed.trim();
        if (name.isEmpty() || Friends.isBusy()) return;
        Friends.add(name);
        typed = "";
        if (nameBox != null) nameBox.setValue("");
    }

    @Override
    public void tick() {
        // Rebuilt when the model moved, except while a name is being typed:
        // rebuilding there would take the caret away mid-word, and nothing on
        // that tab but the status line depends on the model
        boolean moved = Friends.revision() != builtRevision || Friends.signedIn() != builtSignedIn;
        if (moved && tab != Tab.ADD) this.rebuildWidgets();
        else if (moved) builtRevision = Friends.revision();

        // Enter sends a request, on the edge so a held key sends once
        boolean down = gg.spaceclient.input.RawKeyboard.isDown(
                com.mojang.blaze3d.platform.InputConstants.KEY_RETURN);
        if (down && !enterWasDown && tab == Tab.ADD) sendRequest();
        enterWasDown = down;
    }

    // Two shapes, neither annotated, for the same reason as everywhere else
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return scrollBy(scrollY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return scrollBy(amount);
    }

    private boolean scrollBy(double amount) {
        int rows = switch (tab) {
            case FRIENDS -> Friends.friends().size();
            case GROUPS -> Friends.groups().size();
            default -> 0;
        };
        int next = Math.max(0, Math.min(Math.max(0, rows - 1), scroll - (int) Math.signum(amount)));
        if (next != scroll) {
            scroll = next;
            this.rebuildWidgets();
        }
        return true;
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
            Glass.flat(graphics, left, listTop(), PANEL_W - 90, ROW_H, Theme.CHIP, 6);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);

        String note = emptyNote();
        if (note != null) {
            int y = listTop() + 8;
            graphics.text(this.font, note, left, y, Theme.TEXT_DIM, false);
        }

        // The last thing the model has to say - "asked", a name that does not
        // exist, a server that cannot be reached - above the buttons, not
        // under one of them
        String said = Friends.status();
        if (said != null && !said.isEmpty()) {
            String shown = said;
            while (shown.length() > 4 && this.font.width(shown) > PANEL_W) {
                shown = shown.substring(0, shown.length() - 4) + "..";
            }
            int y = ScreenChrome.bottomRow(this.height) - (Friends.signedIn() ? 12 : 42);
            graphics.text(this.font, shown, left, y,
                    looksLikeTrouble(said) ? 0xFFE8C46A : Theme.TEXT_DIM, false);
        }
    }

    private static boolean looksLikeTrouble(String said) {
        String lower = said.toLowerCase();
        return lower.contains("fail") || lower.contains("could not") || lower.contains("cannot")
                || lower.contains("no player") || lower.contains("refused") || lower.contains("not ");
    }

    /** What to say when a list is empty, or null when it is not. */
    private String emptyNote() {
        return switch (tab) {
            case FRIENDS -> Friends.friends().isEmpty()
                    ? "No friends yet. Add somebody by their name." : null;
            case GROUPS -> Friends.groups().isEmpty()
                    ? "No groups yet. Make one with some of your friends." : null;
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
