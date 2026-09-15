package gg.spaceclient.ui;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.util.Screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Everything adjustable, in two columns.
 *
 * A third layout on purpose: worlds are a row of cards, servers are a list of
 * rows, and settings are a grid. Each screen should be recognisable from its
 * shape alone before a single word is read - if all three looked the same, the
 * only way to know where you are would be the heading.
 *
 * The client's own settings sit in the same grid as the game's rather than
 * behind a separate door. Somebody looking for a brightness slider does not
 * care which half of the software owns it, and making them guess is how a
 * client ends up with two settings screens nobody can tell apart.
 *
 * <h2>Missing tiles are hidden, not greyed out</h2>
 *
 * The game's screens are reached by class name, and a name that no longer
 * exists means the tile is dropped from the grid entirely. A disabled tile
 * invites clicking; an absent one closes the question.
 */
public class SettingsHubScreen extends Screen {

    // Measurements live in ScreenChrome now, so this screen and the account
    // screen cannot drift apart by one edit.
    private static final int TILE_W = ScreenChrome.TILE_W;
    private static final int TILE_H = ScreenChrome.TILE_H;
    private static final int GAP = ScreenChrome.GAP;
    private static final int TOP = ScreenChrome.TOP;

    private final Screen parent;
    private final java.util.List<SettingsTile> tiles = new java.util.ArrayList<>();

    private long openedAt = 0L;

    /** First row on screen, and how many there are in total. */
    private int scrollRow = 0;
    private int rowCount = 0;

    public SettingsHubScreen(Screen parent) {
        super(Component.literal("Settings"));
        this.parent = parent;
    }

    /** One tile before it knows where it will sit. */
    private record Plan(String title, String subtitle, SettingsTile.Mark mark, Runnable action) {}

    @Override
    protected void init() {
        if (openedAt == 0L) openedAt = System.currentTimeMillis();
        tiles.clear();

        java.util.List<Plan> plan = new java.util.ArrayList<>();

        // The client's own, first: this menu is reached from the client's
        // button, so what it owns should be what greets you
        plan.add(new Plan("Space Client", "Modules, HUD and everything this client adds",
                SettingsTile.Mark.CLIENT, () -> Screens.open(new SpaceMenuScreen())));

        plan.add(new Plan("Appearance", "Background, font and accent colour",
                SettingsTile.Mark.SKIN, () -> Screens.open(new AppearanceScreen(this))));

        plan.add(new Plan("HUD editor", "Move and scale what sits on screen",
                SettingsTile.Mark.HUD, this::openHudEditor));

        // Missing until now, which is why this screen could be described as
        // "the settings menu where the account switch also is" by somebody who
        // had to reach it from somewhere else entirely.
        plan.add(new Plan("Accounts", "Switch account or refresh the session",
                SettingsTile.Mark.SKIN, () -> Screens.open(new AccountsScreen(this))));

        vanilla(plan, "Video", "Render distance, brightness and frame rate",
                SettingsTile.Mark.SCREEN,
                "net.minecraft.client.gui.screens.options.VideoSettingsScreen");

        vanilla(plan, "Sound", "Music and volume for each kind of sound",
                SettingsTile.Mark.SOUND,
                "net.minecraft.client.gui.screens.options.SoundOptionsScreen");

        vanilla(plan, "Controls", "Key bindings, mouse and sensitivity",
                SettingsTile.Mark.KEYS,
                "net.minecraft.client.gui.screens.options.controls.ControlsScreen");

        // Under options. rather than at the top level, which is where an
        // earlier guess put it - the tile simply never appeared.
        vanilla(plan, "Language", "The language the game is written in",
                SettingsTile.Mark.LANGUAGE,
                "net.minecraft.client.gui.screens.options.LanguageSelectScreen");

        vanilla(plan, "Chat", "Chat visibility, width and colours",
                SettingsTile.Mark.INFO,
                "net.minecraft.client.gui.screens.options.ChatOptionsScreen");

        vanilla(plan, "Accessibility", "Text background, narrator and motion",
                SettingsTile.Mark.INFO,
                "net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen");

        vanilla(plan, "Resource packs", "Textures, sounds and fonts",
                SettingsTile.Mark.PACKS,
                "net.minecraft.client.gui.screens.packs.PackSelectionScreen");

        vanilla(plan, "All options", "The game's own settings screen, unchanged",
                SettingsTile.Mark.INFO,
                "net.minecraft.client.gui.screens.options.OptionsScreen");

        // Two columns where there is room for two, one where there is not.
        //
        // The width was fixed at two before, and two columns of 230 plus the
        // gap need 470 points of screen. At a large GUI scale there are not 470
        // - the game reports a few hundred - so the right-hand column sat off
        // the edge with half its tiles unreachable.
        int columns = ScreenChrome.columnsFor(this.width);
        int left = ScreenChrome.blockLeft(this.width, columns);

        rowCount = (plan.size() + columns - 1) / columns;
        scrollRow = Math.max(0, Math.min(maxScrollRow(columns), scrollRow));

        int visible = visibleRows();
        for (int slot = 0; slot < visible; slot++) {
            int row = scrollRow + slot;
            if (row >= rowCount) break;

            for (int column = 0; column < columns; column++) {
                int index = row * columns + column;
                if (index >= plan.size()) break;

                Plan entry = plan.get(index);
                SettingsTile tile = new SettingsTile(
                        left + column * (TILE_W + GAP),
                        TOP + slot * (TILE_H + GAP),
                        TILE_W, TILE_H,
                        entry.title(), entry.subtitle(), entry.mark(), entry.action());
                tile.setAppear(0f);
                tiles.add(tile);
                this.addRenderableWidget(tile);
            }
        }

        this.addRenderableWidget(new FlatButton(
                left, ScreenChrome.bottomRow(this.height), 110, 24,
                () -> "Back", () -> false, this::onClose).asAction());
    }

    /**
     * How many rows of tiles fit between the heading and the Back button.
     *
     * Worked out from the screen rather than assumed. The list is eleven tiles
     * long and the space for them is whatever the player's GUI scale leaves -
     * which at the larger scales is less than the list needs, and the tiles
     * that did not fit were simply drawn past the bottom edge with no way to
     * reach them.
     */
    private int visibleRows() {
        return ScreenChrome.rowsBetween(ScreenChrome.bottomRow(this.height) - GAP, TILE_H);
    }

    private int maxScrollRow(int columns) {
        return Math.max(0, rowCount - visibleRows());
    }

    private boolean scrollBy(double amount) {
        int max = Math.max(0, rowCount - visibleRows());
        if (max <= 0) return false;

        int before = scrollRow;
        scrollRow = Math.max(0, Math.min(max, scrollRow - (int) Math.signum(amount)));
        if (scrollRow != before) this.rebuildWidgets();
        return true;
    }

    // Two shapes, neither annotated: the wheel callback gained a second axis
    // and whichever one this version declares is the one that gets called.
    // Copied from ServersScreen, where it is already proven on this version.
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return scrollBy(scrollY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return scrollBy(amount);
    }

    /**
     * Adds a tile only if the screen behind it exists.
     *
     * The class is looked up now rather than when the tile is pressed, so a
     * missing screen costs a gap in the grid instead of a button that swallows
     * a click.
     */
    private void vanilla(java.util.List<Plan> plan, String title, String subtitle,
                         SettingsTile.Mark mark, String className) {
        try {
            Class.forName(className);
        } catch (Throwable ignored) {
            return;
        }
        plan.add(new Plan(title, subtitle, mark, () -> openVanilla(className)));
    }

    /**
     * Opens one of the game's own screens.
     *
     * The options object is read straight off the field. Asking for it through
     * a getter returned null, and because a missing argument used to be passed
     * as null rather than refused, every one of these screens was built with no
     * settings behind it - they opened onto nothing at all. Passing the real
     * object is the fix; refusing to build without it is the guard, so the same
     * mistake shows up as a tile that does not react instead of a blank screen.
     */
    private void openVanilla(String className) {
        // Everything the game is holding, offered by type. Naming three
        // objects by hand was what made most of these tiles do nothing: the
        // language screen wants a LanguageManager and the pack screen a
        // PackRepository, neither of which was on the list, so the strict pass
        // refused and the lenient one passed null and was thrown out.
        Object[] pool = Construct.poolFrom(Minecraft.getInstance(), this);

        Object screen = Construct.strict(className, pool);
        if (screen == null) screen = Construct.of(className, pool);

        if (screen instanceof Screen target) {
            failed = "";
            Screens.open(target);
            return;
        }

        // Said on the screen, not only in a log. A tile that swallows a click
        // is indistinguishable from one that is broken, and this client has
        // spent enough time on things that fail quietly.
        failed = className.substring(className.lastIndexOf('.') + 1)
                + " could not be opened on this version.";
        SpaceClient.LOGGER.warn("Could not open {} on this version", className);
    }

    /** The last tile that would not open, shown under the grid. */
    private String failed = "";

    private void drawFailure(GuiGraphicsExtractor graphics) {
        if (failed.isEmpty()) return;
        graphics.text(this.font, failed,
                (this.width - this.font.width(failed)) / 2,
                ScreenChrome.bottomRow(this.height) - 14, 0xFFFF9AAE, false);
    }

    private void openHudEditor() {
        Screens.open(new HudEditorScreen(this));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        drawFailure(graphics);
        ScreenChrome.background(graphics, this.width, this.height, mouseX, mouseY, delta);

        long now = System.currentTimeMillis() - openedAt;
        for (int i = 0; i < tiles.size(); i++) {
            // Staggered over what is on screen, not over the whole list: with
            // scrolling, the tiles further down would otherwise carry the delay
            // of a position they no longer have and arrive long after the rest.
            tiles.get(i).setAppear(ScreenChrome.appearAt(now, i));
        }

        ScreenChrome.header(graphics, this.font, this.width,
                "Settings", "Space Client " + SpaceClient.VERSION);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        Screens.open(parent);
    }
}
