package gg.spaceclient.ui;

import gg.spaceclient.net.Friends;
import gg.spaceclient.util.Screens;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Picking friends for a group: to make a new one, or to bring more people into
 * one that exists.
 *
 * One screen for both, because they are the same question - which of your
 * friends - and only the first needs a name. Only friends are offered: a group
 * is not a way to reach somebody who never agreed to hear from you, and the
 * server would refuse anybody else anyway.
 */
public class GroupPickScreen extends Screen {

    private static final int PANEL_W = 340;
    private static final int ROW_H = 22;
    private static final int GAP = 4;

    private final Screen parent;

    /** The group being added to, or null when making a new one. */
    private final Friends.Group group;

    private final Set<String> picked = new LinkedHashSet<>();
    private EditBox nameBox = null;
    private String name = "";
    private int scroll = 0;

    private GroupPickScreen(Screen parent, Friends.Group group) {
        super(Component.literal(group == null ? "New group" : "Invite"));
        this.parent = parent;
        this.group = group;
    }

    public static GroupPickScreen create(Screen parent) {
        return new GroupPickScreen(parent, null);
    }

    public static GroupPickScreen invite(Screen parent, Friends.Group group) {
        return new GroupPickScreen(parent, group);
    }

    private int panelLeft() { return (this.width - PANEL_W) / 2; }

    /** Friends who could still be added. */
    private List<Friends.Person> candidates() {
        List<Friends.Person> out = new ArrayList<>();
        for (Friends.Person friend : Friends.friends()) {
            boolean inside = false;
            if (group != null) {
                for (Friends.Person member : group.members()) {
                    if (member.uuid().equals(friend.uuid())) inside = true;
                }
            }
            if (!inside) out.add(friend);
        }
        return out;
    }

    private int listTop() {
        return ScreenChrome.TOP + (group == null ? ROW_H + 8 : 0);
    }

    @Override
    protected void init() {
        int left = panelLeft();

        if (group == null) {
            nameBox = new EditBox(this.font, left + 10, ScreenChrome.TOP + (ROW_H - 8) / 2,
                    PANEL_W - 20, 12, Component.literal("Group name"));
            nameBox.setBordered(false);
            nameBox.setMaxLength(32);
            nameBox.setTextColor(Theme.TEXT);
            nameBox.setHint(Component.literal("Group name"));
            nameBox.setValue(name);
            nameBox.setResponder(value -> name = value);
            this.addRenderableWidget(nameBox);
            this.setInitialFocus(nameBox);
        }

        int y = listTop();
        int floor = ScreenChrome.bottomRow(this.height) - 6 - ROW_H;
        List<Friends.Person> people = candidates();
        for (int i = Math.min(scroll, Math.max(0, people.size() - 1)); i < people.size(); i++) {
            if (y > floor) break;
            Friends.Person person = people.get(i);
            // Not an action button: the ON/OFF it draws is the tick
            this.addRenderableWidget(new FlatButton(
                    left, y, PANEL_W, ROW_H,
                    person::name, () -> picked.contains(person.uuid()),
                    () -> {
                        if (!picked.remove(person.uuid())) picked.add(person.uuid());
                    }));
            y += ROW_H + GAP;
        }

        int half = (PANEL_W - GAP) / 2;
        this.addRenderableWidget(new FlatButton(
                left, ScreenChrome.bottomRow(this.height), half, 24,
                () -> "Cancel", () -> false, this::onClose).asAction());
        this.addRenderableWidget(new FlatButton(
                left + half + GAP, ScreenChrome.bottomRow(this.height), PANEL_W - half - GAP, 24,
                () -> group == null ? "Create" : "Add", this::ready, this::confirm).asAction());
    }

    private boolean ready() {
        return !picked.isEmpty() && (group != null || !name.trim().isEmpty());
    }

    private void confirm() {
        if (!ready()) return;
        if (group == null) Friends.createGroup(name.trim(), List.copyOf(picked));
        else Friends.invite(group.id(), List.copyOf(picked));
        onClose();
    }

    // Two shapes, neither annotated, for the same reason as everywhere else
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return scrollBy(scrollY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return scrollBy(amount);
    }

    private boolean scrollBy(double amount) {
        int rows = candidates().size();
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
        ScreenChrome.header(graphics, this.font, this.width,
                group == null ? "New group" : "Invite to " + group.name(),
                picked.isEmpty() ? "Pick friends" : picked.size() + " picked");

        int left = panelLeft();
        if (group == null) {
            Glass.flat(graphics, left, ScreenChrome.TOP, PANEL_W, ROW_H, Theme.CHIP, 6);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);

        if (candidates().isEmpty()) {
            graphics.text(this.font,
                    group == null ? "Add some friends first - a group is made from them."
                                  : "Every friend of yours is in this group already.",
                    left, listTop() + 8, Theme.TEXT_DIM, false);
        }
    }

    @Override
    public void onClose() {
        Screens.open(parent);
    }
}
