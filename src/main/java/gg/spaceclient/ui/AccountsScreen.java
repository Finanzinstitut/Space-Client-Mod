package gg.spaceclient.ui;

import gg.spaceclient.session.LauncherAccount;
import gg.spaceclient.session.LauncherAccounts;
import gg.spaceclient.session.SessionManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Switches between the accounts the launcher knows about, and refreshes the
 * current session, without leaving the game.
 */
public class AccountsScreen extends Screen {

    private final Screen parent;
    private List<LauncherAccount> accounts = List.of();
    private final java.util.List<SettingsTile> tiles = new java.util.ArrayList<>();

    private long openedAt = 0L;
    private int scrollRow = 0;
    private int rowCount = 0;

    public AccountsScreen(Screen parent) {
        super(Component.literal("Accounts"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (openedAt == 0L) openedAt = System.currentTimeMillis();
        accounts = LauncherAccounts.load();
        tiles.clear();

        String playing = Minecraft.getInstance().getUser().getName();

        int columns = ScreenChrome.columnsFor(this.width);
        int left = ScreenChrome.blockLeft(this.width, columns);
        int blockWidth = columns * ScreenChrome.TILE_W + (columns - 1) * ScreenChrome.GAP;

        // The two controls that must always be reachable are placed first, from
        // the bottom up. Everything else gets the room that is left.
        //
        // They used to be stacked after the account rows, which works exactly
        // as long as the accounts fit: with enough of them the Back button was
        // drawn below the bottom edge, and the only way out of this screen was
        // the escape key.
        int bottom = ScreenChrome.bottomRow(this.height);
        int half = (blockWidth - ScreenChrome.GAP) / 2;

        this.addRenderableWidget(new FlatButton(
                left, bottom, half, 24,
                () -> "Refresh session",
                () -> false,
                () -> SessionManager.refreshCurrent()
                        .thenRun(() -> Minecraft.getInstance().execute(this::rebuildWidgets))
        ).asAction());

        this.addRenderableWidget(new FlatButton(
                left + half + ScreenChrome.GAP, bottom, half, 24,
                () -> "Back",
                () -> false,
                this::onClose
        ).asAction());

        int visible = ScreenChrome.rowsBetween(bottom - ScreenChrome.GAP, ScreenChrome.TILE_H);
        rowCount = (accounts.size() + columns - 1) / columns;
        scrollRow = Math.max(0, Math.min(Math.max(0, rowCount - visible), scrollRow));

        // Built out of the same tile as the settings grid, so moving between
        // the two screens is moving within one place rather than between two.
        for (int slot = 0; slot < visible; slot++) {
            int row = scrollRow + slot;
            if (row >= rowCount) break;

            for (int column = 0; column < columns; column++) {
                int index = row * columns + column;
                if (index >= accounts.size()) break;

                LauncherAccount account = accounts.get(index);
                boolean current = account.username().equalsIgnoreCase(playing);

                SettingsTile tile = new SettingsTile(
                        left + column * (ScreenChrome.TILE_W + ScreenChrome.GAP),
                        ScreenChrome.TOP + slot * (ScreenChrome.TILE_H + ScreenChrome.GAP),
                        ScreenChrome.TILE_W, ScreenChrome.TILE_H,
                        account.username(),
                        account.offline() ? "Offline profile" : "Microsoft account",
                        SettingsTile.Mark.SKIN,
                        () -> SessionManager.applyAccount(account)
                                .thenRun(() -> Minecraft.getInstance().execute(this::rebuildWidgets))
                ).withSelected(() -> current);

                tile.setAppear(0f);
                tiles.add(tile);
                this.addRenderableWidget(tile);
            }
        }
    }

    private boolean scrollBy(double amount) {
        int visible = ScreenChrome.rowsBetween(
                ScreenChrome.bottomRow(this.height) - ScreenChrome.GAP, ScreenChrome.TILE_H);
        int max = Math.max(0, rowCount - visible);
        if (max <= 0) return false;

        int before = scrollRow;
        scrollRow = Math.max(0, Math.min(max, scrollRow - (int) Math.signum(amount)));
        if (scrollRow != before) this.rebuildWidgets();
        return true;
    }

    // Both shapes, for the same reason as everywhere else in this mod: the
    // wheel callback gained a second axis and only one of these is real here.
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return scrollBy(scrollY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return scrollBy(amount);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        ScreenChrome.background(graphics, this.width, this.height, mouseX, mouseY, delta);

        long now = System.currentTimeMillis() - openedAt;
        for (int i = 0; i < tiles.size(); i++) {
            tiles.get(i).setAppear(ScreenChrome.appearAt(now, i));
        }

        // Read live from the game, so a failed swap is visible rather than
        // hidden behind the name the launcher believes is in force.
        ScreenChrome.header(graphics, this.font, this.width,
                "Accounts", "Playing as " + Minecraft.getInstance().getUser().getName());

        if (accounts.isEmpty()) {
            String note = LauncherAccounts.isAvailable()
                    ? "The launcher has no accounts signed in."
                    : "Launcher accounts not found - start the game from Space Client.";
            graphics.text(this.font, note,
                    (this.width - this.font.width(note)) / 2, ScreenChrome.TOP + 10,
                    Theme.TEXT_DIM, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);

        String status = SessionManager.status();
        if (!status.isEmpty()) {
            graphics.text(this.font, status,
                    (this.width - this.font.width(status)) / 2,
                    ScreenChrome.bottomRow(this.height) - 14,
                    SessionManager.isBusy() ? Theme.TEXT_DIM : Theme.CYAN, false);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
