package gg.spaceclient.ui;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.module.Module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The main menu, as a window rather than a takeover.
 *
 * The previous version painted the whole screen, which made a quick toggle feel
 * like leaving the game. This one is a panel in the middle with the world still
 * visible around it, so opening the menu reads as pausing rather than exiting.
 *
 * The other change is how modules are found. Categories used to be the only way
 * through the list, which made them behave like pages: to reach a module you
 * first had to know which shelf it was on. Now the list is one continuous
 * scroll with a search field above it, and the categories are a filter you may
 * use rather than a route you must take.
 *
 * Everything interactive is a widget, and the scroll callbacks carry no
 * @Override, both for the reason the rest of this package does: this version
 * reworked the input API, and a signature that no longer matches should become
 * a dead method rather than a failed build.
 */
public class SpaceMenuScreen extends Screen {

    private static final int RAIL_W = 104;
    private static final int ROW_H = 26;
    private static final int ROW_GAP = 2;
    private static final int HEADER_H = 42;
    private static final int CHIP_H = 18;
    private static final int FOOTER_H = 30;
    private static final int PAD = 12;

    /** Which rail entry is showing. Only Mods has a list; the rest open screens. */
    private String section = "Mods";

    /** Null means every category. */
    private String category = null;

    /** Narrows the list to modules that are switched on. */
    private boolean onlyEnabled = false;

    private String query = "";
    private EditBox search;

    private int scroll = 0;
    private int maxScroll = 0;
    private float scrollShown = 0f;

    /**
     * Set at construction rather than in init, so the fade is not restarted
     * every time the widgets are rebuilt.
     */
    private final long openedAt = System.currentTimeMillis();

    private final List<ModRow> rows = new ArrayList<>();
    private final List<Integer> rowBaseY = new ArrayList<>();

    public SpaceMenuScreen() {
        super(Component.literal("Space Client"));
    }

    // ---------------- geometry ----------------
    //
    // Clamped rather than proportional: below the minimum the list stops being
    // readable, and above the maximum a centred panel starts to feel like the
    // full screen layout this replaced.

    private int panelW() { return Math.max(400, Math.min(620, this.width - 80)); }
    private int panelH() { return Math.max(240, Math.min(400, this.height - 60)); }
    private int panelX() { return (this.width - panelW()) / 2; }
    private int panelY() { return (this.height - panelH()) / 2; }

    private int contentLeft()  { return panelX() + RAIL_W; }
    private int contentRight() { return panelX() + panelW(); }
    private int listTop()      { return panelY() + HEADER_H + CHIP_H + 10; }
    private int listBottom()   { return panelY() + panelH() - FOOTER_H; }

    // ---------------- data ----------------

    /** Settings that are not inside a group, so they are not listed twice. */
    private static List<gg.spaceclient.setting.Setting> ungrouped(Module module) {
        java.util.Set<gg.spaceclient.setting.Setting> inGroups = new java.util.HashSet<>();
        module.getGroups().forEach(g -> inGroups.addAll(g.settings()));
        return module.getSettings().stream().filter(s -> !inGroups.contains(s)).toList();
    }

    private boolean matches(Module module) {
        if (category != null && !category.equals(ModuleCategories.of(module))) return false;
        if (onlyEnabled && !module.isEnabled()) return false;
        if (query.isEmpty()) return true;

        String needle = query.toLowerCase(Locale.ROOT);
        // Description is searched too, so "fps" finds the module that mentions
        // frames without having the word in its name
        return module.getName().toLowerCase(Locale.ROOT).contains(needle)
                || module.getId().toLowerCase(Locale.ROOT).contains(needle)
                || module.getDescription().toLowerCase(Locale.ROOT).contains(needle);
    }

    private List<Module> shown() {
        List<Module> out = new ArrayList<>();
        for (Module module : SpaceClient.getModuleManager().getAll()) {
            if (matches(module)) out.add(module);
        }
        return out;
    }

    private int countIn(String cat) {
        int count = 0;
        for (Module module : SpaceClient.getModuleManager().getAll()) {
            if (cat == null || cat.equals(ModuleCategories.of(module))) count++;
        }
        return count;
    }

    // ---------------- build ----------------

    @Override
    protected void init() {
        buildSearch();
        buildRail();
        buildChips();
        buildList();
    }

    /**
     * The search field is a vanilla EditBox on purpose.
     *
     * Typing needs the keyboard API, and this version reworked input. A widget
     * handles its own events internally, so using the vanilla one keeps this
     * screen out of a signature it cannot verify - the same reasoning that
     * keeps the mouse handlers below unannotated, applied one step earlier.
     *
     * The jar bears this out: input in 26.2 runs on KeyEvent and
     * CharacterEvent, so a hand written charTyped(char, int) would have
     * compiled into a method nothing ever calls, and the field would have sat
     * there refusing to type with no error to explain why.
     */
    private void buildSearch() {
        int w = 150;
        int x = contentRight() - PAD - w;
        int y = panelY() + 14;

        search = new EditBox(this.font, x, y, w, 16, Component.literal("Search"));
        search.setBordered(false);
        search.setMaxLength(48);
        search.setTextColor(Theme.TEXT);
        search.setHint(Component.literal("Search modules"));
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            scroll = 0;
            rebuildList();
        });
        this.addRenderableWidget(search);
        this.setInitialFocus(search);
    }

    private void buildRail() {
        int y = panelY() + HEADER_H + 4;
        String[][] entries = {
                {"Mods", ""},
                {"Move HUD", "hud"},
                {"Accounts", "accounts"},
                {"Cosmetica", "cosmetica"},
                {"Appearance", "appearance"},
                {"Diagnostics", "diagnostics"},
        };

        for (String[] entry : entries) {
            String name = entry[0];
            String opens = entry[1];
            this.addRenderableWidget(new NavButton(
                    panelX() + 6, y, RAIL_W - 12, 22, NavButton.Style.SIDEBAR,
                    () -> name,
                    () -> section.equals(name),
                    () -> open(name, opens)
            ));
            y += 24;
        }
    }

    private void open(String name, String opens) {
        Minecraft mc = Minecraft.getInstance();
        switch (opens) {
            case "hud" -> mc.gui.setScreen(new HudEditorScreen(this));
            case "accounts" -> mc.gui.setScreen(new AccountsScreen(this));
            case "cosmetica" -> mc.gui.setScreen(new CosmeticsScreen(this));
            case "appearance" -> mc.gui.setScreen(new AppearanceScreen(this));
            case "diagnostics" -> mc.gui.setScreen(new DiagnosticsScreen(this));
            default -> {
                section = name;
                this.rebuildWidgets();
            }
        }
    }

    private void buildChips() {
        int x = contentLeft() + PAD;
        int y = panelY() + HEADER_H;

        x += chip(x, y, "All", null) + 4;
        for (String cat : ModuleCategories.ALL) {
            if (countIn(cat) == 0) continue;   // an empty shelf is just noise
            x += chip(x, y, cat, cat) + 4;
        }

        // Sits apart from the categories because it stacks with them rather
        // than replacing them: HUD plus enabled is a reasonable thing to ask
        String label = "On";
        int w = this.font.width(label) + 16;
        if (x + w <= contentRight() - PAD) {
            this.addRenderableWidget(new NavButton(
                    x, y, w, CHIP_H, NavButton.Style.CHIP,
                    () -> label,
                    () -> onlyEnabled,
                    () -> {
                        onlyEnabled = !onlyEnabled;
                        scroll = 0;
                        this.rebuildWidgets();
                    }
            ));
        }
    }

    /** Returns the width used, so chips can sit side by side without a table. */
    private int chip(int x, int y, String label, String value) {
        int w = this.font.width(label) + 16;
        this.addRenderableWidget(new NavButton(
                x, y, w, CHIP_H, NavButton.Style.CHIP,
                () -> label,
                () -> java.util.Objects.equals(category, value),
                () -> {
                    category = value;
                    scroll = 0;
                    this.rebuildWidgets();
                }
        ));
        return w;
    }

    private void buildList() {
        rows.clear();
        rowBaseY.clear();

        List<Module> modules = shown();
        int left = contentLeft() + PAD;
        int width = contentRight() - PAD - left;
        int step = ROW_H + ROW_GAP;

        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            int baseY = listTop() + i * step;

            ModRow[] holder = new ModRow[1];
            holder[0] = new ModRow(
                    left, baseY - scroll, width, ROW_H,
                    module::getName,
                    () -> ModuleCategories.of(module),
                    module::isEnabled,
                    module.hasSettings(),
                    () -> {
                        if (holder[0].overGear()) {
                            Minecraft.getInstance().gui.setScreen(new SettingsScreen(
                                    this, module.getName(), module.getDescription(),
                                    ungrouped(module), module.getGroups()));
                            return;
                        }
                        module.toggle();
                        SpaceClient.getConfigManager().save();
                    }
            );
            this.addRenderableWidget(holder[0]);
            rows.add(holder[0]);
            rowBaseY.add(baseY);
        }

        int needed = modules.size() * step;
        int room = listBottom() - listTop();
        maxScroll = Math.max(0, needed - room);
        scroll = Math.min(scroll, maxScroll);
        reposition();
    }

    /**
     * Rebuilds only the list, leaving the search field alone.
     *
     * rebuildWidgets would recreate the EditBox mid keystroke, which drops
     * focus and the caret with it - the field would accept one character and
     * then stop.
     */
    private void rebuildList() {
        for (ModRow row : rows) this.removeWidget(row);
        buildList();
    }

    // ---------------- scrolling ----------------

    private boolean scrollBy(double amount) {
        if (maxScroll <= 0) return false;
        int before = scroll;
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (amount * 22)));
        if (scroll != before) reposition();
        return true;
    }

    /** Slides the existing rows rather than building new ones. */
    private void reposition() {
        for (int i = 0; i < rows.size() && i < rowBaseY.size(); i++) {
            rows.get(i).setY(rowBaseY.get(i) - scroll);
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return scrollBy(scrollY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return scrollBy(amount);
    }

    // ---------------- paint ----------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float age = Math.min(1f, (System.currentTimeMillis() - openedAt) / 180f);
        scrollShown += (scroll - scrollShown) * 0.35f;

        int x0 = panelX();
        int y0 = panelY();
        int x1 = x0 + panelW();
        int y1 = y0 + panelH();

        // The world stays visible behind a veil rather than being painted over.
        // The starfield backdrop is still available for anyone who wants it and
        // is drawn behind the panel, not instead of the game.
        if (Theme.spaceBackdrop()) {
            Backdrop.draw(graphics, this.width, this.height);
        } else {
            graphics.fill(0, 0, this.width, this.height, 0x99000000);
        }

        // A soft edge around the panel, drawn as two rings rather than a blur,
        // which this renderer has no cheap way to do
        graphics.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, 0x30000000);
        graphics.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0x50000000);

        graphics.fill(x0, y0, x1, y1, Theme.CONTENT);
        graphics.fill(x0, y0, x0 + RAIL_W, y1, Theme.SIDEBAR);
        graphics.fill(x0 + RAIL_W, y0, x0 + RAIL_W + 1, y1, Theme.BORDER);

        // Panel outline
        graphics.fill(x0, y0, x1, y0 + 1, Theme.BORDER);
        graphics.fill(x0, y1 - 1, x1, y1, Theme.BORDER);
        graphics.fill(x0, y0, x0 + 1, y1, Theme.BORDER);
        graphics.fill(x1 - 1, y0, x1, y1, Theme.BORDER);

        // Header
        JupiterIcon.draw(graphics, x0 + 12, y0 + 12, 18);
        graphics.text(this.font, "SPACE", x0 + 36, y0 + 12, Theme.accent(), false);
        graphics.text(this.font, "CLIENT", x0 + 36, y0 + 24, Theme.TEXT, false);
        graphics.fill(x0, y0 + HEADER_H - 4, x1, y0 + HEADER_H - 3, Theme.BORDER);

        // Search field: its own frame, since the vanilla border was turned off
        int searchX = contentRight() - PAD - 150;
        int searchY = y0 + 12;
        graphics.fill(searchX - 6, searchY, contentRight() - PAD, searchY + 20, Theme.CARD);
        graphics.fill(searchX - 6, searchY, contentRight() - PAD, searchY + 1, Theme.BORDER);
        graphics.fill(searchX - 6, searchY + 19, contentRight() - PAD, searchY + 20, Theme.BORDER);

        super.extractRenderState(graphics, mouseX, mouseY, delta);

        // Rows are laid out past the list edges, so these strips hide the
        // overflow. A row overhangs by at most its own height, which is why the
        // list is rows and not cards: the overhang lands inside the footer
        // instead of past the window and onto the world.
        graphics.fill(x0 + RAIL_W + 1, y0 + HEADER_H - 3, x1 - 1, listTop(), Theme.CONTENT);
        graphics.fill(x0 + RAIL_W + 1, listBottom(), x1 - 1, y1 - 1, Theme.CONTENT);

        int total = shown().size();
        if (total == 0) {
            String empty = query.isEmpty() ? "Nothing here" : "No module matches";
            graphics.text(this.font, empty,
                    contentLeft() + PAD, listTop() + 10, Theme.TEXT_DIM, false);
        }

        if (maxScroll > 0) {
            int trackTop = listTop();
            int trackHeight = listBottom() - trackTop;
            int thumb = Math.max(20, trackHeight * trackHeight / (trackHeight + maxScroll));
            int travel = trackHeight - thumb;
            int offset = Math.round(travel * (scrollShown / maxScroll));
            int x = x1 - 5;
            graphics.fill(x, trackTop, x + 2, trackTop + trackHeight, Theme.CARD);
            graphics.fill(x, trackTop + offset, x + 2, trackTop + offset + thumb, Theme.accent());
        }

        // Footer: count on the left, who is playing on the right
        graphics.fill(x0 + RAIL_W + 1, y1 - FOOTER_H, x1 - 1, y1 - FOOTER_H + 1, Theme.BORDER);
        graphics.text(this.font, total + (total == 1 ? " module" : " modules"),
                contentLeft() + PAD, y1 - 19, Theme.TEXT_DIM, false);

        String name = Minecraft.getInstance().getUser() != null
                ? Minecraft.getInstance().getUser().getName() : "Player";
        String right = name + "  v" + SpaceClient.VERSION;
        graphics.text(this.font, right,
                contentRight() - PAD - this.font.width(right), y1 - 19, Theme.OFF, false);

        // Description of whichever row the pointer is over, along the rail foot
        List<Module> modules = shown();
        int step = ROW_H + ROW_GAP;
        for (int i = 0; i < modules.size(); i++) {
            int rowY = listTop() + i * step - scroll;
            if (mouseY >= rowY && mouseY < rowY + ROW_H
                    && mouseY >= listTop() && mouseY < listBottom()
                    && mouseX >= contentLeft() && mouseX <= contentRight()) {
                String description = modules.get(i).getDescription();
                int room = contentRight() - PAD - (contentLeft() + PAD)
                        - this.font.width(right) - 20;
                if (this.font.width(description) <= room) {
                    graphics.text(this.font, description,
                            contentLeft() + PAD, y1 - 19, Theme.TEXT_DIM, false);
                }
                break;
            }
        }

        if (age < 1f) {
            int veil = (int) ((1f - age) * 255) << 24;
            graphics.fill(0, 0, this.width, this.height, veil);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
