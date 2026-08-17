package gg.spaceclient.ui;

import gg.spaceclient.shop.ShopClient;
import gg.spaceclient.shop.ShopItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * What you own, and what you are wearing.
 *
 * Split out of the shop because buying and dressing are different errands.
 * The shop is somewhere you go when you want something new; the wardrobe is
 * somewhere you go every time you feel like changing, which makes it menu
 * furniture rather than a corner of a store.
 *
 * The right hand third is reserved for a live view of the player, so a cape
 * can be judged on a body instead of from a name in a list.
 */
public class WardrobeScreen extends Screen {

    private static final int SIDEBAR_W = 148;
    private static final int PAD = 18;
    private static final int ROW_H = 24;
    private static final int GAP = 6;

    /** Everything right of this belongs to the model view. */
    private static final int PREVIEW_W = 200;

    private final Screen parent;

    private String category = null;
    private int scroll = 0;
    private int maxScroll = 0;

    private final List<FlatButton> rows = new ArrayList<>();
    private final List<Integer> rowBaseY = new ArrayList<>();

    /** How far the model has been turned, in degrees, and the drag in progress. */
    private float spin = 180f;
    private boolean dragging = false;
    private int dragFromX = 0;
    private float spinAtGrab = 0f;

    public WardrobeScreen(Screen parent) {
        super(Component.literal("Wardrobe"));
        this.parent = parent;
    }

    private int listLeft() { return SIDEBAR_W + PAD; }
    private int listRight() { return this.width - PREVIEW_W - PAD * 2; }
    private int listTop() { return 108; }
    private int listBottom() { return this.height - PAD; }
    private int previewLeft() { return this.width - PREVIEW_W - PAD; }

    private List<ShopItem> owned() {
        List<ShopItem> out = new ArrayList<>();
        for (ShopItem item : ShopClient.catalogue()) {
            if (!item.owned()) continue;
            if (category != null && !category.equals(item.type())) continue;
            out.add(item);
        }
        return out;
    }

    /** Worn state lives in the equipped map rather than on the item record. */
    private static boolean isWorn(ShopItem item) {
        return item.id().equals(ShopClient.equipped().get(item.type()));
    }

    private int countOwned(String type) {
        int count = 0;
        for (ShopItem item : ShopClient.catalogue()) {
            if (!item.owned()) continue;
            if (type != null && !type.equals(item.type())) continue;
            count++;
        }
        return count;
    }

    @Override
    protected void init() {
        ShopClient.refresh();

        int x = listLeft();
        x += chip(x, 74, "All", null) + 6;
        if (countOwned("cape") > 0) x += chip(x, 74, "Capes", "cape") + 6;
        if (countOwned("wings") > 0) x += chip(x, 74, "Wings", "wings") + 6;

        rows.clear();
        rowBaseY.clear();

        List<ShopItem> items = owned();
        int width = listRight() - listLeft();

        for (int i = 0; i < items.size(); i++) {
            ShopItem item = items.get(i);
            int y = listTop() + i * (ROW_H + GAP);

            FlatButton row = new FlatButton(
                    listLeft(), y - scroll, width, ROW_H,
                    () -> item.name() + (isWorn(item) ? "  -  worn" : ""),
                    () -> isWorn(item),
                    () -> ShopClient.equip(item.id())
            );
            this.addRenderableWidget(row);
            rows.add(row);
            rowBaseY.add(y);
        }

        int needed = items.size() * (ROW_H + GAP);
        maxScroll = Math.max(0, needed - (listBottom() - listTop()));
        scroll = Math.min(scroll, maxScroll);

        this.addRenderableWidget(new FlatButton(
                listLeft(), this.height - 34, 90, ROW_H,
                () -> "Back", () -> false,
                () -> Minecraft.getInstance().gui.setScreen(parent)
        ).asAction());
    }

    private int chip(int x, int y, String label, String value) {
        int w = this.font.width(label) + 22;
        this.addRenderableWidget(new NavButton(
                x, y, w, 22, NavButton.Style.CHIP,
                () -> label,
                () -> java.util.Objects.equals(category, value),
                () -> { category = value; scroll = 0; this.rebuildWidgets(); }
        ));
        return w;
    }

    private void reposition() {
        for (int i = 0; i < rows.size() && i < rowBaseY.size(); i++) {
            rows.get(i).setY(rowBaseY.get(i) - scroll);
        }
    }

    /** No @Override, for the reason given in SpaceMenuScreen. */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return scrollList(scrollY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return scrollList(amount);
    }

    private boolean scrollList(double amount) {
        if (maxScroll <= 0) return false;
        int before = scroll;
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (amount * 20)));
        if (scroll != before) reposition();
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Backdrop.draw(graphics, this.width, this.height);

        graphics.fill(0, 0, SIDEBAR_W, this.height, Theme.SIDEBAR);
        graphics.fill(SIDEBAR_W, 0, this.width, this.height, Theme.CONTENT);
        graphics.fill(SIDEBAR_W, 0, SIDEBAR_W + 1, this.height, Theme.BORDER);

        JupiterIcon.draw(graphics, 16, 24, 22);
        graphics.text(this.font, "SPACE", 46, 24, Theme.accent(), false);
        graphics.text(this.font, "CLIENT", 46, 36, Theme.TEXT, false);

        graphics.text(this.font, "Wardrobe", listLeft(), 30, Theme.TEXT, false);
        int total = owned().size();
        graphics.text(this.font,
                total + (total == 1 ? " item owned" : " items owned"),
                listLeft(), 44, Theme.TEXT_DIM, false);

        super.extractRenderState(graphics, mouseX, mouseY, delta);

        // Hide rows that scrolled past the list bounds
        graphics.fill(SIDEBAR_W + 1, 96, listRight() + PAD, listTop() - 4, Theme.CONTENT);
        graphics.fill(SIDEBAR_W + 1, listBottom(), listRight() + PAD, this.height - 40, Theme.CONTENT);

        // --- the model bay ---
        int px1 = previewLeft();
        int px2 = px1 + PREVIEW_W;
        graphics.fill(px1, listTop() - 34, px2, listBottom(), Theme.CARD);
        graphics.fill(px1, listTop() - 34, px2, listTop() - 33, Theme.BORDER);
        graphics.fill(px1, listBottom() - 1, px2, listBottom(), Theme.BORDER);
        graphics.fill(px1, listTop() - 34, px1 + 1, listBottom(), Theme.BORDER);
        graphics.fill(px2 - 1, listTop() - 34, px2, listBottom(), Theme.BORDER);

        String heading = "Preview";
        graphics.text(this.font, heading,
                px1 + (PREVIEW_W - this.font.width(heading)) / 2, listTop() - 26,
                Theme.TEXT, false);

        PlayerPreview.draw(graphics, px1, listTop() - 12, px2, listBottom() - 24, spin);

        String hint = "drag to turn";
        graphics.text(this.font, hint,
                px1 + (PREVIEW_W - this.font.width(hint)) / 2, listBottom() - 16,
                Theme.TEXT_DIM, false);

        if (owned().isEmpty()) {
            String empty = "Nothing owned yet - visit the Shop.";
            graphics.text(this.font, empty, listLeft(), listTop() + 10, Theme.TEXT_DIM, false);
        }

        trackDrag(mouseX, mouseY);
    }

    /**
     * Turns the model while the pointer is held down inside the bay.
     *
     * Done by watching the pointer each frame rather than by handling drag
     * events, because this version changed those signatures and the rest of
     * this codebase avoids them for that reason.
     */
    private void trackDrag(int mouseX, int mouseY) {
        boolean held = net.minecraft.client.Minecraft.getInstance().mouseHandler != null
                && isLeftDown();
        boolean inside = mouseX >= previewLeft() && mouseX <= previewLeft() + PREVIEW_W
                && mouseY >= listTop() - 34 && mouseY <= listBottom();

        if (held && inside && !dragging) {
            dragging = true;
            dragFromX = mouseX;
            spinAtGrab = spin;
        } else if (!held) {
            dragging = false;
        }

        if (dragging) {
            spin = spinAtGrab + (mouseX - dragFromX) * 1.4f;
        }
    }

    /**
     * Whether the left button is down, read straight from the window.
     *
     * Deliberately without any Minecraft input class, so a rename in the input
     * rework cannot break the build; a failure here simply means the model
     * does not turn.
     */
    private boolean isLeftDown() {
        try {
            long window = net.minecraft.client.Minecraft.getInstance().getWindow().getWindow();
            return org.lwjgl.glfw.GLFW.glfwGetMouseButton(
                    window, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT)
                    == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        } catch (Throwable ignored) {
            return false;
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
