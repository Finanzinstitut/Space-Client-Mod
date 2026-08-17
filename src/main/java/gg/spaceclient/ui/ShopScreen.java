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
    /**
     * Buying and wearing are different errands, so they get different lists.
     *
     * One combined list made every visit a hunt: the cape you own sat between
     * a dozen you do not, and the price you were weighing sat between things
     * already paid for. Store shows only what is for sale, Wardrobe only what
     * is yours, and each list shrinks as the other grows.
     */
    private enum Tab { STORE, WARDROBE }

    /**
     * Which kind of cosmetic to list.
     *
     * ALL stays the default because with two categories a filter is a
     * convenience, not a necessity - and a player who has not noticed the
     * category row should never be looking at a list that hides things.
     */
    private enum Category {
        ALL("All", null),
        CAPES("Capes", "cape"),
        WINGS("Wings", "wings");

        final String label;
        final String type;
        Category(String label, String type) { this.label = label; this.type = type; }
    }

    private static final int ROW_H = 26;
    private static final int GAP = 6;
    private static final int PANEL_W = 380;

    private final Screen parent;
    /** The shop only ever sells; wearing moved to its own screen. */
    private final Tab tab = Tab.STORE;
    private Category category = Category.ALL;
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

        List<ShopItem> items = visibleItems();
        int left = panelLeft();
        int top = 124;

        int room = this.height - top - 80;
        int perPage = Math.max(1, room / (ROW_H + GAP));
        pageCount = Math.max(1, (items.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pageCount - 1));

        Category[] categories = Category.values();
        int catW = (PANEL_W - GAP * (categories.length - 1)) / categories.length;
        for (int i = 0; i < categories.length; i++) {
            Category c = categories[i];
            this.addRenderableWidget(new FlatButton(
                    left + (catW + GAP) * i, 92, catW, ROW_H,
                    () -> c.label + " (" + countIn(c) + ")",
                    () -> category == c,
                    () -> switchCategory(c)
            ));
        }

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

    /** Only what the current tab is for: unowned to buy, owned to wear. */
    private List<ShopItem> visibleItems() {
        List<ShopItem> out = new java.util.ArrayList<>();
        for (ShopItem item : ShopClient.catalogue()) {
            if (tab == Tab.WARDROBE ? !item.owned() : item.owned()) continue;
            if (category.type != null && !category.type.equals(item.type())) continue;
            out.add(item);
        }
        return out;
    }

    /** How many the current tab holds in a category, for the button labels. */
    private int countIn(Category c) {
        int count = 0;
        for (ShopItem item : ShopClient.catalogue()) {
            if (tab == Tab.WARDROBE ? !item.owned() : item.owned()) continue;
            if (c.type != null && !c.type.equals(item.type())) continue;
            count++;
        }
        return count;
    }

    /** Category changes restart paging for the same reason tab changes do. */
    private void switchCategory(Category target) {
        if (category == target) return;
        category = target;
        page = 0;
        this.rebuildWidgets();
    }

    private int ownedCount() {
        int count = 0;
        for (ShopItem item : ShopClient.catalogue()) if (item.owned()) count++;
        return count;
    }

    /** A tab change restarts paging, since page three of the old list means nothing here. */
    private void switchTo(Tab target) {
        if (tab == target) return;
        tab = target;
        page = 0;
        this.rebuildWidgets();
    }

    private String label(ShopItem item, boolean worn) {
        if (worn) return item.name() + "  -  worn";
        if (item.owned()) return item.name() + "  -  click to wear";
        boolean affordable = ShopClient.balance() >= item.price();
        return item.name() + "  -  " + item.price() + " points"
                + (affordable ? "" : "  (not enough)");
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

        graphics.text(this.font,
                "Click an item to buy it. Wear it from the Wardrobe.",
                left, 78, Theme.TEXT_DIM, false);

        // An empty list with no explanation reads as a failure to load
        if (visibleItems().isEmpty() && !ShopClient.isBusy()) {
            String what = category == Category.ALL ? "" : " " + category.label.toLowerCase();
            String empty = tab == Tab.STORE
                    ? "You own every" + (what.isEmpty() ? "thing in the store." : what + " there is.")
                    : "No" + (what.isEmpty() ? "thing" : what) + " owned yet - try the Store.";
            graphics.text(this.font, empty,
                    left + (PANEL_W - this.font.width(empty)) / 2, 180,
                    Theme.TEXT_DIM, false);
        }

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
