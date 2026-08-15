package gg.spaceclient.ui;

import gg.spaceclient.shop.ShopClient;
import gg.spaceclient.shop.ShopItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The Galaxy Points shop.
 *
 * Nothing is decided here: the balance, the catalogue and whether a purchase
 * goes through all come from the server. A refusal is shown in the server's own
 * words rather than guessed at, because "not enough points" and "already owned"
 * are worth telling apart.
 */
public class ShopScreen extends Screen {
    private static final int ROW_H = 26;
    private static final int GAP = 6;
    private static final int PANEL_W = 380;

    private final Screen parent;
    private int page = 0;
    private int pageCount = 1;

    /**
     * Whether the catalogue has been asked for yet.
     *
     * Loading from init() and then rebuilding the widgets calls init() again,
     * which asks again, which rebuilds again - the screen never settles and
     * sits on "working..." forever. The request happens once per opening
     * instead, and a rebuild after it arrives does not trigger another.
     */
    private boolean requested = false;

    public ShopScreen(Screen parent) {
        super(Component.literal("Shop"));
        this.parent = parent;
    }

    private int panelLeft() { return (this.width - PANEL_W) / 2; }

    @Override
    protected void init() {
        if (!requested) {
            requested = true;
            ShopClient.refresh().thenRun(() ->
                    Minecraft.getInstance().execute(this::rebuildWidgets));
        }

        List<ShopItem> items = ShopClient.catalogue();
        int left = panelLeft();
        int top = 100;

        int room = this.height - top - 80;
        int perPage = Math.max(1, room / (ROW_H + GAP));
        pageCount = Math.max(1, (items.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pageCount - 1));

        int start = page * perPage;
        int end = Math.min(items.size(), start + perPage);
        int y = top;

        for (int i = start; i < end; i++) {
            ShopItem item = items.get(i);
            boolean worn = item.id().equals(ShopClient.equipped().get(item.type()));

            this.addRenderableWidget(new FlatButton(
                    left, y, PANEL_W, ROW_H,
                    () -> label(item, worn),
                    () -> worn,
                    () -> {
                        // Owning it means the click is about wearing it; not
                        // owning it means the click is a purchase.
                        if (item.owned()) {
                            ShopClient.equip(worn ? "" : item.id())
                                    .thenRun(() -> Minecraft.getInstance().execute(this::rebuildWidgets));
                        } else {
                            ShopClient.buy(item.id())
                                    .thenRun(() -> Minecraft.getInstance().execute(this::rebuildWidgets));
                        }
                    }
            ));
            y += ROW_H + GAP;
        }

        int bottom = this.height - 34;

        if (pageCount > 1) {
            int third = (PANEL_W - GAP * 2) / 3;

            this.addRenderableWidget(new FlatButton(
                    left, bottom, third, ROW_H, () -> "< Page", () -> false,
                    () -> { if (page > 0) { page--; this.rebuildWidgets(); } }
            ).asAction());

            this.addRenderableWidget(new FlatButton(
                    left + third + GAP, bottom, third, ROW_H, () -> "Back", () -> false,
                    this::onClose
            ).asAction());

            this.addRenderableWidget(new FlatButton(
                    left + (third + GAP) * 2, bottom, third, ROW_H, () -> "Page >", () -> false,
                    () -> { if (page < pageCount - 1) { page++; this.rebuildWidgets(); } }
            ).asAction());
        } else {
            this.addRenderableWidget(new FlatButton(
                    left, bottom, PANEL_W, ROW_H, () -> "Back", () -> false, this::onClose
            ).asAction());
        }
    }

    private String label(ShopItem item, boolean worn) {
        if (worn) return item.name() + "  -  worn";
        if (item.owned()) return item.name() + "  -  owned";
        return item.name() + "  -  " + item.price() + " points";
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Backdrop.draw(graphics, this.width, this.height);

        int left = panelLeft();
        graphics.fill(left - 18, 20, left + PANEL_W + 18, this.height - 20, Theme.PANEL);
        graphics.fill(left - 18, 20, left + PANEL_W + 18, 21, Theme.BORDER);

        JupiterIcon.draw(graphics, left, 34, 24);
        graphics.text(this.font, "SHOP", left + 34, 38, Theme.CYAN, false);
        graphics.text(this.font, "Cosmetics for Galaxy Points",
                left + 34, 50, Theme.TEXT_DIM, false);

        String balance = ShopClient.balance() + " points";
        graphics.text(this.font, balance,
                left + PANEL_W - this.font.width(balance), 38, Theme.TEXT, false);

        graphics.fill(left, 74, left + PANEL_W, 75, Theme.BORDER);

        // Owning something is a click away from wearing it, which is not obvious
        graphics.text(this.font, "Click to buy. Click again to wear or take off.",
                left, 84, Theme.TEXT_DIM, false);

        super.extractRenderState(graphics, mouseX, mouseY, delta);

        String status = ShopClient.isBusy() ? "working..." : ShopClient.status();
        if (status != null && !status.isEmpty()) {
            graphics.text(this.font, status, left, this.height - 56, Theme.CYAN, false);
        }

        if (pageCount > 1) {
            String label = "Page " + (page + 1) + " of " + pageCount;
            graphics.text(this.font, label,
                    left + PANEL_W - this.font.width(label), 60, Theme.TEXT_DIM, false);
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
