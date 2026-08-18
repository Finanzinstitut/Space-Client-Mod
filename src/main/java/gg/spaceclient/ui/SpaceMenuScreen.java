package gg.spaceclient.ui;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.module.Module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The main menu: a sidebar, a row of category chips, and a grid of module cards.
 *
 * The old layout put every module in one flat two column list, which worked
 * while there were eight of them and stopped working once there were twenty.
 * Splitting navigation out to the left and filtering by category above the grid
 * means the number of modules can keep growing without the screen getting worse.
 *
 * Everything interactive is still a widget, because this version changed the
 * mouse event signatures and the codebase has been avoiding them deliberately.
 * Scrolling is the one exception and is handled defensively - see scrollBy.
 */
public class SpaceMenuScreen extends Screen {

    private static final int SIDEBAR_W = 148;
    private static final int PAD = 18;
    private static final int CARD_W = 132;
    private static final int CARD_H = 74;
    private static final int GAP = 10;
    private static final int CHIP_H = 22;

    /** Which sidebar entry is showing. Only MODS has a grid; the rest open screens. */
    private String section = "Mods";

    /** Null means every category. */
    private String category = null;

    private int scroll = 0;
    private int maxScroll = 0;

    /** Eased separately from scroll so the grid glides instead of jumping. */
    private float scrollShown = 0f;

    /**
     * When the screen opened, for the fade in.
     *
     * Set once at construction rather than in init. init runs again on every
     * rebuild, and rebuilding on each scroll step was restarting the fade -
     * which is what made the screen flash black while the wheel turned.
     */
    private final long openedAt = System.currentTimeMillis();

    /** The cards, kept so scrolling can move them instead of rebuilding them. */
    private final List<ModCard> cards = new ArrayList<>();
    private final List<Integer> cardBaseY = new ArrayList<>();

    public SpaceMenuScreen() {
        super(Component.literal("Space Client"));
    }

    private int contentLeft() { return SIDEBAR_W + PAD; }
    private int contentRight() { return this.width - PAD; }
    private int gridTop() { return 108; }
    private int gridBottom() { return this.height - PAD; }

    /** Settings that are not inside a group, so they are not listed twice. */
    private static List<gg.spaceclient.setting.Setting> ungrouped(Module module) {
        java.util.Set<gg.spaceclient.setting.Setting> inGroups = new java.util.HashSet<>();
        module.getGroups().forEach(g -> inGroups.addAll(g.settings()));
        return module.getSettings().stream().filter(s -> !inGroups.contains(s)).toList();
    }

    private List<Module> shown() {
        List<Module> out = new ArrayList<>();
        for (Module module : SpaceClient.getModuleManager().getAll()) {
            if (category == null || category.equals(ModuleCategories.of(module))) out.add(module);
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

    private int columns() {
        int usable = contentRight() - contentLeft();
        return Math.max(1, (usable + GAP) / (CARD_W + GAP));
    }

    @Override
    protected void init() {
        buildSidebar();
        buildChips();
        buildGrid();
    }

    private void buildSidebar() {
        int y = 96;
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
                    0, y, SIDEBAR_W, 26, NavButton.Style.SIDEBAR,
                    () -> name,
                    () -> section.equals(name),
                    () -> open(name, opens)
            ));
            y += 28;
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
        int x = contentLeft();
        int y = 74;

        x += chip(x, y, "All mods", null) + 6;
        for (String cat : ModuleCategories.ALL) {
            if (countIn(cat) == 0) continue;   // an empty shelf is just noise
            x += chip(x, y, cat, cat) + 6;
        }
    }

    /** Returns the width used, so chips can sit side by side without a table. */
    private int chip(int x, int y, String label, String value) {
        int w = this.font.width(label) + 22;
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

    private void buildGrid() {
        cards.clear();
        cardBaseY.clear();

        List<Module> modules = shown();
        int cols = columns();
        int left = contentLeft();
        int top = gridTop() - scroll;

        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            int x = left + (i % cols) * (CARD_W + GAP);
            int y = top + (i / cols) * (CARD_H + GAP);

            // The card remembers where the pointer was, so one press handler can
            // tell a click on the dots from a click on the card - the same
            // approach FlatButton uses, and for the same reason: this version
            // changed the mouse event API and overriding it is not safe.
            ModCard[] holder = new ModCard[1];
            holder[0] = new ModCard(
                    x, y, CARD_W, CARD_H,
                    module::getName,
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
            cards.add(holder[0]);
            cardBaseY.add(gridTop() + (i / cols) * (CARD_H + GAP));
        }

        int rows = (modules.size() + cols - 1) / cols;
        int needed = rows * (CARD_H + GAP);
        int room = gridBottom() - gridTop();
        maxScroll = Math.max(0, needed - room);
        scroll = Math.min(scroll, maxScroll);
    }

    /**
     * Moves the grid, in whatever units the caller has.
     *
     * The scroll callbacks below deliberately carry no @Override. This version
     * reworked the input API and the codebase has been avoiding those
     * signatures for that reason; without the annotation a signature that no
     * longer matches becomes an unused method rather than a build failure, so
     * the worst case is that the wheel does nothing and the buttons still work.
     */
    private boolean scrollBy(double amount) {
        if (maxScroll <= 0) return false;
        int before = scroll;
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (amount * 24)));
        if (scroll != before) reposition();
        return true;
    }

    /**
     * Slides the existing cards rather than building new ones.
     *
     * rebuildWidgets on every wheel notch threw away and recreated every
     * widget, which restarted their hover animations and the screen fade -
     * the flash of black. Moving them keeps all of that intact.
     */
    private void reposition() {
        for (int i = 0; i < cards.size() && i < cardBaseY.size(); i++) {
            cards.get(i).setY(cardBaseY.get(i) - scroll);
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return scrollBy(scrollY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return scrollBy(amount);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Backdrop.draw(graphics, this.width, this.height);

        // The whole panel fades up on open, so the menu arrives rather than
        // being slapped onto the world
        float age = Math.min(1f, (System.currentTimeMillis() - openedAt) / 180f);

        // Scroll is eased for display only; the widgets themselves sit on the
        // integer value, so a click never lands where the card is not.
        scrollShown += (scroll - scrollShown) * 0.35f;

        graphics.fill(0, 0, SIDEBAR_W, this.height, Theme.SIDEBAR);
        graphics.fill(SIDEBAR_W, 0, this.width, this.height, Theme.CONTENT);
        graphics.fill(SIDEBAR_W, 0, SIDEBAR_W + 1, this.height, Theme.BORDER);

        JupiterIcon.draw(graphics, 16, 24, 22);
        graphics.text(this.font, "SPACE", 46, 24, Theme.accent(), false);
        graphics.text(this.font, "CLIENT", 46, 36, Theme.TEXT, false);
        graphics.fill(16, 60, SIDEBAR_W - 16, 61, Theme.BORDER);
        graphics.text(this.font, "MENU", 16, 76, Theme.TEXT_DIM, false);

        // Footer: who is playing, mirroring where launchers put it
        String name = Minecraft.getInstance().getUser() != null
                ? Minecraft.getInstance().getUser().getName() : "Player";
        graphics.fill(16, this.height - 44, SIDEBAR_W - 16, this.height - 43, Theme.BORDER);
        graphics.text(this.font, name, 16, this.height - 34, Theme.TEXT, false);
        graphics.text(this.font, "v" + SpaceClient.VERSION, 16, this.height - 22,
                Theme.TEXT_DIM, false);

        graphics.text(this.font, section, contentLeft(), 30, Theme.TEXT, false);
        int total = shown().size();
        graphics.text(this.font, total + (total == 1 ? " module" : " modules"),
                contentLeft(), 44, Theme.TEXT_DIM, false);

        super.extractRenderState(graphics, mouseX, mouseY, delta);

        // Cards are laid out beyond the grid, so the strips above and below
        // hide the overflow instead of letting rows bleed into the header
        graphics.fill(SIDEBAR_W + 1, 0, this.width, gridTop() - 4, Theme.CONTENT);
        graphics.fill(SIDEBAR_W + 1, gridBottom(), this.width, this.height, Theme.CONTENT);

        if (maxScroll > 0) {
            int trackTop = gridTop();
            int trackBottom = gridBottom();
            int trackHeight = trackBottom - trackTop;
            int thumb = Math.max(24, trackHeight * trackHeight / (trackHeight + maxScroll));
            int travel = trackHeight - thumb;
            int offset = maxScroll == 0 ? 0 : Math.round(travel * (scrollShown / maxScroll));
            int x = this.width - 6;
            graphics.fill(x, trackTop, x + 3, trackBottom, Theme.CARD);
            graphics.fill(x, trackTop + offset, x + 3, trackTop + offset + thumb, Theme.accent());
        }

        // Description of whichever card the pointer is over, along the bottom
        List<Module> modules = shown();
        int cols = columns();
        for (int i = 0; i < modules.size(); i++) {
            int x = contentLeft() + (i % cols) * (CARD_W + GAP);
            int y = gridTop() - scroll + (i / cols) * (CARD_H + GAP);
            if (mouseX >= x && mouseX <= x + CARD_W && mouseY >= y && mouseY <= y + CARD_H
                    && mouseY >= gridTop() - 4 && mouseY <= gridBottom()) {
                graphics.text(this.font, modules.get(i).getDescription(),
                        contentLeft(), this.height - 12, Theme.TEXT_DIM, false);
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
