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

    /** Servers still waiting to be asked how they are doing. */
    private final java.util.Deque<Object> pending = new java.util.ArrayDeque<>();

    /**
     * New textures registered on this frame.
     *
     * Decoding a PNG and handing it to the GPU is not free, and doing it for
     * every server the first time the list draws is the other half of the
     * stall. One per frame means a full list is dressed within a second
     * without any single frame carrying the whole cost.
     */
    /**
     * Servers whose icon has not been turned into a texture yet.
     *
     * A queue rather than a per frame counter, and that swap is the fix for
     * icons appearing one per visit. The counter assumed icons were asked for
     * every frame, which they were - until the list stopped being rebuilt every
     * frame. Two correct changes that were wrong together: the throttle allowed
     * one per frame, the debounce left one frame's worth of asking, so exactly
     * one icon got made each time the screen opened.
     */
    private final java.util.Deque<Object> iconQueue = new java.util.ArrayDeque<>();

    private final DragScroll drag = new DragScroll();

    /** Pixels pulled since the last whole row was consumed. */
    private int dragCarry = 0;

    // --- carrying a row to a new place ---

    /** What a pull that is already under way is doing. */
    private static final int GESTURE_NONE = 0;
    private static final int GESTURE_CARRY = 1;
    private static final int GESTURE_SCROLL = 2;

    private int gesture = GESTURE_NONE;

    /** The row being carried, as an index into rows, or -1 for none. */
    private int carried = -1;

    /** Where inside the row the pointer took hold of it. */
    private int carryGrab = 0;

    /** Where the carried row is drawn right now. */
    private int carryY = 0;

    /** When the list last stepped itself while a row was held at an edge. */
    private long lastEdgeScroll = 0L;

    /** Something worth saying for a few seconds, under the heading. */
    private String notice = null;
    private long noticeAt = 0L;

    /**
     * Whether the list should be read off disk again before it is shown.
     *
     * Set for every new instance of this screen, which is what makes servers
     * added outside it appear. The list object is shared and long lived - that
     * is deliberate, reading it is slow - but a cache nothing ever refreshes is
     * how a server could be added in the game's own screen, written to
     * servers.dat, and still be missing here. init cannot ask this question
     * itself: it runs again on every scroll and every rebuild, and re-reading
     * the file that often would re-ping every server while you were still
     * moving the wheel.
     */
    private boolean needsReload = true;

    /** True while the worker is re-reading, so nothing else reads the list. */
    private volatile boolean reloading = false;

    public ServersScreen(Screen parent) {
        super(Component.literal("Multiplayer"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        gg.spaceclient.util.Timings.measure("servers:init", this::buildScreen);
    }

    private void buildScreen() {
        if (openedAt == 0L) openedAt = System.currentTimeMillis();
        if (serverList == null) {
            loadList();
        } else if (needsReload) {
            needsReload = false;
            reload();
        }

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

    /**
     * What a pull does, decided once by where it started.
     *
     * A pull that begins on a row carries that row; a pull that begins
     * anywhere else scrolls the list. Deciding once, at the moment the pull is
     * recognised, is the whole trick - asking every frame would let a carry
     * turn into a scroll halfway through, under the hand of someone who is
     * already holding a row.
     */
    private void applyDrag(int mouseX, int mouseY) {
        if (!drag.isDragging()) {
            if (gesture == GESTURE_CARRY) dropCarried();
            gesture = GESTURE_NONE;
            dragCarry = 0;
            return;
        }

        if (gesture == GESTURE_NONE) {
            int grabbed = rowAt(drag.startX(), drag.startY());
            if (grabbed < 0 || rows.size() < 2) {
                gesture = GESTURE_SCROLL;
            } else if (swapMethod() == null) {
                // Said now rather than after the pull. Being allowed to drag a
                // row about and then told it was never going to move is worse
                // than not being able to pick it up.
                gesture = GESTURE_SCROLL;
                say("This version does not let the client reorder servers");
            } else {
                gesture = GESTURE_CARRY;
                carried = grabbed;
                carryGrab = drag.startY() - rowTop(grabbed);
                carryY = rowTop(grabbed);
                raise(grabbed);
            }
        }

        if (gesture == GESTURE_CARRY) {
            carry(mouseY);
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

    // ---------------- carrying a row ----------------

    private int listTop() { return 92; }

    private int rowStep() { return ROW_H + ROW_GAP; }

    private int rowTop(int index) {
        return listTop() + (index - scrollRow) * rowStep();
    }

    /** The row under a point, or -1 for the gap between two of them. */
    private int rowAt(int x, int y) {
        int left = (this.width - LIST_W) / 2;
        if (x < left || x > left + LIST_W) return -1;

        int offset = y - listTop();
        if (offset < 0) return -1;
        if (offset % rowStep() >= ROW_H) return -1;

        int index = scrollRow + offset / rowStep();
        if (index < 0 || index >= rows.size()) return -1;
        if (index - scrollRow >= visibleRows()) return -1;
        return index;
    }

    /**
     * Puts one row last among the widgets, so it draws over the others.
     *
     * The carried row overlaps its neighbours by design, and widgets are drawn
     * in the order they were added - so without this a row carried downwards
     * would slide underneath the rows it is passing, which reads as the list
     * eating it.
     */
    private void raise(int index) {
        ServerRow row = widgetAt(index);
        if (row == null) return;
        this.removeWidget(row);
        this.addRenderableWidget(row);
    }

    private ServerRow widgetAt(int index) {
        int slot = index - scrollRow;
        return slot >= 0 && slot < widgets.size() ? widgets.get(slot) : null;
    }

    /**
     * Follows the pointer, and moves the entry as it goes.
     *
     * The list is reordered while the row is still being held rather than once
     * at the end. That is not a flourish: it keeps the carried row's index and
     * its place on screen the same thing. The version that only worked out
     * where the row would land had to invent a position for it, and when the
     * list scrolled itself at an edge that invented position walked off the top
     * of the screen - the row you were holding simply vanished until you let go.
     *
     * Writing the file is still one thing that happens once, when you let go.
     */
    private void carry(int mouseY) {
        int top = listTop();
        int bottom = top + visibleRows() * rowStep() - ROW_GAP;

        carryY = Math.max(top - ROW_H / 2,
                Math.min(bottom - ROW_H / 2, mouseY - carryGrab));

        int slot = Math.round((carryY - top) / (float) rowStep());
        int target = Math.max(0, Math.min(rows.size() - 1, scrollRow + slot));

        if (target != carried && moveServer(carried, target)) {
            carried = target;
            rebuildRows();
            this.rebuildWidgets();
            raise(carried);
        }

        // Held against an edge with more list behind it: step, slowly enough
        // to see which row is passing
        long now = System.currentTimeMillis();
        if (now - lastEdgeScroll < 140) return;

        int before = scrollRow;
        if (mouseY < top + ROW_H / 2 && scrollRow > 0) {
            scrollRow--;
        } else if (mouseY > bottom - ROW_H / 2 && scrollRow < maxScrollRow()) {
            scrollRow++;
        }

        if (scrollRow != before) {
            lastEdgeScroll = now;
            this.rebuildWidgets();
            raise(carried);
        }
    }

    /** Let go: keep the selection on the row, write the file once. */
    private void dropCarried() {
        int landed = carried;
        carried = -1;
        for (ServerRow row : widgets) row.setLifted(false);

        // A press that carried a row is not the first click of a double click.
        // Without this, nudging the same server up twice in a row counts as
        // one, and the second grab joins it.
        lastPick = 0L;

        if (landed < 0) return;

        selected = landed;
        Construct.invokedOn(serverList, "save");
        rebuildRows();
        this.rebuildWidgets();
    }

    /**
     * The game's swap(int, int), or nothing if this version has no such thing.
     *
     * Found by hand rather than through Construct because Construct fills an
     * int parameter it cannot match with a zero, and swap(0, 0) is a move that
     * silently does nothing - the worst possible answer for a gesture whose
     * whole feedback is that the row moved.
     */
    private Method swapMethod() {
        if (serverList == null) return null;

        Method known = swapCache;
        if (known != null) return known;

        for (Method method : serverList.getClass().getMethods()) {
            if (!method.getName().equals("swap")) continue;
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 2 || params[0] != int.class || params[1] != int.class) continue;
            method.setAccessible(true);
            swapCache = method;
            return method;
        }

        SpaceClient.LOGGER.warn("No ServerList.swap(int, int) on this version");
        return null;
    }

    private Method swapCache = null;

    /**
     * Moves one entry, by walking it past its neighbours one swap at a time.
     *
     * A swap of adjacent entries, repeated, is a move - and swap is the only
     * thing the game's list offers. Nothing is written to disk here; the file
     * is saved once, when the row is let go.
     */
    private boolean moveServer(int from, int to) {
        Method swap = swapMethod();
        if (swap == null) return false;
        if (from < 0 || to < 0 || from >= rows.size() || to >= rows.size()) return false;

        try {
            int step = from < to ? 1 : -1;
            for (int i = from; i != to; i += step) {
                swap.invoke(serverList, i, i + step);
            }
        } catch (Throwable t) {
            SpaceClient.LOGGER.warn("Could not reorder the server list: {}", String.valueOf(t));
            return false;
        }
        return true;
    }

    /** Puts every visible row where this frame says it belongs. */
    private void layoutRows() {
        for (int slot = 0; slot < widgets.size(); slot++) {
            int index = scrollRow + slot;
            ServerRow row = widgets.get(slot);

            boolean carrying = index == carried;
            row.setLifted(carrying);
            row.setY(carrying ? carryY : rowTop(index));
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

    /**
     * The list, kept for the rest of the session.
     *
     * Reading it means reading servers.dat off disk, and doing that on the
     * render thread is what froze the screen on the way in. Once it has been
     * read there is no reason to read it again - edits go through this same
     * object and are written back from it, so a second load would only find
     * what is already here.
     */
    private static Object sharedList = null;
    private static Object sharedPinger = null;

    /** Whether a background read is on its way. */
    private volatile boolean loading = false;

    /**
     * Starts reading the list, off the render thread.
     *
     * The parsing happens on a worker and only the finished object comes back;
     * building the rows from it stays here, on the thread that draws them.
     * That keeps everything touching the screen single threaded while the part
     * that waits on a disk does not.
     */
    private void loadList() {
        if (sharedList != null) {
            serverList = sharedList;
            pinger = sharedPinger;
            rebuildRows();
            if (needsReload) {
                needsReload = false;
                reload();
            } else {
                refresh();
            }
            return;
        }

        // A first read is a read, so the flag has nothing left to ask for
        needsReload = false;

        if (loading) return;
        loading = true;

        Thread worker = new Thread(() -> {
            Object list = null;
            Object ping = null;
            String problem = null;

            try {
                list = Construct.of("net.minecraft.client.multiplayer.ServerList",
                        Minecraft.getInstance());
                if (list == null) throw new IllegalStateException("no server list");

                Construct.invokedOn(list, "load");
                ping = Construct.of("net.minecraft.client.multiplayer.ServerStatusPinger");

            } catch (Throwable t) {
                problem = "Could not read the server list on this version";
                SpaceClient.LOGGER.warn("Server list unavailable: {}", String.valueOf(t));
            }

            final Object builtList = list;
            final Object builtPinger = ping;
            final String builtProblem = problem;

            // Handed back onto the game's own thread. Touching the widgets from
            // the worker would be the kind of bug that shows up once a week on
            // somebody else's machine.
            Minecraft.getInstance().execute(() -> {
                loading = false;

                if (builtProblem != null) {
                    failure = builtProblem;
                    return;
                }

                sharedList = builtList;
                sharedPinger = builtPinger;
                serverList = builtList;
                pinger = builtPinger;

                rebuildRows();
                refresh();
                this.rebuildWidgets();
            });
        }, "space-client-server-list");

        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Reads servers.dat again, off the render thread, into the list already held.
     *
     * The list object stays the same one; only its contents are replaced. That
     * matters because everything else here - the pinger, the icon cache, the
     * editor - is pointed at that object, and swapping it would leave all of
     * them holding the old one.
     *
     * Nothing else may read the list while this runs. A ping answering in the
     * middle of it would ask for row four of a list that has just been emptied,
     * which is an exception on the thread that draws, so the flag below stops
     * the one path that could: the dirty rebuild in the draw loop.
     */
    private void reload() {
        if (serverList == null || reloading) return;
        reloading = true;

        Thread worker = new Thread(() -> {
            try {
                Construct.invokedOn(serverList, "load");
            } catch (Throwable t) {
                SpaceClient.LOGGER.warn("Could not re-read the server list: {}", String.valueOf(t));
            }

            Minecraft.getInstance().execute(() -> {
                reloading = false;
                selected = -1;
                rebuildRows();
                refresh();
                this.rebuildWidgets();
            });
        }, "space-client-server-reload");

        worker.setDaemon(true);
        worker.start();
    }

    private void rebuildRows() {
        gg.spaceclient.util.Timings.measure("servers:rows", this::rebuildRowsNow);
    }

    private void rebuildRowsNow() {
        rows.clear();
        servers.clear();
        if (serverList == null || reloading) return;

        Object sizeValue = Reflect.call(serverList, "size");
        int size = sizeValue instanceof Number number ? number.intValue() : 0;

        for (int i = 0; i < size; i++) {
            // Read defensively: size was true a moment ago, and the list is
            // the game's own object that other screens can also write to
            Object server;
            try {
                server = Reflect.callWith(serverList, "get", i);
            } catch (Throwable ignored) {
                break;
            }
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

    private static String string(Object target, String... names) {
        Object value = readAny(target, names);
        return value instanceof String text && !text.isEmpty() ? text : null;
    }

    /** A method with any of these names, or failing that a field. */
    private static Object readAny(Object target, String... names) {
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

    /**
     * The icon already worked out for a given server object.
     *
     * Keyed by identity rather than by content, and this is the fix for the
     * stutter. Every completed ping rebuilt the whole list, and rebuilding
     * hashed each server's PNG again to look it up - a few kilobytes per
     * server per rebuild, times a rebuild per ping. A dozen servers answering
     * at once turned into a hundred and forty of those in the same second.
     */
    private static final java.util.Map<Object, Identifier> BY_SERVER =
            new java.util.IdentityHashMap<>();

    private Identifier icon(Object server, String address) {
        Identifier settled = BY_SERVER.get(server);
        if (settled != null) return settled;

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
        if (known != null) {
            BY_SERVER.put(server, known);
            return known;
        }

        // Not decoded here. Queued instead, and worked through a couple per
        // frame from the draw loop, so a list of twenty fills in over half a
        // second without any single frame paying for all of them.
        if (!iconQueue.contains(server)) iconQueue.add(server);
        return null;
    }

    /**
     * Actually decodes and registers one server's icon.
     *
     * Split out from the lookup because the two happen at different times now:
     * the lookup runs while rows are built and must be instant, this runs from
     * the draw loop and is allowed to cost something.
     */
    private Identifier registerIcon(Object server) {
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
        if (known != null) {
            BY_SERVER.put(server, known);
            return known;
        }

        Identifier id = Identifier.fromNamespaceAndPath(
                SpaceClient.MOD_ID, "server_list/" + Integer.toHexString(key));

        Identifier registered = TextureLoader.register(bytes, id);
        if (registered != null) {
            ICONS.put(key, registered);
            BY_SERVER.put(server, registered);
        }
        return registered;
    }

    // --- the actions ---

    private Object current() {
        return selected >= 0 && selected < servers.size() ? servers.get(selected) : null;
    }

    /**
     * The last server this client tried to reach.
     *
     * Static because the screen does not survive a connection attempt - the
     * game replaces it while connecting, and what comes back afterwards is a
     * fresh instance with an empty selection. Remembering it here is what lets
     * the button still know where you were.
     */
    private static Object lastJoined = null;
    private static String lastJoinedName = "";

    private void join() {
        joinServer(current());
    }

    /**
     * Joins the last server again, from wherever the button happens to be.
     *
     * Public and static because the place this belongs is the disconnect
     * screen, which is the game's and not ours - that is where you are
     * standing when you need it, and a button in a menu you have to navigate
     * back to first is not a reconnect button.
     */
    public static void reconnectLast() {
        if (lastJoined == null) return;

        String address = string(lastJoined, "ip", "getIp", "getAddress");
        if (address == null) return;

        Object parsed = Construct.call(
                "net.minecraft.client.multiplayer.resolver.ServerAddress", "parseString", address);

        Screen back = new ServersScreen(new MainMenuScreen());
        boolean started = Construct.invoked(
                "net.minecraft.client.gui.screens.ConnectScreen", "startConnecting",
                back, Minecraft.getInstance(), parsed, lastJoined);

        if (!started) SpaceClient.LOGGER.warn("Reconnect could not start a connection");
    }

    /** Whether there is anything to reconnect to. */
    public static boolean hasLastServer() { return lastJoined != null; }

    public static String lastServerName() { return lastJoinedName; }

    private void joinServer(Object server) {
        if (server == null) return;

        lastJoined = server;
        String saved = string(server, "name", "getName");
        lastJoinedName = saved == null ? "server" : saved;

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

    /**
     * A ServerData carrying a name and an address, whatever shape it has here.
     *
     * Built by hand rather than through Construct, because Construct fills a
     * slot from a pool and two of these slots are both Strings - it would fill
     * them in whatever order the pool happened to be in, which is how a server
     * ends up named after its address. This walks the constructor instead:
     * Strings go in the order they are declared, an enum slot gets the entry
     * called OTHER or failing that the first one, and a boolean gets false.
     *
     * The enum is worth the trouble. Its name cannot be written here - it is an
     * inner class that moves - and passing null instead writes a broken line
     * into servers.dat, which is a file the game then refuses to read.
     */
    private Object newServerData(String name, String address) {
        try {
            Class<?> type = Class.forName("net.minecraft.client.multiplayer.ServerData");
            Constructor<?>[] constructors = type.getConstructors();
            java.util.Arrays.sort(constructors,
                    (a, b) -> a.getParameterCount() - b.getParameterCount());

            for (Constructor<?> constructor : constructors) {
                Class<?>[] params = constructor.getParameterTypes();
                Object[] args = new Object[params.length];

                int strings = 0;
                boolean usable = true;

                for (int i = 0; i < params.length; i++) {
                    Class<?> want = params[i];
                    if (want == String.class) {
                        args[i] = strings++ == 0 ? name : address;
                    } else if (want.isEnum()) {
                        args[i] = firstConstant(want);
                        if (args[i] == null) usable = false;
                    } else if (want == boolean.class) {
                        args[i] = Boolean.FALSE;
                    } else if (want == int.class) {
                        args[i] = 0;
                    } else if (want.isPrimitive()) {
                        usable = false;
                    } else {
                        args[i] = null;
                    }
                }

                if (!usable || strings < 2) continue;

                try {
                    return constructor.newInstance(args);
                } catch (Throwable ignored) {
                    // Built but refused what it was given; try the next shape
                }
            }
        } catch (Throwable t) {
            SpaceClient.LOGGER.warn("No usable ServerData constructor: {}", String.valueOf(t));
        }
        return null;
    }

    /** The constant called OTHER, or the first one there is. */
    private static Object firstConstant(Class<?> type) {
        Object[] constants = type.getEnumConstants();
        if (constants == null || constants.length == 0) return null;
        for (Object constant : constants) {
            if (String.valueOf(constant).equalsIgnoreCase("OTHER")) return constant;
        }
        return constants[0];
    }

    /**
     * Writes a String onto a server entry, by setter or by field.
     *
     * Both, because which one exists has changed between versions and neither
     * is worth guessing: the setter is tried first, and the field is walked up
     * the hierarchy after it.
     */
    private static boolean writeString(Object target, String value, String... names) {
        if (target == null) return false;

        for (String name : names) {
            String setter = "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            if (Construct.invokedOn(target, setter, value)) return true;
        }

        for (String name : names) {
            Class<?> current = target.getClass();
            while (current != null) {
                try {
                    Field field = current.getDeclaredField(name);
                    if (field.getType() != String.class) break;
                    field.setAccessible(true);
                    field.set(target, value);
                    return true;
                } catch (NoSuchFieldException ignored) {
                    current = current.getSuperclass();
                } catch (Throwable ignored) {
                    break;
                }
            }
        }
        return false;
    }

    private void add() {
        Screens.open(new ServerEditScreen(this, ServerEditScreen.Mode.ADD, "", "",
                (name, address) -> saveNew(name, address)));
    }

    private void edit() {
        Object server = current();
        if (server == null) return;

        String name = string(server, "name", "getName");
        String address = string(server, "ip", "getIp", "getAddress");

        Screens.open(new ServerEditScreen(this, ServerEditScreen.Mode.EDIT,
                name == null ? "" : name,
                address == null ? "" : address,
                (newName, newAddress) -> saveEdit(server, newName, newAddress)));
    }

    /** A new entry, from the two things the editor collected. */
    private void saveNew(String name, String address) {
        Object server = newServerData(name, address);

        if (server == null || serverList == null) {
            say("This version would not let the client build a server entry");
            SpaceClient.LOGGER.warn("Add server failed: serverData={} list={}",
                    server != null, serverList != null);
            Screens.open(this);
            return;
        }

        // Two shapes: the newer one also says whether it goes on top
        if (!Construct.invokedOn(serverList, "add", server, Boolean.FALSE)) {
            if (!Construct.invokedOn(serverList, "add", server)) {
                say("Could not add the server to the list on this version");
                Screens.open(this);
                return;
            }
        }

        Construct.invokedOn(serverList, "save");
        rebuildRows();
        selected = rows.size() - 1;
        scrollRow = Math.max(0, maxScrollRow());
        refresh();
        Screens.open(this);
    }

    /** An existing entry, written back in place. */
    private void saveEdit(Object server, String name, String address) {
        boolean wroteName = writeString(server, name, "name");
        boolean wroteAddress = writeString(server, address, "ip", "address");

        if (!wroteName || !wroteAddress) {
            say("Could not write the server entry on this version");
            SpaceClient.LOGGER.warn("Edit server: name written={} address written={}",
                    wroteName, wroteAddress);
        } else {
            Construct.invokedOn(serverList, "save");
        }

        rebuildRows();
        refresh();
        Screens.open(this);
    }

    /** Says something under the heading for a few seconds. */
    private void say(String text) {
        notice = text;
        noticeAt = System.currentTimeMillis();
    }

    private void delete() {
        Object server = current();
        if (server == null || serverList == null) return;

        Construct.invokedOn(serverList, "remove", server);
        Construct.invokedOn(serverList, "save");

        selected = -1;
        scrollRow = Math.max(0, Math.min(scrollRow, Math.max(0, rows.size() - 2)));
        rebuildRows();
        this.rebuildWidgets();
    }

    /**
     * Type an address, join it once, save nothing.
     *
     * The same screen as adding, in its third mode. It used to be the game's
     * DirectJoinServerScreen, reached the same way the editor was and able to
     * miss in the same way - and missing here dropped you into the game's
     * server list, which is not what either button said it would do.
     */
    private void direct() {
        Screens.open(new ServerEditScreen(this, ServerEditScreen.Mode.DIRECT, "", "",
                (name, address) -> {
                    Object server = newServerData(name, address);
                    if (server == null) {
                        say("This version would not let the client build a server entry");
                        Screens.open(this);
                        return;
                    }
                    joinServer(server);
                }));
    }

    /**
     * Asks every server how it is doing.
     *
     * Each ping runs on its own and writes back into the ServerData object the
     * rows already read from, so nothing here waits and the bars fill in as
     * answers arrive.
     */
    /** Queues every server for another ping, a couple per frame. */
    private void refresh() {
        pending.clear();
        pending.addAll(servers);
    }

    /**
     * Turns a couple of queued icons into textures.
     *
     * Two per frame: enough that a full list is dressed within a second, few
     * enough that no frame carries more than two PNG decodes and uploads.
     */
    private void drainIcons() {
        for (int i = 0; i < 2 && !iconQueue.isEmpty(); i++) {
            Object server = iconQueue.poll();
            if (server == null) continue;

            Identifier made = gg.spaceclient.util.Timings.measure(
                    "servers:icon", () -> registerIcon(server));

            // Only worth rebuilding when something actually changed
            if (made != null) rowsDirty = true;
        }
    }

    /**
     * Threads for pinging, so no ping ever runs on the thread that draws.
     *
     * The pings were meant to be asynchronous already - the game's pinger
     * submits its own work - but which method the reflective call lands on is
     * not something this code chooses. If it picks one that resolves a hostname
     * before handing off, that resolution happens wherever it was called from,
     * and a handful of servers that do not answer is several seconds of frozen
     * window. Calling from a worker makes that impossible rather than unlikely.
     */
    private static final java.util.concurrent.ExecutorService PING_POOL =
            java.util.concurrent.Executors.newFixedThreadPool(3, runnable -> {
                Thread thread = new Thread(runnable, "space-client-ping");
                thread.setDaemon(true);
                return thread;
            });

    /** Hands the next couple of servers to the pool. */
    private void drainPings() {
        if (pinger == null) { pending.clear(); return; }

        for (int i = 0; i < 2 && !pending.isEmpty(); i++) {
            Object server = pending.poll();
            if (server == null) continue;

            try {
                PING_POOL.submit(() -> ping(server));
            } catch (Throwable ignored) {
                // A refused ping costs the bars for that row, nothing else
            }
        }
    }

    /**
     * Marks the list as needing a rebuild without doing one.
     *
     * Pings answer on their own threads and each answer used to rebuild the
     * whole list immediately - so twelve servers replying meant twelve full
     * rebuilds, several of them inside the same frame. The flag collapses a
     * burst into one rebuild on the next draw, which is the soonest anything
     * could be seen anyway.
     */
    private volatile boolean rowsDirty = false;

    private void markDirty() { rowsDirty = true; }

    private void ping(Object server) {
        if (pinger == null) return;

        // Asked whether it ran: callWith cannot say, so the shorter shape
        // used to be skipped even when the longer one had not matched
        if (Construct.invokedOn(pinger, "pingServer", server,
                (Runnable) this::markDirty, (Runnable) this::markDirty)) {
            return;
        }
        Construct.invokedOn(pinger, "pingServer", server, (Runnable) this::markDirty);
    }

    /**
     * Hands over to the game's own server list.
     *
     * Flagged, because the client otherwise turns that screen back into this
     * one wherever it appears. Without the flag a screen this one cannot do
     * would bounce straight back here and the button would look broken.
     */
    private void openVanilla() {
        Object screen = Construct.of(
                "net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen", parent);
        if (screen instanceof Screen fallback) {
            allowVanillaOnce = true;
            Screens.open(fallback);
        }
    }

    /** Lets the next vanilla server list through untouched. */
    private static boolean allowVanillaOnce = false;

    /**
     * Whether the client should replace a vanilla server list it did not open.
     *
     * The disconnect screen sends you back to the game's list rather than to
     * whichever screen you left from, so returning from a server landed you in
     * vanilla's. Catching it wherever it appears is more reliable than trying
     * to talk the disconnect screen into pointing somewhere else.
     */
    public static boolean shouldReplaceVanillaList() {
        if (allowVanillaOnce) {
            allowVanillaOnce = false;
            return false;
        }
        return true;
    }

    // --- drawing ---

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (!MenuWallpaper.draw(graphics, this.width, this.height, mouseX, mouseY, delta)) {
            Backdrop.draw(graphics, this.width, this.height);
        }
        graphics.fill(0, 0, this.width, this.height, 0x60000000);

        drainIcons();

        if (rowsDirty) {
            rowsDirty = false;
            rebuildRows();
        }
        drainPings();

        drag.update(mouseX, mouseY);
        applyDrag(mouseX, mouseY);
        layoutRows();

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

        boolean shouting = notice != null && System.currentTimeMillis() - noticeAt < 5000;
        if (!shouting) notice = null;

        String hint = shouting
                ? notice
                : loading || reloading
                ? "Reading your server list"
                : failure != null
                ? failure
                : rows.isEmpty()
                    ? "No servers saved yet - add one below"
                    : carried >= 0
                    ? "Drop it where you want it"
                    : rows.size() + (rows.size() == 1 ? " server" : " servers")
                        + "  ·  double click to join"
                        + (rows.size() > 1 ? "  ·  hold and drag to reorder" : "")
                        + (maxScrollRow() > 0
                            ? "  ·  " + (scrollRow + 1) + "-"
                                + Math.min(rows.size(), scrollRow + visibleRows())
                            : "");
        graphics.text(this.font, hint,
                (this.width - this.font.width(hint)) / 2, 56,
                shouting || failure != null ? 0xFFE8C46A : 0xFFB9B4DC, false);

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
