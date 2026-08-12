package gg.spaceclient.ui;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.module.Module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The main menu: a flat dark panel with accent-coloured toggles.
 *
 * Everything interactive is a widget, so the screen never overrides the mouse
 * event methods whose signatures changed in this version.
 */
public class SpaceMenuScreen extends Screen {
    private static final int ROW_H = 26;
    private static final int GAP = 6;
    private static final int COLUMNS = 2;
    private static final int PANEL_W = 460;

    public SpaceMenuScreen() {
        super(Component.literal("Space Client"));
    }

    private int panelLeft() { return (this.width - PANEL_W) / 2; }
    private int contentTop() { return 92; }
    private int columnWidth() { return (PANEL_W - GAP) / COLUMNS; }

    @Override
    protected void init() {
        List<Module> modules = SpaceClient.getModuleManager().getAll();
        int left = panelLeft();
        int top = contentTop();

        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = left + col * (columnWidth() + GAP);
            int y = top + row * (ROW_H + GAP);

            this.addRenderableWidget(new FlatButton(
                    x, y, columnWidth(), ROW_H,
                    module::getName,
                    module::isEnabled,
                    () -> {
                        module.toggle();
                        SpaceClient.getConfigManager().save();
                    }
            ));
        }

        int rows = (modules.size() + COLUMNS - 1) / COLUMNS;
        int bottom = top + rows * (ROW_H + GAP) + 14;

        this.addRenderableWidget(new FlatButton(
                left, bottom, columnWidth(), ROW_H,
                () -> "Appearance",
                () -> false,
                () -> Minecraft.getInstance().gui.setScreen(new AppearanceScreen(this))
        ));

        this.addRenderableWidget(new FlatButton(
                left + columnWidth() + GAP, bottom, columnWidth(), ROW_H,
                () -> "Close",
                () -> false,
                this::onClose
        ));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Backdrop.draw(graphics, this.width, this.height);

        int left = panelLeft();

        // A translucent panel keeps the rows readable over the starfield
        int rows = (SpaceClient.getModuleManager().getAll().size() + COLUMNS - 1) / COLUMNS;
        int panelBottom = contentTop() + rows * (ROW_H + GAP) + ROW_H + 24;
        graphics.fill(left - 18, 20, left + PANEL_W + 18, panelBottom, Theme.PANEL);
        graphics.fill(left - 18, 20, left + PANEL_W + 18, 21, Theme.BORDER);
        graphics.fill(left - 18, panelBottom - 1, left + PANEL_W + 18, panelBottom, Theme.BORDER);

        // Header
        JupiterIcon.draw(graphics, left, 34, 24);
        graphics.text(this.font, "SPACE CLIENT", left + 34, 38, Theme.CYAN, false);
        graphics.text(this.font, "v" + SpaceClient.VERSION + "  ·  Right Shift to close",
                left + 34, 50, Theme.TEXT_DIM, false);

        // Divider under the header
        graphics.fill(left, 74, left + PANEL_W, 75, Theme.BORDER);

        super.extractRenderState(graphics, mouseX, mouseY, delta);

        // Description of whichever row the mouse is over
        List<Module> modules = SpaceClient.getModuleManager().getAll();
        int top = contentTop();
        for (int i = 0; i < modules.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = left + col * (columnWidth() + GAP);
            int y = top + row * (ROW_H + GAP);

            if (mouseX >= x && mouseX <= x + columnWidth() && mouseY >= y && mouseY <= y + ROW_H) {
                String desc = modules.get(i).getDescription();
                graphics.text(this.font, desc, left, this.height - 28, Theme.TEXT_DIM, false);
                break;
            }
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
