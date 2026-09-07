package gg.spaceclient.ui;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.util.Reflect;
import gg.spaceclient.util.Screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * The server list: rows, a selection, and one bar of actions.
 *
 * Vanilla puts six buttons under the list and greys four of them out until
 * something is picked, which means most of the screen is disabled most of the
 * time. Here the actions that need a server sit together and simply fade when
 * nothing is chosen, and the two that never need one - refresh and direct
 * connect - stay bright. The difference is small and it is the whole feel of
 * the screen.
 *
 * <h2>Reflection, again</h2>
 *
 * The server list, the pinger, the editor and the connection flow are four
 * separate APIs and not one of them has been compiled against here. Each is
 * reached through Reflect or Construct and each degrades on its own: a pinger
 * that cannot be built costs the bars, not the list, and a connect call that
 * cannot be resolved falls back to the game's own screen rather than leaving a
 * button that does nothing.
 */
public class ServersScreen extends Screen {

    private static final int ROW_H = 52;
    private static final int ROW_GAP = 6;
    private static final int LIST_W = 460;

    private final Screen parent;

    /** The game's ServerList, kept so edits write back to the same file. */
    private Object serverList = null;
    private Object pinger = null;

    private final java.util.List<ServerRow.Data> rows = new java.util.ArrayList<>();
    private final java.util.List<Object> servers = new java.util.ArrayList<>();
    private final java.util.List<ServerRow> widgets = new java.util.ArrayList<>();

    private int selected = -1;
    private String failure = null;

    private long openedAt = 0L;

    /**
     * The first row on screen, counted in whole rows.
     *
     * Whole rows rather than pixels because nothing here can clip: without a
     * scissor call a half row would simply draw over the buttons below it,
     * which is exactly the overlap this replaces. Stepping by a row means the
     * list is always a set of complete rows inside its box.
     */
    private int scrollRow = 0;

    private final DragScroll drag = new DragScroll();

    /** Pixels pulled since the last whole row was consumed. */
    private int dragCarry = 0;

    public ServersScreen(Screen parent) {
        super(Component.literal("Multiplayer"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (openedAt == 0L) openedAt = System.currentTimeMillis();
        if (serverList == null) loadList();

        widgets.clear();

        int left = (this.width - LIST_W) / 2;
        int top = 92;

        int visible = visibleRows();
        scrollRow = Math.max(0, Math.min(maxScrollRow(), scrollRow));

        for (int slot = 0; slot < visible; slot++) {
            final int index = scrollRow + slot;
            if (index >= rows.size()) break;

            int y = top + slot * (ROW_H + ROW_GAP);

            ServerRow row = new ServerRow(
                    left, y, LIST_W, ROW_H,
                    () -> index < rows.size() ? rows.get(index) : null,
                    () -> selected == index,
                    () -> pick(index));
            row.setAppear(0f);
            widgets.add(row);
            this.addRenderableWidget(row);
        }

        buildActions(left, top);
    }

    private void buildActions(int left, int top) {
        int y = this.height - 40;
        int gap = 6;
        int wide = 96;
        int narrow = 78;

        int x = left;

        this.addRenderableWidget(new FlatButton(
                x, y, wide, 24, () -> "Join", () -> false, this::join).asAction());
        x += wide + gap;

        this.addRenderableWidget(new FlatButton(
                x, y, narrow, 24, () -> "Edit", () -> false, this::edit).asAction());
        x += narrow + gap;

        this.addRenderableWidget(new FlatButton(
                x, y, narrow, 24, () -> "Delete", () -> false, this::delete).asAction());
        x += narrow + gap;

        this.addRenderableWidget(new FlatButton(
                x, y, narrow, 24, () -> "Refresh", () -> false, this::refresh).asAction());

        // The two that never need a selection sit on the row above, away from
        // the ones that do
        int upper = y - 30;
        this.addRenderableWidget(new FlatButton(
                left, upper, 130, 24, () -> "Add server", () -> false, this::add).asAction());

        this.addRenderableWidget(new FlatButton(
                left + 136, upper, 130, 24,
                () -> "Direct connect", () -> false, this::direct).asAction());

        this.addRenderableWidget(new FlatButton(
                left + LIST_W - 90, y, 90, 24,
                () -> "Back", () -> false, this::onClose).asAction());
    }

    /** How many whole rows fit between the heading and the action bar. */
    private int visibleRows() {
        int top = 92;
        int bottom = this.height - 78;
        int room = bottom - top;
        return Math.max(1, room / (ROW_H + ROW_GAP));
    }

    private int maxScrollRow() {
        return Math.max(0, rows.size() - visibleRows());
    }

    private boolean scrollBy(double amount) {
        int max = maxScrollRow();
        if (max <= 0) return false;

        int before = scrollRow;
        scrollRow = Math.max(0, Math.min(max, scrollRow - (int) Math.signum(amount)));
        if (scrollRow != before) this.rebuildWidgets();
        return true;
    }

    // Two shapes, neither annotated: the wheel callback gained a second axis
    // and whichever one this version declares is the one that gets called.
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return scrollBy(scrollY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return scrollBy(amount);
    }

    /** Turns a pull into whole rows, keeping the remainder for the next frame. */
    private void applyDrag() {
        if (!drag.isDragging()) {
            dragCarry = 0;
            return;
        }
        dragCarry += drag.deltaY();

        int step = ROW_H + ROW_GAP;
        while (Math.abs(dragCarry) >= step) {
            // Pulling down moves the list down, which shows earlier rows
            int direction = dragCarry > 0 ? 1 : -1;
            dragCarry -= direction * step;

            int before = scrollRow;
            scrollRow = Math.max(0, Math.min(maxScrollRow(), scrollRow - direction));
            if (scrollRow == before) {
                dragCarry = 0;
                break;
            }
            this.rebuildWidgets();
        }
    }

    private void pick(int index) {
        // A press that ends a pull is not a choice
        if (drag.swallowsClick()) return;

        long now = System.currentTimeMillis();
        if (selected == index && now - lastPick < 400) {
            join();
            return;
        }
        selected = index;
        lastPick = now;
    }

    private long lastPick = 0L;

    // --- reading the list ---

    private void loadList() {
        try {
            Object list = Construct.of("net.minecraft.client.multiplayer.ServerList",
                    Minecraft.getInstance());
            if (list == null) throw new IllegalStateException("no server list");

            Reflect.call(list, "load");
            serverList = list;

            pinger = Construct.of("net.minecraft.client.multiplayer.ServerStatusPinger");

            rebuildRows();
            refresh();

        } catch (Throwable t) {
            failure = "Could not read the server list on this version";
            SpaceClient.LOGGER.warn("Server list unavailable: {}", String.valueOf(t));
        }
    }

    private void rebuildRows() {
        rows.clear();
        servers.clear();
        if (serverList == null) return;

        Object sizeValue = Reflect.call(serverList, "size");
        int size = sizeValue instanceof Number number ? number.intValue() : 0;

        for (int i = 0; i < size; i++) {
            Object server = Reflect.callWith(serverList, "get", i);
            if (server == null) continue;
            servers.add(server);
            rows.add(toRow(server));
        }
    }

    private ServerRow.Data toRow(Object server) {
        String name = string(server, "name", "getName");
        String address = string(server, "ip", "getIp", "getAddress");
        String motd = componentText(readAny(server, "motd", "getMotd", "status", "getStatus"));

        Object pingValue = readAny(server, "ping", "getPing");
        long ping = pingValue instanceof Number number ? number.longValue() : -1L;

        int online = intOf(readAny(server, "playerCount", "getPlayerCount"));
        int max = intOf(readAny(server, "maxPlayers", "getMaxPlayers"));

        return new ServerRow.Data(
                name == null ? "Server" : name,
                address == null ? "" : address,
                motd == null ? "" : motd,
                ping, online, max,
                icon(server, address));
    }

    private static int intOf(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private String string(Object target, String... names) {
        Object value = readAny(target, names);
        return value instanceof String text && !text.isEmpty() ? text : null;
    }

    /** A method with any of these names, or failing that a field. */
    private Object readAny(Object target, String... names) {
        Object value = Reflect.call(target, names);
        if (value != null) return value;

        for (String name : names) {
            Object field = readField(target, name);
            if (field != null) return field;
        }
        return null;
    }

    private static Object readField(Object target, String name) {
        if (target == null) return null;
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    /** Text out of a chat component, or out of a plain string. */
    private String componentText(Object value) {
        if (value == null) return null;
        if (value instanceof String text) return text;

        Object flat = Reflect.call(value, "getString");
        if (flat instanceof String text) {
            // The description is often two lines; the row has space for one
            int newline = text.indexOf('\n');
            return newline > 0 ? text.substring(0, newline) : text;
        }
        return null;
    }

    // --- icons ---

    private static final java.util.Map<Integer, Identifier> ICONS = new java.util.HashMap<>();

    private Identifier icon(Object server, String address) {
        Object value = readAny(server, "iconBytes", "getIconBytes", "getIcon");

        byte[] bytes = null;
        if (value instanceof byte[] raw) {
            bytes = raw;
        } else if (value != null) {
            Object unwrapped = Reflect.call(value, "iconBytes", "bytes", "getBytes");
            if (unwrapped instanceof byte[] raw) bytes = raw;
        }
        if (bytes == null || bytes.length == 0) return null;

        int key = java.util.Arrays.hashCode(bytes);
        Identifier known = ICONS.get(key);
        if (known != null) return known;

        Identifier id = Identifier.fromNamespaceAndPath(
                SpaceClient.MOD_ID, "server_list/" + Integer.toHexString(key));

        Identifier registered = TextureLoader.register(bytes, id);
        if (registered != null) ICONS.put(key, registered);
        return registered;
    }

    // --- the actions ---

    private Object current() {
        return selected >= 0 && selected < servers.size() ? servers.get(selected) : null;
    }

    private void join() {
        Object server = current();
        if (server == null) return;

        String address = string(server, "ip", "getIp", "getAddress");
        if (address == null) return;

        Object parsed = Construct.call(
                "net.minecraft.client.multiplayer.resolver.ServerAddress", "parseString", address);

        // Asked whether it ran, not what it gave back: startConnecting returns
        // void, so its null answer is indistinguishable from a miss. Treating
        // that null as failure is what opened the game's own server list for a
        // moment on every successful join - and left the disconnect screen
        // pointing at it afterwards.
        boolean started = Construct.invoked(
                "net.minecraft.client.gui.screens.ConnectScreen", "startConnecting",
                this, Minecraft.getInstance(), parsed, server);

        if (!started) {
            SpaceClient.LOGGER.warn("Could not start a connection: {}",
                    Construct.describeStatics(
                            "net.minecraft.client.gui.screens.ConnectScreen", "startConnecting"));
            openVanilla();
        }
    }

    private void add() {
        Object blank = Construct.of("net.minecraft.client.multiplayer.ServerData",
                "Minecraft Server", "", null);
        Object screen = Construct.of(
                "net.minecraft.client.gui.screens.EditServerScreen",
                this, (java.util.function.Consumer<Boolean>) ok -> saveEdit(ok, blank, true), blank);

        if (screen instanceof Screen editor) Screens.open(editor);
        else openVanilla();
    }

    private void edit() {
        Object server = current();
        if (server == null) return;

        Object screen = Construct.of(
                "net.minecraft.client.gui.screens.EditServerScreen",
                this, (java.util.function.Consumer<Boolean>) ok -> saveEdit(ok, server, false), server);

        if (screen instanceof Screen editor) Screens.open(editor);
        else openVanilla();
    }

    private void saveEdit(Boolean ok, Object server, boolean isNew) {
        if (Boolean.TRUE.equals(ok) && serverList != null) {
            if (isNew) Reflect.callWith(serverList, "add", server, Boolean.FALSE);
            Reflect.call(serverList, "save");
            rebuildRows();
        }
        Screens.open(this);
        rebuildRows();
    }

    private void delete() {
        Object server = current();
        if (server == null || serverList == null) return;

        Reflect.callWith(serverList, "remove", server);
        Reflect.call(serverList, "save");
        selected = -1;
        rebuildRows();
        this.rebuildWidgets();
    }

    private void direct() {
        Object blank = Construct.of("net.minecraft.client.multiplayer.ServerData",
                "Minecraft Server", "", null);
        Object screen = Construct.of(
                "net.minecraft.client.gui.screens.DirectJoinServerScreen",
                this, (java.util.function.Consumer<Boolean>) ok -> Screens.open(this), blank);

        if (screen instanceof Screen direct) Screens.open(direct);
        else openVanilla();
    }

    /**
     * Asks every server how it is doing.
     *
     * Each ping runs on its own and writes back into the ServerData object the
     * rows already read from, so nothing here waits and the bars fill in as
     * answers arrive.
     */
    private void refresh() {
        if (pinger == null || servers.isEmpty()) return;

        for (Object server : servers) {
            try {
                Reflect.callWith(pinger, "pingServer", server,
                        (Runnable) this::rebuildRows, (Runnable) this::rebuildRows);
            } catch (Throwable ignored) {
                // Older shape with fewer callbacks
                Reflect.callWith(pinger, "pingServer", server, (Runnable) this::rebuildRows);
            }
        }
    }

    private void openVanilla() {
        Object screen = Construct.of(
                "net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen", parent);
        if (screen instanceof Screen fallback) Screens.open(fallback);
    }

    // --- drawing ---

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (!MenuWallpaper.draw(graphics, this.width, this.height, mouseX, mouseY, delta)) {
            Backdrop.draw(graphics, this.width, this.height);
        }
        graphics.fill(0, 0, this.width, this.height, 0x60000000);

        drag.update(mouseX, mouseY);
        applyDrag();

        long now = System.currentTimeMillis() - openedAt;
        for (int i = 0; i < widgets.size(); i++) {
            long start = 60L * i;
            widgets.get(i).setAppear(clampSpan(now, start, start + 260));
        }

        String title = "Multiplayer";
        int titleWidth = this.font.width(title) * 2;
        boolean scaled = Scale.push(graphics, (this.width - titleWidth) / 2, 30, 2f);
        graphics.text(this.font, title,
                scaled ? 0 : (this.width - titleWidth) / 2, scaled ? 0 : 30,
                0xFFFFFFFF, false);
        if (scaled) Scale.pop(graphics);

        String hint = failure != null
                ? failure
                : rows.isEmpty()
                    ? "No servers saved yet - add one below"
                    : rows.size() + (rows.size() == 1 ? " server" : " servers")
                        + (maxScrollRow() > 0
                            ? "  ·  " + (scrollRow + 1) + "-"
                                + Math.min(rows.size(), scrollRow + visibleRows())
                                + "  ·  scroll or drag"
                            : "  ·  double click to join");
        graphics.text(this.font, hint,
                (this.width - this.font.width(hint)) / 2, 56,
                failure != null ? 0xFFE8C46A : 0xFFB9B4DC, false);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private static float clampSpan(long now, long from, long to) {
        if (now <= from) return 0f;
        if (now >= to) return 1f;
        return (now - from) / (float) (to - from);
    }

    @Override
    public void onClose() {
        Screens.open(parent);
    }
}
