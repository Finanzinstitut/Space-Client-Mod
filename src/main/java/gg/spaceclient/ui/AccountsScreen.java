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
    private static final int ROW_H = 26;
    private static final int GAP = 6;
    private static final int PANEL_W = 340;

    /** Where the account rows start, under the heading. */
    private static final int TOP = 96;

    private final Screen parent;
    private List<LauncherAccount> accounts = List.of();
    private int scrollRow = 0;

    public AccountsScreen(Screen parent) {
        super(Component.literal("Accounts"));
        this.parent = parent;
    }

    private int panelLeft() { return (this.width - PANEL_W) / 2; }

    @Override
    protected void init() {
        accounts = LauncherAccounts.load();
        String active = LauncherAccounts.activeUuid();
        String playing = Minecraft.getInstance().getUser().getName();

        int left = panelLeft();

        // The two buttons that must always be reachable are placed first, from
        // the bottom up. Everything else gets the room that is left.
        //
        // They used to be stacked after the account rows, which works exactly
        // as long as the accounts fit: with enough of them the Back button was
        // drawn below the bottom edge, and the only way out of this screen was
        // the escape key.
        int backY = this.height - 34;
        int refreshY = backY - ROW_H - GAP;

        this.addRenderableWidget(new FlatButton(
                left, refreshY, PANEL_W, ROW_H,
                () -> "Refresh current session",
                () -> false,
                () -> SessionManager.refreshCurrent()
                        .thenRun(() -> Minecraft.getInstance().execute(this::rebuildWidgets))
        ).asAction());

        this.addRenderableWidget(new FlatButton(
                left, backY, PANEL_W, ROW_H,
                () -> "Back",
                () -> false,
                this::onClose
        ).asAction());

        int visible = visibleRows(refreshY);
        scrollRow = Math.max(0, Math.min(Math.max(0, accounts.size() - visible), scrollRow));

        int y = TOP;
        for (int slot = 0; slot < visible; slot++) {
            int index = scrollRow + slot;
            if (index >= accounts.size()) break;

            LauncherAccount account = accounts.get(index);
            boolean current = account.username().equalsIgnoreCase(playing);

            this.addRenderableWidget(new FlatButton(
                    left, y, PANEL_W, ROW_H,
                    () -> account.username() + (account.offline() ? "  (offline)" : ""),
                    () -> current,
                    () -> SessionManager.applyAccount(account)
                            .thenRun(() -> Minecraft.getInstance().execute(this::rebuildWidgets))
            ));
            y += ROW_H + GAP;
        }
    }

    /** How many account rows fit between the heading and the buttons below. */
    private int visibleRows(int floor) {
        int room = floor - TOP - GAP;
        return Math.max(1, room / (ROW_H + GAP));
    }

    private boolean scrollBy(double amount) {
        int max = Math.max(0, accounts.size() - visibleRows(this.height - 34 - ROW_H - GAP));
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
        // The same backdrop as the menus this is reached from. It was the only
        // screen in the flow still drawing the plain fallback, which read as
        // arriving somewhere that belonged to a different program.
        if (!MenuWallpaper.draw(graphics, this.width, this.height, mouseX, mouseY, delta)) {
            Backdrop.draw(graphics, this.width, this.height);
        }

        int left = panelLeft();
        graphics.fill(left - 18, 20, left + PANEL_W + 18, this.height - 20, Theme.PANEL);
        graphics.fill(left - 18, 20, left + PANEL_W + 18, 21, Theme.BORDER);

        JupiterIcon.draw(graphics, left, 34, 24);
        graphics.text(this.font, "ACCOUNTS", left + 34, 38, Theme.CYAN, false);
        // Read live from the game, so a failed swap is visible rather than hidden
        graphics.text(this.font, "Game reports: " + Minecraft.getInstance().getUser().getName(),
                left + 34, 50, Theme.TEXT_DIM, false);
        graphics.fill(left, 74, left + PANEL_W, 75, Theme.BORDER);

        if (accounts.isEmpty()) {
            graphics.text(this.font,
                    LauncherAccounts.isAvailable()
                            ? "The launcher has no accounts signed in."
                            : "Launcher accounts not found - start the game from Space Client.",
                    left, 100, Theme.TEXT_DIM, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);

        String status = SessionManager.status();
        if (!status.isEmpty()) {
            graphics.text(this.font, status, left, this.height - 34,
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
