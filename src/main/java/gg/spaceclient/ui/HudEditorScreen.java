package gg.spaceclient.ui;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.input.RawKeyboard;
import gg.spaceclient.module.HudModule;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Arranging the HUD: pick an element up and pull it where you want it.
 *
 * <h2>What was wrong with the old one</h2>
 *
 * It had a mode switch - Move, Resize, Lock - and you clicked once to pick an
 * element up, moved the mouse with the button released, and clicked again to
 * put it down. That is not how anything else on a computer moves, so every
 * visit started by working out what the last click had done; an element
 * followed the pointer when you thought you had dropped it, and the only way
 * to change its size was to hit a fourteen pixel corner. The modes made it
 * worse rather than better: a click meant three different things depending on
 * a switch at the top of the screen that is nowhere near where you are looking.
 *
 * <h2>What this is instead</h2>
 *
 * Hold the button and pull. No modes: a press picks up whatever is under the
 * pointer, the element follows while the button is held, and releasing puts it
 * down - which is the gesture the same person has already used everywhere else
 * today. Nothing has to be aimed at either: the panel on the left lists every
 * element there is, and the bar along the bottom resizes, locks, centres and
 * resets whichever one is selected, so the corner grip and the scroll wheel
 * are shortcuts rather than the only way through.
 *
 * <h2>Why the button is read from GLFW</h2>
 *
 * Holding a button is a drag, and the drag callback changed shape in this
 * version along with the rest of the input API - naming one of those
 * signatures has already cost a build once. The window handle and the button
 * state come from LWJGL, which has not moved, and the cursor position is
 * handed to every draw call, so the whole gesture is assembled out of things
 * that are known to work. It is the same reasoning DragScroll is built on.
 */
public class HudEditorScreen extends Screen {

    /** How close an edge has to be to a line before it snaps, in pixels. */
    private static final int SNAP = 6;

    /** The step used when the grid is switched on. */
    private static final int GRID = 8;

    /** The corner grip on the selected element. */
    private static final int GRIP = 10;

    private static final int PANEL_W = 176;
    private static final int ROW_H = 22;
    /**
     * Two rows: the size controls, then the nine positions.
     *
     * One row was the first attempt and it did not survive being measured. The
     * eight controls came to 680 pixels, so on any window narrower than that -
     * which includes the 640 the game gives you at a gui scale of three - the
     * last of them were drawn off the edge of the screen.
     */
    private static final int BAR_H = 66;

    private final Screen parent;

    private HudModule selected;
    private HudModule dragging;
    private HudModule resizing;

    /** Where inside the element the pointer took hold of it. */
    private int grabX;
    private int grabY;

    private boolean wasDown = false;

    /**
     * Whether the first frame has read the button yet.
     *
     * This screen is opened by clicking something - a tile in the settings hub,
     * a line in the menu's rail - and a Button fires on the press, not the
     * release. So the editor's very first frame can run while that same click
     * is still held down, and without this the poll would read it as a fresh
     * press and grab whatever element happened to be under the cursor. The
     * first frame therefore only records the state; it never acts on it.
     */
    private boolean primed = false;

    /** True once the pointer has moved far enough for a press to be a drag. */
    private boolean moved = false;
    private int pressX;
    private int pressY;

    /** When the last drag ended, so a button under the release does not fire. */
    private long droppedAt = 0L;

    private boolean snapping = true;
    private boolean gridOn = false;
    private boolean panelOpen = true;

    /** Key repeat for the arrow keys, which nudge by a pixel. */
    private long nudgedAt = 0L;
    private boolean nudgeFresh = true;

    private final List<ElementRow> rows = new ArrayList<>();
    private SliderRow sizeSlider;

    /**
     * First element shown in the panel.
     *
     * The list has to scroll, and the earlier version simply stopped adding
     * rows once it ran out of room - so on a short window the elements at the
     * bottom of the list had no row at all. Since the whole point of the panel
     * is that every element is reachable without aiming at it, an element that
     * is not listed is the one failure it cannot afford.
     */
    private int panelScroll = 0;

    /** Eased highlight per element, so nothing switches on in one frame. */
    private final Map<HudModule, Float> glow = new HashMap<>();

    /** Guides to draw this frame, in screen coordinates. */
    private final List<Integer> guidesX = new ArrayList<>();
    private final List<Integer> guidesY = new ArrayList<>();

    private final long openedAt = System.currentTimeMillis();

    public HudEditorScreen(Screen parent) {
        super(Component.literal("HUD Editor"));
        this.parent = parent;
    }

    // ---------------- geometry ----------------

    private int panelX() { return 16; }
    private int panelY() { return 64; }

    /**
     * As tall as its contents and no taller.
     *
     * The first version reserved everything between the top bar and the bottom
     * one, which blacked out the left third of the screen - and the left third
     * of the screen is where most people keep their HUD, so the editor was
     * covering the very thing it exists to arrange. It is still in the way of
     * something, which is what the Elements chip and the list itself are for:
     * an element hidden behind the panel is still selectable by name.
     */
    private int panelH() {
        return 16 + panelRows() * (ROW_H + 2);
    }

    /** How many rows the panel shows, which is as many as fit. */
    private int panelRows() {
        int room = this.height - panelY() - BAR_H - 40;
        int fits = Math.max(1, (room - 16) / (ROW_H + 2));
        return Math.min(elements().size(), fits);
    }

    private int maxPanelScroll() {
        return Math.max(0, elements().size() - panelRows());
    }

    /** High enough that the hint line below it is not inside the bar. */
    private int barY()   { return this.height - BAR_H - 26; }

    /**
     * Where the editor's own controls are, collected as they are built.
     *
     * A press inside one of these never picks an element up - otherwise a
     * click aimed at a button would also grab whatever HUD element happens to
     * lie underneath it, and the element would fly off with the pointer while
     * the button was being pressed.
     *
     * Rectangles rather than bands, and that is the point. The first version
     * reserved everything above y=44 and everything below the bar, which took
     * the whole top strip of the screen away - and the top strip is where the
     * corner elements live, so an element sitting near the top edge could not
     * be dragged at all. The top bar is three chips and a fourth in the far
     * corner; everything between them is world, and now it behaves like world.
     */
    private final List<int[]> chrome = new ArrayList<>();

    private void reserve(int x, int y, int w, int h) {
        chrome.add(new int[]{ x - 4, y - 4, x + w + 4, y + h + 4 });
    }

    private boolean overChrome(int x, int y) {
        for (int[] box : chrome) {
            if (x >= box[0] && x < box[2] && y >= box[1] && y < box[3]) return true;
        }
        return false;
    }

    /** True while a press should be ignored because it ended a pull. */
    private boolean justDragged() {
        return moved || System.currentTimeMillis() - droppedAt < 160L;
    }

    private List<HudModule> elements() {
        return SpaceClient.getModuleManager().getHudModules();
    }

    // ---------------- build ----------------

    @Override
    protected void init() {
        rows.clear();
        chrome.clear();
        sizeSlider = null;

        buildTopBar();
        if (panelOpen) buildPanel();
        buildBottomBar();
    }

    private void buildTopBar() {
        int x = 16;
        x += chip(x, 16, 78, () -> "Elements", () -> panelOpen,
                () -> { panelOpen = !panelOpen; this.rebuildWidgets(); });
        x += chip(x, 16, 64, () -> "Snap", () -> snapping,
                () -> snapping = !snapping);
        x += chip(x, 16, 58, () -> "Grid", () -> gridOn,
                () -> gridOn = !gridOn);

        // The way back out of a mistake, and the reason it is not hidden: the
        // one state this editor can reach that it cannot easily undo is an
        // element scaled past the edge of the screen, which then has no corner
        // left to grab.
        chip(this.width - 16 - 96, 16, 96, () -> "Reset all", () -> false, () -> {
            for (HudModule module : elements()) {
                module.setScale(1f);
                module.setLocked(false);
            }
            SpaceClient.getConfigManager().save();
            this.rebuildWidgets();
        });
    }

    /** Adds a chip and returns the width it used, so a row needs no table. */
    private int chip(int x, int y, int w, java.util.function.Supplier<String> label,
                     java.util.function.BooleanSupplier on, Runnable action) {
        this.addRenderableWidget(new NavButton(x, y, w, 20, NavButton.Style.CHIP,
                label, on, () -> { if (!justDragged()) action.run(); }));
        reserve(x, y, w, 20);
        return w + 6;
    }

    /**
     * The list of every HUD element there is.
     *
     * The point of it is that nothing in this editor requires aim. An element
     * can be selected, switched on, and locked from a row that is always the
     * same size in the same place - which matters most for exactly the elements
     * that are hardest to click on the screen itself, the ones that are two
     * pixels tall or sitting underneath something else.
     */
    private void buildPanel() {
        int x = panelX() + 6;
        int w = PANEL_W - 12;
        int y = panelY() + 8;

        List<HudModule> all = elements();
        panelScroll = Math.max(0, Math.min(panelScroll, maxPanelScroll()));

        int last = Math.min(all.size(), panelScroll + panelRows());
        for (int i = panelScroll; i < last; i++) {
            ElementRow row = new ElementRow(all.get(i), x, y, w, ROW_H);
            this.addRenderableWidget(row);
            rows.add(row);
            y += ROW_H + 2;
        }
        reserve(panelX(), panelY(), PANEL_W, panelH());
    }

    /**
     * The bar for whichever element is selected.
     *
     * Rebuilt whenever the selection changes, because the slider carries the
     * selected element's size as its starting position and a slider cannot be
     * told a new one on this version - setValue is not a method it is proven to
     * have, and guessing at it is how a screen ends up silently doing nothing.
     */
    private void buildBottomBar() {
        if (selected == null) return;

        HudModule module = selected;
        int top = barY();

        // 0 to 250 rather than 50 to 300: the slider's knob sits at value over
        // maximum, so a range that does not start at zero puts the knob in the
        // wrong place at every position.
        int initial = Math.round((module.getScale() - HudModule.MIN_SCALE) * 100f);
        sizeSlider = new SliderRow(140, top + 6, 150, 16, "Size", initial,
                Math.round((HudModule.MAX_SCALE - HudModule.MIN_SCALE) * 100f),
                value -> {
                    if (module.isLocked()) return;
                    module.setScale(HudModule.MIN_SCALE + value / 100f);
                    SpaceClient.getConfigManager().save();
                })
                .withFormat(value -> Math.round((HudModule.MIN_SCALE + value / 100f) * 100) + "%");
        this.addRenderableWidget(sizeSlider);

        int x = 298;
        x += chip(x, top + 6, 68, () -> module.isLocked() ? "Locked" : "Lock",
                module::isLocked,
                () -> {
                    module.setLocked(!module.isLocked());
                    SpaceClient.getConfigManager().save();
                });

        chip(x, top + 6, 70, () -> "Size 100%", () -> false,
                () -> {
                    if (module.isLocked()) return;
                    module.setScale(1f);
                    SpaceClient.getConfigManager().save();
                    this.rebuildWidgets();
                });

        // The nine positions, as three across and three down. They place an
        // element exactly in a corner or exactly in the middle, which is the
        // one thing nobody can do by holding a mouse still.
        int align = 56;
        String[] across = { "Left", "Centre", "Right" };
        for (int i = 0; i < across.length; i++) {
            final int at = i;
            align += chip(align, top + 36, 50, () -> across[at], () -> false,
                    () -> alignX(module, at * 0.5f));
        }
        align += 8;
        String[] down = { "Top", "Middle", "Bottom" };
        for (int i = 0; i < down.length; i++) {
            final int at = i;
            align += chip(align, top + 36, 50, () -> down[at], () -> false,
                    () -> alignY(module, at * 0.5f));
        }
        reserve(8, top - 4, this.width - 16, BAR_H);
    }

    private void alignX(HudModule module, float where) {
        if (module.isLocked()) return;
        int free = Math.max(0, this.width - module.getScaledWidth());
        module.setPosition(free * where / this.width, module.getYPercent());
        SpaceClient.getConfigManager().save();
    }

    private void alignY(HudModule module, float where) {
        if (module.isLocked()) return;
        int free = Math.max(0, this.height - module.getScaledHeight());
        module.setPosition(module.getXPercent(), free * where / this.height);
        SpaceClient.getConfigManager().save();
    }

    private void select(HudModule module) {
        if (selected == module) return;
        selected = module;
        this.rebuildWidgets();
    }

    // ---------------- pointer ----------------

    /**
     * The whole gesture, read once a frame.
     *
     * Press, hold, release - in that order and in one place, so there is no
     * mode to get out of step with. A press on the grip of the selected element
     * resizes; a press on any element body moves it; a press on nothing clears
     * the selection.
     */
    private void pointer(int mouseX, int mouseY) {
        boolean down = RawKeyboard.isMouseDown(GLFW.GLFW_MOUSE_BUTTON_LEFT);

        if (!primed) {
            primed = true;
            wasDown = down;
            return;
        }

        if (down && !wasDown) {
            pressX = mouseX;
            pressY = mouseY;
            moved = false;

            if (!overChrome(mouseX, mouseY)) {
                if (selected != null && !selected.isLocked() && overGrip(selected, mouseX, mouseY)) {
                    resizing = selected;
                } else {
                    HudModule under = elementAt(mouseX, mouseY);
                    if (under != null) {
                        select(under);
                        if (!under.isLocked()) {
                            dragging = under;
                            grabX = mouseX - under.getX(this.width);
                            grabY = mouseY - under.getY(this.height);
                        }
                    } else {
                        select(null);
                    }
                }
            }
        }

        if (down) {
            // Five pixels of slop, so a shaky click is still a click. Without
            // it every selection would move the element by a pixel or two and
            // a layout would drift every time it was looked at.
            if (!moved && (Math.abs(mouseX - pressX) > 4 || Math.abs(mouseY - pressY) > 4)) {
                moved = true;
            }
            if (dragging != null && moved) moveTo(mouseX, mouseY);
            if (resizing != null) resizeTo(mouseX);
        }

        if (!down && wasDown) {
            boolean wasResizing = resizing != null;
            if (dragging != null || resizing != null) {
                SpaceClient.getConfigManager().save();
            }
            // Stamped for any pull, not only one that carried an element. A
            // drag across empty space ends over a button just as easily, and
            // without the stamp that release would press it.
            if (moved) droppedAt = System.currentTimeMillis();
            dragging = null;
            resizing = null;
            moved = false;

            // The slider was built with the size the element had when it was
            // selected, and this version gives no way to tell one a new
            // position - so after a corner drag it is rebuilt rather than left
            // pointing somewhere the element no longer is.
            if (wasResizing) this.rebuildWidgets();
        }

        wasDown = down;
    }

    private void moveTo(int mouseX, int mouseY) {
        int wantX = mouseX - grabX;
        int wantY = mouseY - grabY;

        if (gridOn) {
            wantX = Math.round(wantX / (float) GRID) * GRID;
            wantY = Math.round(wantY / (float) GRID) * GRID;
        }
        if (snapping) {
            wantX = snap(wantX, dragging.getScaledWidth(), this.width, true, dragging);
            wantY = snap(wantY, dragging.getScaledHeight(), this.height, false, dragging);
        }

        dragging.setPosition(wantX / (float) this.width, wantY / (float) this.height);
    }

    private void resizeTo(int mouseX) {
        int originX = resizing.getX(this.width);
        int contentWidth = Math.max(1, resizing.getWidth());
        resizing.setScale((mouseX - originX) / (float) contentWidth);
    }

    private boolean overPanel(int x, int y) {
        return x >= panelX() && x < panelX() + PANEL_W
                && y >= panelY() && y < panelY() + panelH();
    }

    private boolean overGrip(HudModule module, int mouseX, int mouseY) {
        int x = module.getX(this.width) + Math.max(24, module.getScaledWidth()) + 2 - GRIP;
        int y = module.getY(this.height) + Math.max(12, module.getScaledHeight()) + 2 - GRIP;
        return mouseX >= x - 2 && mouseX <= x + GRIP + 2
                && mouseY >= y - 2 && mouseY <= y + GRIP + 2;
    }

    /**
     * The element under the pointer, topmost first.
     *
     * Walked backwards so that when two elements overlap, the one drawn last -
     * the one you can actually see - is the one that gets picked up.
     */
    private HudModule elementAt(int mouseX, int mouseY) {
        List<HudModule> all = elements();
        for (int i = all.size() - 1; i >= 0; i--) {
            HudModule module = all.get(i);
            if (!module.isEnabled()) continue;
            int x = module.getX(this.width);
            int y = module.getY(this.height);
            if (mouseX >= x && mouseX <= x + Math.max(24, module.getScaledWidth())
                    && mouseY >= y && mouseY <= y + Math.max(12, module.getScaledHeight())) {
                return module;
            }
        }
        return null;
    }

    // ---------------- keyboard ----------------

    /**
     * The arrow keys move the selection by a pixel, or by eight with control.
     *
     * Polled rather than handled, for the same reason the mouse button is: the
     * key event types changed in this version. A repeat is built in by hand
     * because a poll has no idea the key was already down last frame - without
     * one, holding an arrow would move the element at the frame rate, which at
     * two hundred frames a second is the width of the screen in a second.
     */
    private void nudge() {
        if (selected == null || selected.isLocked()) return;

        int dx = 0;
        int dy = 0;
        if (RawKeyboard.isDown(GLFW.GLFW_KEY_LEFT)) dx -= 1;
        if (RawKeyboard.isDown(GLFW.GLFW_KEY_RIGHT)) dx += 1;
        if (RawKeyboard.isDown(GLFW.GLFW_KEY_UP)) dy -= 1;
        if (RawKeyboard.isDown(GLFW.GLFW_KEY_DOWN)) dy += 1;

        if (dx == 0 && dy == 0) {
            nudgeFresh = true;
            return;
        }

        long now = System.currentTimeMillis();
        long wait = nudgeFresh ? 0L : 45L;
        if (now - nudgedAt < wait) return;

        // A pause before the repeat starts, so a tap is one pixel rather than
        // the first of a stream
        if (nudgeFresh) {
            nudgeFresh = false;
            nudgedAt = now + 240L;
        } else {
            nudgedAt = now;
        }

        int step = RawKeyboard.isDown(GLFW.GLFW_KEY_LEFT_CONTROL) ? GRID : 1;
        int x = selected.getX(this.width) + dx * step;
        int y = selected.getY(this.height) + dy * step;
        selected.setPosition(x / (float) this.width, y / (float) this.height);
        SpaceClient.getConfigManager().save();
    }

    // ---------------- snapping ----------------

    /**
     * Pulls a moving edge onto a nearby line.
     *
     * Lining elements up by eye is the slowest part of arranging a HUD: two
     * pixels out is invisible while placing something and obvious once the game
     * is running. The candidates are the screen's edges and centre and the
     * edges and centre of every other element - which are the lines a person
     * was aiming for anyway.
     */
    private int snap(int value, int size, int screenSize, boolean horizontal, HudModule moving) {
        List<Integer> lines = new ArrayList<>();
        lines.add(0);
        lines.add(screenSize / 2 - size / 2);
        lines.add(screenSize - size);

        for (HudModule other : elements()) {
            if (other == moving || !other.isEnabled()) continue;
            int start = horizontal ? other.getX(this.width) : other.getY(this.height);
            int extent = horizontal ? other.getScaledWidth() : other.getScaledHeight();
            lines.add(start);                         // near edges flush
            lines.add(start + extent - size);         // far edges flush
            lines.add(start + extent / 2 - size / 2); // centres in line
        }

        for (int line : lines) {
            if (Math.abs(value - line) <= SNAP) {
                if (horizontal) guidesX.add(line <= value ? line : line + size);
                else guidesY.add(line <= value ? line : line + size);
                return line;
            }
        }
        return value;
    }

    // ---------------- paint ----------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float age = Ease.clamp01((System.currentTimeMillis() - openedAt) / 200f);
        float shown = Ease.outCubic(age);

        graphics.fill(0, 0, this.width, this.height, (int) (0xA0 * shown) << 24);

        guidesX.clear();
        guidesY.clear();

        pointer(mouseX, mouseY);
        nudge();

        drawElements(graphics, mouseX, mouseY, delta);
        drawGuides(graphics);

        // Panels over the elements, so nothing being arranged can cover a
        // control that is supposed to be reachable
        // No caption over the panel: the chip that opens it is already called
        // Elements, and a second label collided with the screen's own title.
        if (panelOpen) {
            Glass.pill(graphics, panelX(), panelY(), PANEL_W, panelH(), Theme.PANEL, 12);

            // A thumb whenever the list is longer than the panel. Without it
            // the list looks complete when it is not, and somebody hunting for
            // an element that is two rows further down has no reason to think
            // the wheel would help.
            int max = maxPanelScroll();
            if (max > 0) {
                int trackTop = panelY() + 8;
                int trackHeight = panelH() - 16;
                int thumb = Math.max(16, trackHeight * panelRows() / elements().size());
                int offset = Math.round((trackHeight - thumb) * (panelScroll / (float) max));
                Glass.flat(graphics, panelX() + PANEL_W - 6, trackTop, 2, trackHeight,
                        0x30FFFFFF, 1);
                Glass.flat(graphics, panelX() + PANEL_W - 6, trackTop + offset, 2, thumb,
                        Theme.accent(), 1);
            }
        }
        if (selected != null) {
            Glass.pill(graphics, 8, barY() - 4, this.width - 16, BAR_H, Theme.PANEL, 12);
            // The name and where it sits. The size is not repeated here: the
            // slider two inches to the right already carries it, and two
            // numbers that have to agree are two numbers that can disagree.
            String name = ToggleRow.fit(this.font, selected.getName(), 110);
            graphics.text(this.font, name, 18, barY() + 10, Theme.TEXT, false);
            graphics.text(this.font,
                    selected.getX(this.width) + ", " + selected.getY(this.height),
                    18, barY() + 24, Theme.OFF, false);
            graphics.text(this.font, "Align", 18, barY() + 40, Theme.OFF, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);

        header(graphics);

        if (age < 1f) {
            graphics.fill(0, 0, this.width, this.height, (int) ((1f - shown) * 255) << 24);
        }
    }

    private void drawElements(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        for (HudModule module : elements()) {
            if (!module.isEnabled()) continue;

            int x = module.getX(this.width);
            int y = module.getY(this.height);
            int w = Math.max(24, module.getScaledWidth());
            int h = Math.max(12, module.getScaledHeight());

            boolean hovered = !overChrome(mouseX, mouseY)
                    && mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
            boolean live = dragging == module || resizing == module;
            boolean pick = selected == module;

            module.draw(graphics, x, y);

            // At rest an element is outlined clearly rather than hinted at. This
        // is an editor: the outline is how you know what there is to grab, and
        // the first version's 0.14 left it almost invisible against the world.
        float target = live ? 1f : (pick ? 0.9f : (hovered ? 0.65f : 0.4f));
            float lit = Ease.approach(glow.getOrDefault(module, 0f), target, 0.3f, delta);
            glow.put(module, lit);

            int base = module.isLocked() ? Theme.OFF : Theme.accent();
            int colour = Ease.color(Theme.BORDER, live ? Theme.CYAN : base, lit);
            colour = (Math.round(255 * Math.min(1f, 0.45f + lit * 0.55f)) << 24) | (colour & 0xFFFFFF);

            outline(graphics, x - 2, y - 2, w + 4, h + 4, colour);

            // The grip is on the selected element only, and it is drawn whether
            // or not the pointer is near it. A control that appears once you
            // are already on top of it cannot be aimed at.
            if (pick && !module.isLocked()) {
                int gripX = x + w + 2 - GRIP;
                int gripY = y + h + 2 - GRIP;
                Glass.pill(graphics, gripX, gripY, GRIP, GRIP, colour, 3);
            }

            if (hovered || pick || live) {
                String label = module.getName();
                if (module.isLocked()) label = label + "  locked";
                else if (live) label = label + "  " + Math.round(module.getScale() * 100) + "%";
                graphics.text(this.font, label, x - 2, y - 14,
                        live ? Theme.CYAN : Theme.TEXT, false);
            }
        }
    }

    private void outline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int colour) {
        graphics.fill(x, y, x + w, y + 1, colour);
        graphics.fill(x, y + h - 1, x + w, y + h, colour);
        graphics.fill(x, y, x + 1, y + h, colour);
        graphics.fill(x + w - 1, y, x + w, y + h, colour);
    }

    private void drawGuides(GuiGraphicsExtractor graphics) {
        int colour = (0xAA << 24) | (Theme.accent() & 0xFFFFFF);
        for (int line : guidesX) graphics.fill(line, 0, line + 1, this.height, colour);
        for (int line : guidesY) graphics.fill(0, line, this.width, line + 1, colour);
    }

    private void header(GuiGraphicsExtractor graphics) {
        Font font = this.font;
        graphics.text(font, "HUD EDITOR", 16, 44, Theme.TEXT_DIM, false);
        graphics.text(font, hint(), 16, this.height - 20, Theme.TEXT_DIM, false);
        String back = "Esc to go back";
        graphics.text(font, back, this.width - 16 - font.width(back),
                this.height - 20, Theme.OFF, false);
    }

    private String hint() {
        if (resizing != null) return "Pull to resize - let go to keep it";
        if (dragging != null) return "Pull to move - let go to place it";
        if (selected != null) {
            return "Drag to move  ·  corner to resize  ·  scroll to size  ·  arrows to nudge";
        }
        return "Click an element to pick it, or choose one on the left";
    }

    // ---------------- resizing by wheel ----------------

    private boolean scrollResize(double mouseX, double mouseY, double amount) {
        // Over the list the wheel moves the list; anywhere else it sizes what
        // it is pointing at. Both are the obvious meaning of a wheel in the
        // place it is being turned.
        if (panelOpen && overPanel((int) mouseX, (int) mouseY)) {
            int max = maxPanelScroll();
            if (max <= 0) return false;
            int before = panelScroll;
            panelScroll = Math.max(0, Math.min(max,
                    panelScroll - (int) Math.signum(amount)));
            if (panelScroll != before) this.rebuildWidgets();
            return true;
        }

        HudModule under = elementAt((int) mouseX, (int) mouseY);
        if (under == null) under = selected;
        if (under == null || under.isLocked()) return false;

        under.setScale(under.getScale() + (float) amount * 0.1f);
        SpaceClient.getConfigManager().save();
        // The bar carries the size, and the slider cannot be told a new
        // position, so it is rebuilt rather than left showing a stale one.
        if (under == selected) this.rebuildWidgets();
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return scrollResize(mouseX, mouseY, scrollY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return scrollResize(mouseX, mouseY, amount);
    }

    // ---------------- the element list ----------------

    /**
     * One element in the panel: its name, whether it is on, and whether it is
     * locked.
     *
     * The switch and the lock are separate targets inside the row, told apart
     * by where the pointer was on the frame before the click - the same trick
     * the module cards use, and for the same reason: this version changed the
     * mouse event signatures, so the whole package avoids overriding them.
     */
    private class ElementRow extends Button {

        private final HudModule module;
        private float hover = 0f;
        private float pick = 0f;

        private int lastMouseX = 0;

        ElementRow(HudModule module, int x, int y, int width, int height) {
            // The press arrives through the handler the constructor takes, not
            // through an override: Button on this version declares no onPress
            // of its own for a subclass to replace, and a build has already
            // said so in as many words. The handler is given the button it
            // fired on, which is enough to get back here.
            super(x, y, width, height, Component.empty(),
                    btn -> { if (btn instanceof ElementRow row) row.pressed(); },
                    DEFAULT_NARRATION);
            this.module = module;
            this.pick = selected == module ? 1f : 0f;
        }

        private boolean overSwitch() { return lastMouseX >= getX() + width - 26; }

        private boolean overLock() {
            return lastMouseX >= getX() + width - 44 && lastMouseX < getX() + width - 26;
        }

        private void pressed() {
            if (justDragged()) return;
            if (overSwitch()) {
                module.toggle();
                SpaceClient.getConfigManager().save();
                return;
            }
            if (overLock()) {
                module.setLocked(!module.isLocked());
                SpaceClient.getConfigManager().save();
                return;
            }
            select(module);
        }

        @Override
        public void setFocused(boolean focused) { super.setFocused(false); }

        @Override
        public boolean isFocused() { return false; }

        public net.minecraft.client.gui.ComponentPath nextFocusPath(
                net.minecraft.client.gui.navigation.FocusNavigationEvent event) {
            return null;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            lastMouseX = mouseX;

            hover = Ease.approach(hover, isHovered() ? 1f : 0f, 0.3f, delta);
            pick = Ease.approach(pick, selected == module ? 1f : 0f, 0.3f, delta);

            int x1 = getX();
            int y1 = getY();
            Font font = Minecraft.getInstance().font;

            if (hover > 0.02f || pick > 0.02f) {
                Glass.flat(graphics, x1, y1, width, height,
                        Ease.color(0x00FFFFFF, 0x1EFFFFFF, Math.max(hover, pick)),
                        Math.min(8, height / 2));
            }
            if (pick > 0.02f) {
                int bar = Math.round((height - 8) * pick);
                int middle = y1 + height / 2;
                graphics.fill(x1 + 3, middle - bar / 2, x1 + 5, middle + bar / 2, Theme.accent());
            }

            int textY = y1 + (height - 8) / 2;
            String name = ToggleRow.fit(font, module.getName(), width - 60);
            graphics.text(font, name, x1 + 12, textY,
                    module.isEnabled()
                            ? Ease.color(Theme.TEXT_DIM, Theme.TEXT, Math.max(hover, pick))
                            : Theme.OFF,
                    false);

            // The padlock. Shut, the shackle sits over the body; open, it is
            // lifted and pushed to one side. Shape carries the state rather
            // than colour alone - eight identical grey padlocks in a column
            // read as eight locked elements no matter what shade they are.
            int lockX = x1 + width - 40;
            boolean shut = module.isLocked();
            int lockColour = shut ? Theme.accent()
                    : (overLock() && hover > 0.3f ? Theme.TEXT_DIM : Theme.OFF);
            graphics.fill(lockX, textY + 3, lockX + 7, textY + 8, lockColour);
            if (shut) {
                graphics.fill(lockX + 1, textY, lockX + 2, textY + 3, lockColour);
                graphics.fill(lockX + 5, textY, lockX + 6, textY + 3, lockColour);
                graphics.fill(lockX + 2, textY - 1, lockX + 5, textY, lockColour);
            } else {
                graphics.fill(lockX + 5, textY - 1, lockX + 6, textY + 3, lockColour);
                graphics.fill(lockX + 8, textY - 2, lockX + 9, textY + 1, lockColour);
                graphics.fill(lockX + 6, textY - 3, lockX + 8, textY - 2, lockColour);
            }

            // The switch, same shape as everywhere else in the client
            int pillW = 20;
            int pillH = 10;
            int pillX = x1 + width - pillW - 6;
            int pillY = y1 + (height - pillH) / 2;
            float state = module.isEnabled() ? 1f : 0f;
            Glass.flat(graphics, pillX, pillY, pillW, pillH,
                    module.isEnabled() ? Theme.accent() : Theme.CHIP, pillH / 2);
            int knob = pillX + 1 + Math.round((pillW - pillH) * state);
            Glass.flat(graphics, knob, pillY + 1, pillH - 2, pillH - 2,
                    module.isEnabled() ? Theme.TEXT_ON_ACCENT : Theme.TEXT_DIM, (pillH - 2) / 2);
        }
    }

    @Override
    public void onClose() {
        dragging = null;
        resizing = null;
        SpaceClient.getConfigManager().save();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
