package gg.spaceclient.ui;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.config.DropLock;
import gg.spaceclient.module.Module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Drop Lock's item grid: every item in the game, a search box over it, and a
 * click to lock or unlock one. Locked items are lit and carry a red corner;
 * "Locked" shows only those.
 *
 * The grid is a fixed set of cells laid over the list: scrolling or typing
 * changes which item each cell stands for, not the cells themselves, so the
 * search box keeps its focus while you type.
 */
public class DropLockScreen extends Screen {

    private static final int CELL = 22;
    private static final int PANEL_W = 420;
    private static final int TOP = 104;

    private final Screen parent;

    /** Every item, with its name in lower case for the search. */
    private record Entry(String id, ItemStack stack, String name) {}

    private final List<Entry> all = new ArrayList<>();
    private List<Entry> shown = new ArrayList<>();
    private String query = "";
    private boolean onlyLocked = false;
    private int scrollRow = 0;
    private EditBox search;
    private boolean iconsBroken = false;

    /** Where each cell is, left to right and top to bottom. */
    private final List<int[]> cells = new ArrayList<>();

    public DropLockScreen(Screen parent) {
        super(Component.literal("Drop Lock"));
        this.parent = parent;
        collect();
    }

    private void collect() {
        all.clear();
        for (Item item : BuiltInRegistries.ITEM) {
            try {
                if (item == Items.AIR) continue;
                ItemStack stack = new ItemStack(item);
                String id = BuiltInRegistries.ITEM.getKey(item).toString();
                String name = stack.getHoverName().getString();
                all.add(new Entry(id, stack, (name + " " + id).toLowerCase(Locale.ROOT)));
            } catch (Throwable ignored) {
                // An item that cannot be shown is left out, not the grid
            }
        }
        refilter();
    }

    private void refilter() {
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<>();
        for (Entry e : all) {
            if (onlyLocked && !DropLock.isLocked(e.id())) continue;
            if (!q.isEmpty() && !e.name().contains(q)) continue;
            out.add(e);
        }
        shown = out;
        scrollRow = Math.min(scrollRow, maxScroll());
    }

    private int panelLeft() { return (this.width - PANEL_W) / 2; }
    private int columns() { return PANEL_W / CELL; }
    private int gridBottom() { return this.height - 64; }
    private int rows() { return Math.max(1, (gridBottom() - TOP) / CELL); }
    private int maxScroll() {
        int totalRows = (shown.size() + columns() - 1) / columns();
        return Math.max(0, totalRows - rows());
    }

    private Entry entryAt(int cell) {
        int index = scrollRow * columns() + cell;
        return index >= 0 && index < shown.size() ? shown.get(index) : null;
    }

    private Module module() {
        return SpaceClient.getModuleManager().get("droplock");
    }

    @Override
    protected void init() {
        int left = panelLeft();
        cells.clear();

        String typed = search != null ? search.getValue() : query;
        search = new EditBox(this.font, left + 4, 74, 220, 14, Component.literal("Search"));
        search.setHint(Component.literal("Search items..."));
        search.setValue(typed);
        search.setResponder(text -> {
            query = text;
            scrollRow = 0;
            refilter();
        });
        this.addRenderableWidget(search);
        this.setInitialFocus(search);

        this.addRenderableWidget(new FlatButton(left + 232, 70, 90, 20,
                () -> onlyLocked ? "Locked only" : "All items",
                () -> onlyLocked,
                () -> {
                    onlyLocked = !onlyLocked;
                    scrollRow = 0;
                    refilter();
                }).asAction());

        this.addRenderableWidget(new FlatButton(left + PANEL_W - 90, 70, 90, 20,
                () -> "Clear all", () -> false,
                () -> {
                    DropLock.clear();
                    SpaceClient.getConfigManager().save();
                    refilter();
                }).asAction());

        int cols = columns();
        int offset = (PANEL_W - cols * CELL) / 2;
        for (int r = 0; r < rows(); r++) {
            for (int c = 0; c < cols; c++) {
                int x = left + offset + c * CELL;
                int y = TOP + r * CELL;
                int cell = cells.size();
                cells.add(new int[]{x, y});
                this.addRenderableWidget(new FlatButton(x + 1, y + 1, CELL - 2, CELL - 2,
                        () -> "", () -> false,
                        () -> {
                            Entry e = entryAt(cell);
                            if (e == null) return;
                            DropLock.toggle(e.id());
                            SpaceClient.getConfigManager().save();
                            if (onlyLocked) refilter();
                        }).asAction());
            }
        }

        int footer = this.height - 46;
        this.addRenderableWidget(new FlatButton(left, footer, 150, 22,
                () -> "Protection",
                () -> {
                    Module m = module();
                    return m != null && m.isEnabled();
                },
                () -> {
                    Module m = module();
                    if (m == null) return;
                    m.toggle();
                    SpaceClient.getConfigManager().save();
                }));

        this.addRenderableWidget(new FlatButton(left + PANEL_W - 120, footer, 120, 22,
                () -> "Back", () -> false, this::onClose).asAction());
    }

    private boolean scroll(double amount) {
        int before = scrollRow;
        scrollRow = Math.max(0, Math.min(maxScroll(), scrollRow - (int) Math.signum(amount)));
        return scrollRow != before || amount != 0;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return scroll(scrollY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return scroll(amount);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Backdrop.draw(graphics, this.width, this.height);

        int left = panelLeft();
        graphics.fill(left - 18, 24, left + PANEL_W + 18, this.height - 24, Theme.PANEL);

        JupiterIcon.draw(graphics, left, 38, 22);
        graphics.text(this.font, "DROP LOCK", left + 32, 40, Theme.CYAN, false);
        graphics.text(this.font, "Click the items you never want to drop by accident",
                left + 32, 52, Theme.TEXT_DIM, false);
        graphics.fill(left, 96, left + PANEL_W, 97, Theme.BORDER);

        // Cells with no item behind them are not drawn at all
        var children = this.children();
        int firstCell = -1;
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i) instanceof FlatButton b && b.getWidth() == CELL - 2 && b.getHeight() == CELL - 2) {
                if (firstCell < 0) firstCell = i;
                b.visible = entryAt(i - firstCell) != null;
            }
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);

        Entry hovered = null;
        for (int i = 0; i < cells.size(); i++) {
            Entry e = entryAt(i);
            if (e == null) continue;
            int x = cells.get(i)[0];
            int y = cells.get(i)[1];
            boolean locked = DropLock.isLocked(e.id());
            if (locked) {
                // Lit plate and a red corner: readable at a glance in a full grid
                graphics.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0x40FFFFFF);
                graphics.fill(x + CELL - 7, y + 1, x + CELL - 1, y + 3, Theme.LOCKED);
                graphics.fill(x + CELL - 3, y + 1, x + CELL - 1, y + 7, Theme.LOCKED);
            }
            drawIcon(graphics, e.stack(), x + 3, y + 3);
            if (mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL) hovered = e;
        }

        int infoY = this.height - 60;
        if (hovered != null) {
            String name = hovered.stack().getHoverName().getString();
            boolean locked = DropLock.isLocked(hovered.id());
            graphics.text(this.font, name + (locked ? "  -  locked" : ""), left, infoY,
                    locked ? Theme.TEXT : Theme.TEXT_DIM, false);
        } else {
            String keyName = keyName();
            graphics.text(this.font, DropLock.count() + " locked  -  "
                            + shown.size() + " shown  -  toggle key: " + keyName,
                    left, infoY, Theme.TEXT_DIM, false);
        }
        if (shown.isEmpty()) {
            graphics.text(this.font, onlyLocked ? "No items locked yet" : "Nothing matches",
                    left + 4, TOP + 6, Theme.TEXT_DIM, false);
        }
    }

    private static String keyName() {
        try {
            var key = SpaceClient.getDropLockKey();
            if (key == null || key.isUnbound()) return "none (set it in Drop Lock's settings)";
            return key.getTranslatedKeyMessage().getString();
        } catch (Throwable t) {
            return "?";
        }
    }

    private void drawIcon(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
        if (iconsBroken) {
            graphics.fill(x, y, x + 16, y + 16, 0x55FFFFFF);
            return;
        }
        try {
            var gui = (gg.spaceclient.mixin.GuiItemInvoker) (Object) graphics;
            gui.spaceclient$item(mc.player, mc.level, stack, x, y, 0);
        } catch (Throwable t) {
            iconsBroken = true;
            SpaceClient.LOGGER.warn("Item icons unavailable on this version", t);
        }
    }

    private static final Minecraft mc = Minecraft.getInstance();

    @Override
    public void onClose() {
        gg.spaceclient.compat.Screens.set(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
