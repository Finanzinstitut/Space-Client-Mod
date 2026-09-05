package gg.spaceclient.ui;

import gg.spaceclient.prank.Pranks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Triggers the local prank effects.
 *
 * A grid of buttons, each of which starts an effect and closes the menu so the
 * effect is what you see. Every one of them draws only on your own screen -
 * nothing here reaches the server or another player, by design and by the
 * absence of any code that could.
 */
public class PrankScreen extends Screen {

    private static final int ROW_H = 22;
    private static final int GAP = 6;
    private static final int PANEL_W = 300;

    private final Screen parent;

    public PrankScreen(Screen parent) {
        super(Component.literal("Pranks"));
        this.parent = parent;
    }

    private int panelLeft() { return (this.width - PANEL_W) / 2; }

    private void fire(Pranks.Effect effect, long ms) {
        Pranks.start(effect, ms);
        // Close to the world, not to the parent menu, so the effect is on show
        Minecraft.getInstance().gui.setScreen(null);
    }

    private void fireScreen(Pranks.Screen kind, long ms) {
        Pranks.startScreen(kind, ms);
        Minecraft.getInstance().gui.setScreen(null);
    }

    @Override
    protected void init() {
        int left = panelLeft();
        int y = 70;

        button(left, y, "Fake crash", () -> fire(Pranks.Effect.FAKE_CRASH, 0)); y += ROW_H + GAP;
        button(left, y, "Fake kick", () -> fire(Pranks.Effect.FAKE_KICK, 0)); y += ROW_H + GAP;
        button(left, y, "Fake ban", () -> fire(Pranks.Effect.FAKE_BAN, 0)); y += ROW_H + GAP;
        button(left, y, "Fake lag (5s)", () -> fire(Pranks.Effect.FAKE_LAG, 5000)); y += ROW_H + GAP;
        button(left, y, "Reverse controls (5s)",
                () -> fire(Pranks.Effect.REVERSED_CONTROLS, 5000)); y += ROW_H + GAP;

        // Screen effects share a row of four
        int quarter = (PANEL_W - GAP * 3) / 4;
        screenButton(left, y, quarter, "Shake", Pranks.Screen.SHAKE);
        screenButton(left + quarter + GAP, y, quarter, "Flip", Pranks.Screen.FLIP);
        screenButton(left + (quarter + GAP) * 2, y, quarter, "Static", Pranks.Screen.STATIC);
        screenButton(left + (quarter + GAP) * 3, y, quarter, "Crack", Pranks.Screen.CRACKED);
        y += ROW_H + GAP;

        button(left, y, "Fake chat message",
                () -> {
                    Pranks.queueChat("\u00a7c[Server] \u00a7fYou have been reported to staff");
                    Minecraft.getInstance().gui.setScreen(null);
                });
        y += ROW_H + GAP * 2;

        this.addRenderableWidget(new FlatButton(
                left, y, PANEL_W, ROW_H, () -> "Back", () -> false, this::onClose).asAction());
    }

    private void button(int x, int y, String label, Runnable action) {
        this.addRenderableWidget(new FlatButton(
                x, y, PANEL_W, ROW_H, () -> label, () -> false, action).asAction());
    }

    private void screenButton(int x, int y, int w, String label, Pranks.Screen kind) {
        this.addRenderableWidget(new FlatButton(
                x, y, w, ROW_H, () -> label, () -> false,
                () -> fireScreen(kind, 4000)).asAction());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Backdrop.draw(graphics, this.width, this.height);
        graphics.fill(0, 0, this.width, this.height, 0x44000000);

        int left = panelLeft();
        JupiterIcon.draw(graphics, left, 34, 22);
        graphics.text(Fonts.ui(), "PRANKS", left + 32, 36, Theme.CYAN, false);
        graphics.text(Fonts.ui(), "Local effects - only you see them, nothing leaves your game",
                left + 32, 48, Theme.TEXT_DIM, false);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
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
