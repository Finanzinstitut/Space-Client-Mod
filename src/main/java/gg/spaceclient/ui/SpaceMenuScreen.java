package gg.spaceclient.ui;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.config.Profiles;
import gg.spaceclient.module.Module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The client menu, rebuilt from nothing.
 *
 * <h2>What was wrong with the old one</h2>
 *
 * Modules were a single column of rows, and the things that were not modules -
 * the HUD editor, accounts, cosmetics, the look of the client - were scattered
 * between a left rail, a chip strip and two corners of a footer. So the menu
 * had three separate ideas of where a control might live, and knowing which
 * one to look in was something you had to learn rather than see. A column also
 * wastes the width it is given: eleven lines at a time out of twenty-six,
 * whatever the size of the window.
 *
 * <h2>What this is instead</h2>
 *
 * One panel, three regions, and nothing outside them. The rail on the left is
 * the only navigation there is - the category filters and every other screen
 * the client has, in one list, in the place you look first. The middle is a
 * grid of cards rather than a column of rows, so the panel shows most of the
 * client at once. The footer holds the three things that are settings of the
 * menu itself rather than places to go.
 *
 * <h2>Movement</h2>
 *
 * Nothing here appears; everything arrives. The panel fades up, the cards
 * stagger in from below, the rail's selected marker is one bar that slides
 * between entries instead of a light going out as another comes on, and the
 * grid restarts its entrance whenever the filter changes - which is also what
 * tells you the filter did something. All of it runs off frame time, so it
 * takes the same fifth of a second at thirty frames a second as at two
 * hundred and forty.
 *
 * <h2>Two conventions carried over</h2>
 *
 * The scroll callbacks have no @Override, and the search field is a vanilla
 * EditBox. Both are because this version reworked the input API: a hand
 * written handler whose signature no longer matches should become a dead
 * method rather than a failed build, and typing needs the keyboard API, which
 * the vanilla widget already handles internally.
 */
public class SpaceMenuScreen extends Screen {

    private static final int RAIL_W = 128;
    private static final int HEADER_H = 46;
    private static final int FOOTER_H = 34;
    private static final int PAD = 14;
    private static final int GAP = 8;
    private static final int SEARCH_W = 160;

    /** Null means every category. */
    private String category = null;

    /** Narrows the grid to modules that are switched on. */
    private boolean onlyEnabled = false;

    private String query = "";
    private EditBox search;

    /**
     * Scrolling, in pixels, with the drawn position chasing the wanted one.
     *
     * Pixel scrolling is only safe here because a card clips itself to the
     * grid's band - see ModuleCard. The old menu could not do this and stepped
     * by whole rows instead, which is why it never felt like a scroll.
     */
    private float scrollWanted = 0f;
    private float scrollShown = 0f;

    /** Where the rail's sliding marker is, and where it is heading. */
    private float markShown = -1f;

    /** Pull the grid by holding the left button, as well as by the wheel. */
    private final DragScroll drag = new DragScroll();

    private final long openedAt = System.currentTimeMillis();

    /** Restarted on every filter change, which is what re-runs the stagger. */
    private long gridSince = System.currentTimeMillis();

    private final List<ModuleCard> cards = new ArrayList<>();

    /**
     * The modules the cards were built from, and the shelves the rail was built
     * from, kept rather than worked out again.
     *
     * Both used to be recomputed inside the paint pass - once for the hovered
     * card's description, once for the rail's marker - which walked every
     * module several times a frame to answer a question that had already been
     * answered when the widgets were made. A client that ships an FPS module
     * should not be the reason the number goes down.
     */
    private List<Module> visible = new ArrayList<>();
    private List<String> shelves = new ArrayList<>();

    /** Laid out once per rebuild so the paint pass does not redo the maths. */
    private int columns = 1;
    private int cardW = ModuleCard.WIDTH;
    private int rows = 0;

    public SpaceMenuScreen() {
        super(Component.literal("Space Client"));
    }

    // ---------------- geometry ----------------
    //
    // Clamped rather than proportional at both ends: below the minimum the grid
    // drops to one column and stops being a grid, and above the maximum a
    // centred panel starts to read as the full-screen takeover this replaced.

    private int panelW() { return Math.max(440, Math.min(880, this.width - 60)); }
    private int panelH() { return Math.max(260, Math.min(480, this.height - 50)); }
    private int panelX() { return (this.width - panelW()) / 2; }
    private int panelY() { return (this.height - panelH()) / 2; }

    private int gridLeft()   { return panelX() + RAIL_W + PAD; }
    private int gridRight()  { return panelX() + panelW() - PAD; }
    private int gridTop()    { return panelY() + HEADER_H + 6; }
    private int gridBottom() { return panelY() + panelH() - FOOTER_H - 22; }

    // ---------------- data ----------------

    /** Settings that are not inside a group, so nothing is listed twice. */
    private static List<gg.spaceclient.setting.Setting> ungrouped(Module module) {
        java.util.Set<gg.spaceclient.setting.Setting> inGroups = new java.util.HashSet<>();
        module.getGroups().forEach(group -> inGroups.addAll(group.settings()));
        return module.getSettings().stream().filter(setting -> !inGroups.contains(setting)).toList();
    }

    private boolean matches(Module module) {
        if (category != null && !category.equals(ModuleCategories.of(module))) return false;
        if (onlyEnabled && !module.isEnabled()) return false;
        if (query.isEmpty()) return true;

        // The description is searched too, so "frames" finds the FPS module
        // without the word being in its name.
        String needle = query.toLowerCase(Locale.ROOT);
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

    private int countIn(String shelf) {
        int count = 0;
        for (Module module : SpaceClient.getModuleManager().getAll()) {
            if (shelf == null || shelf.equals(ModuleCategories.of(module))) count++;
        }
        return count;
    }

    /** Every category that has anything on it, "All" first. */
    private List<String> buildShelves() {
        List<String> out = new ArrayList<>();
        out.add(null);
        for (String shelf : ModuleCategories.ALL) {
            if (countIn(shelf) > 0) out.add(shelf);
        }
        return out;
    }

    private static String shelfName(String shelf) {
        return shelf == null ? "All" : shelf;
    }

    // ---------------- build ----------------

    @Override
    protected void init() {
        buildSearch();
        buildRail();
        buildFooter();
        buildGrid();
    }

    /**
     * The search field, as a vanilla EditBox with its border switched off.
     *
     * The frame is drawn by this screen instead, so the field matches the rest
     * of the panel; the widget itself is vanilla because typing goes through
     * the keyboard API, and this version runs input on KeyEvent and
     * CharacterEvent. A hand-written charTyped(char, int) would have compiled
     * into a method nothing calls, and the field would have sat there refusing
     * to type with no error to explain it.
     */
    private void buildSearch() {
        // Starts past the magnifier rather than at the pill's edge, so the
        // caret never sits on top of the glyph.
        int x = panelX() + panelW() - PAD - SEARCH_W + 26;
        int y = panelY() + 16;

        search = new EditBox(this.font, x, y, SEARCH_W - 36, 14, Component.literal("Search"));
        search.setBordered(false);
        search.setMaxLength(48);
        search.setTextColor(Theme.TEXT);
        search.setHint(Component.literal("Search"));
        // Value before responder, never the other way round: setValue fires the
        // responder, and the responder rebuilds the grid - so a rebuild would
        // run here and again when init reaches buildGrid, stacking two widgets
        // on every card.
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            scrollWanted = 0f;
            restartGrid();
            rebuildGrid();
        });
        this.addRenderableWidget(search);
        this.setInitialFocus(search);
    }

    /**
     * The rail: filters first, then every screen the client has.
     *
     * The two halves are one list on purpose. Splitting them - which is what
     * the old menu did, with the filters as chips over the grid and the screens
     * in a sidebar - meant there were two places a control could be and no way
     * to tell from the outside which held what.
     */
    private void buildRail() {
        int x = panelX() + 8;
        int w = RAIL_W - 16;
        int y = panelY() + HEADER_H + 6;

        shelves = buildShelves();
        for (String shelf : shelves) {
            this.addRenderableWidget(new RailEntry(
                    x, y, w, 20,
                    () -> shelfName(shelf),
                    () -> java.util.Objects.equals(category, shelf),
                    () -> countIn(shelf),
                    () -> {
                        category = shelf;
                        onlyEnabled = false;
                        scrollWanted = 0f;
                        restartGrid();
                        this.rebuildWidgets();
                    }));
            y += 22;
        }

        // Sits apart from the shelves because it stacks with them rather than
        // replacing them: "HUD, and only the ones that are on" is a reasonable
        // thing to ask for.
        this.addRenderableWidget(new RailEntry(
                x, y, w, 20,
                () -> "Active",
                () -> onlyEnabled,
                () -> {
                    int count = 0;
                    for (Module module : SpaceClient.getModuleManager().getAll()) {
                        if (module.isEnabled()) count++;
                    }
                    return count;
                },
                () -> {
                    onlyEnabled = !onlyEnabled;
                    scrollWanted = 0f;
                    restartGrid();
                    this.rebuildWidgets();
                }));
        y += 30;

        // Below the Active row, not through it. The gap after that row is 30
        // and the row is 20 tall, so the halfway point of the gap is five
        // pixels back from where the destinations start.
        railDividerY = y - 5;

        String[][] places = {
                {"Move HUD", "hud"},
                {"Item Shower", "itemshower"},
                {"Accounts", "accounts"},
                {"Cosmetics", "cosmetica"},
                {"Appearance", "appearance"},
                {"Diagnostics", "diagnostics"},
                {"Credits", "credits"},
        };

        int floor = panelY() + panelH() - FOOTER_H - 4;
        for (String[] place : places) {
            if (y + 20 > floor) break;   // a line half under the footer is worse than none
            String opens = place[1];
            this.addRenderableWidget(new RailEntry(
                    x, y, w, 20,
                    () -> place[0],
                    () -> false,
                    null,
                    () -> open(opens)));
            y += 22;
        }
    }

    /** Where the line between filters and destinations is drawn. */
    private int railDividerY = 0;

    private void open(String opens) {
        Minecraft mc = Minecraft.getInstance();
        switch (opens) {
            case "hud" -> mc.gui.setScreen(new HudEditorScreen(this));
            case "itemshower" -> mc.gui.setScreen(new ItemShowerScreen(this));
            case "accounts" -> mc.gui.setScreen(new AccountsScreen(this));
            case "cosmetica" -> mc.gui.setScreen(new CosmeticsScreen(this));
            case "appearance" -> mc.gui.setScreen(new AppearanceScreen(this));
            case "diagnostics" -> mc.gui.setScreen(new DiagnosticsScreen(this));
            case "credits" -> mc.gui.setScreen(new CreditsScreen(this));
            default -> { }
        }
    }

    /**
     * The footer: the three controls that are about the menu rather than
     * places inside it.
     *
     * Layout is what you are arranging, pinning ties that arrangement to the
     * server you are on, and streamer mode changes what other people see. None
     * of them is a destination, so none of them belongs in the rail.
     */
    private void buildFooter() {
        int y = panelY() + panelH() - FOOTER_H + 7;
        int x = panelX() + RAIL_W + PAD;

        this.addRenderableWidget(new NavButton(
                x, y, 104, 20, NavButton.Style.CHIP,
                () -> "Layout: " + Profiles.active(),
                () -> false,
                () -> {
                    List<String> all = Profiles.list();
                    int at = all.indexOf(Profiles.active());
                    Profiles.switchTo(all.get((at + 1) % all.size()));
                    this.rebuildWidgets();
                }));

        // Next to the layout switcher rather than on a screen of its own,
        // because the decision is made the moment you have finished arranging
        // things for where you are - and a setting you have to go and find
        // afterwards is one nobody finds.
        this.addRenderableWidget(new NavButton(
                x + 110, y, 108, 20, NavButton.Style.CHIP,
                () -> {
                    String here = gg.spaceclient.util.CurrentServer.address();
                    String pinned = gg.spaceclient.config.HudServerProfiles.profileFor(here);
                    if (pinned == null) return "Pin here";
                    return pinned.equals(Profiles.active()) ? "Pinned here" : "Pinned: " + pinned;
                },
                () -> gg.spaceclient.config.HudServerProfiles.profileFor(
                        gg.spaceclient.util.CurrentServer.address()) != null,
                () -> {
                    String here = gg.spaceclient.util.CurrentServer.address();
                    String pinned = gg.spaceclient.config.HudServerProfiles.profileFor(here);
                    if (pinned != null && pinned.equals(Profiles.active())) {
                        gg.spaceclient.config.HudServerProfiles.clear(here);
                    } else {
                        gg.spaceclient.config.HudServerProfiles.assign(here, Profiles.active());
                    }
                    this.rebuildWidgets();
                }));

        this.addRenderableWidget(new NavButton(
                panelX() + panelW() - PAD - 96, y, 96, 20, NavButton.Style.CHIP,
                () -> StreamerMode.isOn() ? "Streamer: on" : "Streamer",
                StreamerMode::isOn,
                () -> Minecraft.getInstance().gui.setScreen(new StreamerScreen(this))));
    }

    /**
     * How many columns fit, and how wide a card is once they do.
     *
     * The card's preferred width is a floor, not a fixed size: whatever is left
     * over after the columns are counted is shared out between them, so the
     * grid meets the right edge of the panel instead of leaving a ragged gap
     * that changes with the window.
     */
    private void measure() {
        int room = gridRight() - gridLeft();
        columns = Math.max(1, (room + GAP) / (ModuleCard.WIDTH + GAP));
        cardW = (room - (columns - 1) * GAP) / columns;
    }

    private int step() { return ModuleCard.HEIGHT + GAP; }

    private int maxScroll() {
        int content = rows * step() - GAP;
        return Math.max(0, content - (gridBottom() - gridTop()));
    }

    private void buildGrid() {
        cards.clear();
        measure();

        visible = shown();
        List<Module> modules = visible;
        rows = (modules.size() + columns - 1) / columns;

        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            int column = i % columns;
            int row = i / columns;
            int x = gridLeft() + column * (cardW + GAP);
            int y = gridTop() + row * step();

            ModuleCard[] holder = new ModuleCard[1];
            holder[0] = new ModuleCard(
                    x, y, cardW, ModuleCard.HEIGHT,
                    module::getName, module.getDescription(),
                    module::isEnabled, module.hasSettings(),
                    () -> {
                        // A press that ends a pull is the pull's, not the
                        // card's - otherwise letting go after scrolling toggles
                        // whatever happened to be under the pointer.
                        if (drag.swallowsClick()) return;
                        if (holder[0].overGear()) {
                            Minecraft.getInstance().gui.setScreen(new SettingsScreen(
                                    this, module.getName(), module.getDescription(),
                                    ungrouped(module), module.getGroups()));
                            return;
                        }
                        module.toggle();
                        SpaceClient.getConfigManager().save();
                    });
            this.addRenderableWidget(holder[0]);
            cards.add(holder[0]);
        }
    }

    /**
     * Rebuilds only the grid, leaving the search field alone.
     *
     * rebuildWidgets would recreate the EditBox mid keystroke, which drops
     * focus and the caret with it - the field would take one character and
     * then stop.
     */
    private void rebuildGrid() {
        for (ModuleCard card : cards) this.removeWidget(card);
        buildGrid();
    }

    private void restartGrid() {
        gridSince = System.currentTimeMillis();
    }

    // ---------------- scrolling ----------------

    private boolean scrollBy(double amount) {
        int max = maxScroll();
        if (max <= 0) return false;
        scrollWanted = Math.max(0f, Math.min(max, scrollWanted - (float) amount * step() * 0.9f));
        return true;
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
        float age = Ease.clamp01((System.currentTimeMillis() - openedAt) / 220f);
        float shown = Ease.outCubic(age);

        // Pulling the grid, only while the pointer is actually over it - so a
        // drag that starts on the rail or the footer does not scroll anything.
        drag.update(mouseX, mouseY);
        if (drag.isDragging() && mouseX >= gridLeft() - PAD && mouseY >= gridTop()
                && mouseY < gridBottom()) {
            scrollWanted = Math.max(0f, Math.min(maxScroll(), scrollWanted - drag.deltaY()));
        }
        scrollWanted = Math.max(0f, Math.min(maxScroll(), scrollWanted));
        scrollShown = Ease.approach(scrollShown, scrollWanted, 0.45f, delta);

        int x0 = panelX();
        int y0 = panelY();
        int x1 = x0 + panelW();
        int y1 = y0 + panelH();

        // The chosen background, the same one every other screen draws, so a
        // background setting is not a setting that visibly does nothing
        // everywhere except the screen it was changed on.
        Backdrop.draw(graphics, this.width, this.height);
        graphics.fill(0, 0, this.width, this.height, (int) (0x55 * shown) << 24);

        // One rounded plate with its own soft edge, rather than a rectangle
        // with four one-pixel borders. This is the shape the HUD plates and
        // every button use, and the menu was the last thing still drawing a box.
        Glass.pill(graphics, x0, y0, panelW(), panelH(), Theme.PANEL, 14);

        // The rail is a shade darker than the panel instead of a separate
        // surface with a line down its edge: one plate reads as one window.
        Glass.pill(graphics, x0 + 4, y0 + HEADER_H - 4, RAIL_W - 4,
                panelH() - HEADER_H - FOOTER_H + 6, 0x30000000, 12);

        header(graphics, x0, y0, x1);

        // The rail's divider, between the filters and the places they are not.
        if (railDividerY > 0) {
            graphics.fill(x0 + 16, railDividerY, x0 + RAIL_W - 16, railDividerY + 1, 0x22FFFFFF);
        }

        railMarker(graphics, x0, delta);
        layoutCards(mouseX, mouseY);

        super.extractRenderState(graphics, mouseX, mouseY, delta);

        if (cards.isEmpty()) {
            String empty = query.isEmpty() ? "Nothing on this shelf" : "No module matches";
            graphics.text(this.font, empty, gridLeft(), gridTop() + 16, Theme.TEXT_DIM, false);
        }

        // Fades at the top and bottom of the grid.
        //
        // A card is clipped row by row, so it stops dead at the band's edge.
        // That is correct and it looks cut, so the last few rows are darkened
        // in steps and the cut becomes an edge the content passes under.
        fadeEdge(graphics, gridTop(), 1);
        fadeEdge(graphics, gridBottom(), -1);

        scrollbar(graphics, x1);
        footer(graphics, x0, x1, y1, mouseX, mouseY);

        if (age < 1f) {
            // Eased rather than linear: a veil that lifts at a constant rate
            // reads as a cut, because the eye notices the last few percent most
            // and that is exactly where linear spends the least time.
            graphics.fill(0, 0, this.width, this.height, (int) ((1f - shown) * 255) << 24);
        }
    }

    private void header(GuiGraphicsExtractor graphics, int x0, int y0, int x1) {
        JupiterIcon.draw(graphics, x0 + 14, y0 + 13, 20);
        graphics.text(this.font, "SPACE", x0 + 40, y0 + 13, Theme.TEXT, false);
        graphics.text(this.font, "CLIENT", x0 + 40, y0 + 25, Theme.TEXT_DIM, false);

        // The search field's own frame, since the vanilla border is off.
        int searchX = x1 - PAD - SEARCH_W;
        int searchY = y0 + 12;
        Glass.pill(graphics, searchX, searchY, SEARCH_W, 22, Theme.CHIP, 11);

        // A magnifier, drawn small: a ring and a stroke off its corner.
        int ringX = searchX + 12;
        int ringY = searchY + 11;
        int glass = query.isEmpty() ? Theme.OFF : Theme.TEXT_DIM;
        graphics.fill(ringX - 3, ringY - 4, ringX + 2, ringY - 3, glass);
        graphics.fill(ringX - 3, ringY + 2, ringX + 2, ringY + 3, glass);
        graphics.fill(ringX - 4, ringY - 3, ringX - 3, ringY + 2, glass);
        graphics.fill(ringX + 2, ringY - 3, ringX + 3, ringY + 2, glass);
        graphics.fill(ringX + 3, ringY + 3, ringX + 5, ringY + 5, glass);
    }

    /**
     * One bar that travels to whichever rail entry is selected.
     *
     * Deliberately drawn here rather than by the widgets. A widget knows only
     * about itself, so a rail built out of widgets can only fade one marker out
     * as another fades in - which reads as two lights, not one indicator. The
     * bar is placed from the same numbers buildRail used, so it cannot drift
     * away from the row it is pointing at.
     */
    private void railMarker(GuiGraphicsExtractor graphics, int x0, float delta) {
        int index = -1;
        if (onlyEnabled) {
            index = shelves.size();      // the Active line, just past the shelves
        } else {
            for (int i = 0; i < shelves.size(); i++) {
                if (java.util.Objects.equals(shelves.get(i), category)) { index = i; break; }
            }
        }
        if (index < 0) return;

        float target = panelY() + HEADER_H + 6 + index * 22;
        if (markShown < 0f) markShown = target;    // no slide on the first frame
        markShown = Ease.approach(markShown, target, 0.4f, delta);

        int top = Math.round(markShown) + 4;
        graphics.fill(x0 + 8, top, x0 + 11, top + 12, Theme.accent());
    }

    /**
     * Puts every card where the scroll says it goes, and hands it the band it
     * is allowed to paint in and how far its entrance has got.
     *
     * Done every frame rather than on a rebuild, so scrolling moves widgets
     * instead of recreating them - which is what keeps the motion smooth and
     * keeps a card's hover and toggle animations from restarting mid-scroll.
     */
    private void layoutCards(int mouseX, int mouseY) {
        long elapsed = System.currentTimeMillis() - gridSince;
        int scroll = Math.round(scrollShown);

        for (int i = 0; i < cards.size(); i++) {
            ModuleCard card = cards.get(i);
            int column = i % columns;
            int row = i / columns;

            card.setX(gridLeft() + column * (cardW + GAP));
            card.setY(gridTop() + row * step() - scroll);
            card.setClip(gridTop(), gridBottom());

            // Staggered along the diagonal rather than by index in the list.
            // Row-major would run the wave along each row and start the next
            // one back at the left, which reads as four separate animations; a
            // diagonal is one wave crossing the grid, and it finishes sooner
            // because the last card is at row+column rather than at row*columns.
            card.setAppear(ScreenChrome.appearAt(elapsed, row + column));
        }
    }

    /** Darkens the last few rows of the grid so the clip reads as an edge. */
    private void fadeEdge(GuiGraphicsExtractor graphics, int edge, int direction) {
        for (int step = 0; step < 6; step++) {
            int alpha = (6 - step) * 9;
            int y = edge + direction * step;
            int top = direction > 0 ? y : y - 1;
            graphics.fill(gridLeft() - 4, top, gridRight() + 6, top + 1, alpha << 24);
        }
    }

    private void scrollbar(GuiGraphicsExtractor graphics, int x1) {
        int max = maxScroll();
        if (max <= 0) return;

        int trackTop = gridTop();
        int trackHeight = gridBottom() - trackTop;
        int content = rows * step() - GAP;
        int thumb = Math.max(24, trackHeight * trackHeight / Math.max(1, content));
        int travel = trackHeight - thumb;
        int offset = Math.round(travel * (scrollShown / max));

        int x = x1 - 7;
        Glass.pill(graphics, x, trackTop, 3, trackHeight, 0x30FFFFFF, 1);
        Glass.pill(graphics, x, trackTop + offset, 3, thumb, Theme.accent(), 1);
    }

    private void footer(GuiGraphicsExtractor graphics, int x0, int x1, int y1,
                        int mouseX, int mouseY) {
        Font font = this.font;

        // The description of whichever card the pointer is over, on its own
        // line above the footer. In the footer it would sit over the buttons,
        // which made both unreadable and made the buttons look broken.
        String hint = hoveredDescription(mouseX, mouseY);
        if (hint != null) {
            graphics.text(font, ToggleRow.fit(font, hint, gridRight() - gridLeft()),
                    gridLeft(), y1 - FOOTER_H - 13, Theme.TEXT_DIM, false);
        } else {
            String name = Minecraft.getInstance().getUser() != null
                    ? Minecraft.getInstance().getUser().getName() : "Player";
            String line = name + "  ·  v" + SpaceClient.VERSION;
            graphics.text(font, line, gridLeft(), y1 - FOOTER_H - 13, Theme.OFF, false);
        }
    }

    private String hoveredDescription(int mouseX, int mouseY) {
        if (mouseY < gridTop() || mouseY >= gridBottom()) return null;
        List<Module> modules = visible;
        for (int i = 0; i < cards.size() && i < modules.size(); i++) {
            ModuleCard card = cards.get(i);
            if (mouseX >= card.getX() && mouseX < card.getX() + cardW
                    && mouseY >= card.getY() && mouseY < card.getY() + ModuleCard.HEIGHT) {
                return modules.get(i).getDescription();
            }
        }
        return null;
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
