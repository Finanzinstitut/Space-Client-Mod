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

    /** Settings that are not inside a group, so they are not listed twice. */
    private static java.util.List<gg.spaceclient.setting.Setting> ungrouped(Module module) {
        java.util.Set<gg.spaceclient.setting.Setting> inGroups = new java.util.HashSet<>();
        module.getGroups().forEach(g -> inGroups.addAll(g.settings()));
        return module.getSettings().stream()
                .filter(s -> !inGroups.contains(s))
                .toList();
    }
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

            // The button remembers where the mouse was, so a click on the right
            // hand strip opens settings while the rest of the row toggles.
            FlatButton[] holder = new FlatButton[1];
            holder[0] = new FlatButton(
                    x, y, columnWidth(), ROW_H,
                    module::getName,
                    module::isEnabled,
                    () -> {
                        FlatButton button = holder[0];
                        boolean onGear = button.lastMouseX() >= x + columnWidth() - 34;

                        if (onGear && module.hasSettings()) {
                            Minecraft.getInstance().gui.setScreen(new SettingsScreen(
                                    this, module.getName(), module.getDescription(),
                                    ungrouped(module), module.getGroups()));
                            return;
                        }
                        button.flash();
                        module.toggle();
                        SpaceClient.getConfigManager().save();
                    }
            );
            if (module.hasSettings()) holder[0].withGear();
            this.addRenderableWidget(holder[0]);
        }

        int rows = (modules.size() + COLUMNS - 1) / COLUMNS;
        int bottom = top + rows * (ROW_H + GAP) + 14;

        int quarter = (PANEL_W - GAP * 3) / 4;

        this.addRenderableWidget(new FlatButton(
                left, bottom, quarter, ROW_H,
                () -> "Move HUD",
                () -> false,
                () -> Minecraft.getInstance().gui.setScreen(new HudEditorScreen(this))
        ));

        this.addRenderableWidget(new FlatButton(
                left + quarter + GAP, bottom, quarter, ROW_H,
                () -> "Accounts",
                () -> false,
                () -> Minecraft.getInstance().gui.setScreen(new AccountsScreen(this))
        ));

        this.addRenderableWidget(new FlatButton(
                left + (quarter + GAP) * 2, bottom, quarter, ROW_H,
                () -> "Appearance",
                () -> false,
                () -> Minecraft.getInstance().gui.setScreen(new AppearanceScreen(this))
        ));

        this.addRenderableWidget(new FlatButton(
                left + (quarter + GAP) * 3, bottom, quarter, ROW_H,
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
        graphics.text(this.font,
                "v" + SpaceClient.VERSION + "  ·  click to toggle, gear icon for settings",
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
