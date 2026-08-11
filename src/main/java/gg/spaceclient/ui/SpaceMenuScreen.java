package gg.spaceclient.ui;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.module.Category;
import gg.spaceclient.module.Module;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** The main menu, opened with Right Shift. */
public class SpaceMenuScreen extends Screen {
    private static final int CARD_W = 210;
    private static final int CARD_H = 46;
    private static final int GAP = 10;
    private static final int COLUMNS = 2;

    private Category selected = Category.HUD;
    private int scroll = 0;

    public SpaceMenuScreen() {
        super(Component.literal("Space Client"));
    }

    private List<Module> visibleModules() {
        List<Module> out = new ArrayList<>();
        for (Module m : SpaceClient.getModuleManager().getAll()) {
            if (m.getCategory() == selected) out.add(m);
        }
        return out;
    }

    private int gridLeft() {
        return (width - (COLUMNS * CARD_W + (COLUMNS - 1) * GAP)) / 2;
    }

    private int gridTop() {
        return 96;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.fill(0, 0, width, height, Theme.BACKDROP);

        // Header
        context.drawString(font, "SPACE CLIENT", 30, 26, Theme.ACCENT_LIGHT, true);
        context.drawString(font, "Right Shift to close", 30, 40, Theme.TEXT_DIM, false);

        // Category tabs
        int tabX = 30;
        for (Category cat : Category.values()) {
            String label = cat.getDisplayName();
            int w = font.width(label) + 22;
            boolean active = cat == selected;

            context.fill(tabX, 60, tabX + w, 84, active ? Theme.CARD_ON : Theme.PANEL_LIGHT);
            if (active) {
                context.fill(tabX, 82, tabX + w, 84, Theme.ACCENT_LIGHT);
            }
            context.drawString(font, label, tabX + 11, 68, active ? Theme.TEXT : Theme.TEXT_DIM, false);
            tabX += w + 6;
        }

        // Module cards
        List<Module> modules = visibleModules();
        int left = gridLeft();
        int top = gridTop() - scroll;

        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = left + col * (CARD_W + GAP);
            int y = top + row * (CARD_H + GAP);

            if (y + CARD_H < gridTop() || y > height) continue;

            boolean hovered = mouseX >= x && mouseX <= x + CARD_W && mouseY >= y && mouseY <= y + CARD_H;
            boolean on = module.isEnabled();

            context.fill(x, y, x + CARD_W, y + CARD_H, on ? Theme.CARD_ON : Theme.CARD_OFF);
            int border = on ? Theme.ACCENT_LIGHT : (hovered ? Theme.ACCENT : Theme.BORDER);
            context.fill(x, y, x + CARD_W, y + 1, border);
            context.fill(x, y + CARD_H - 1, x + CARD_W, y + CARD_H, border);
            context.fill(x, y, x + 1, y + CARD_H, border);
            context.fill(x + CARD_W - 1, y, x + CARD_W, y + CARD_H, border);

            context.drawString(font, module.getName().toUpperCase(),
                    x + 12, y + 10, on ? Theme.TEXT : Theme.TEXT_DIM, true);

            String desc = module.getDescription();
            if (font.width(desc) > CARD_W - 44) {
                desc = font.plainSubstrByWidth(desc, CARD_W - 52) + "...";
            }
            context.drawString(font, desc, x + 12, y + 25, Theme.TEXT_DIM, false);

            // Settings affordance, mirrors the "..." on the cards
            context.drawString(font, "...", x + CARD_W - 22, y + 18, Theme.TEXT_DIM, false);
        }

        // Footer hint
        context.drawString(font, "Left click: toggle    Right click: settings    E: HUD editor",
                30, height - 22, Theme.TEXT_DIM, false);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Category tabs
        int tabX = 30;
        for (Category cat : Category.values()) {
            int w = font.width(cat.getDisplayName()) + 22;
            if (mouseX >= tabX && mouseX <= tabX + w && mouseY >= 60 && mouseY <= 84) {
                selected = cat;
                scroll = 0;
                return true;
            }
            tabX += w + 6;
        }

        List<Module> modules = visibleModules();
        int left = gridLeft();
        int top = gridTop() - scroll;

        for (int i = 0; i < modules.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = left + col * (CARD_W + GAP);
            int y = top + row * (CARD_H + GAP);

            if (mouseX >= x && mouseX <= x + CARD_W && mouseY >= y && mouseY <= y + CARD_H) {
                Module module = modules.get(i);
                boolean settingsClick = button == 1 || mouseX >= x + CARD_W - 28;

                if (settingsClick) {
                    if (!module.getSettings().isEmpty()) {
                        assert client != null;
                        client.setScreen(new ModuleSettingsScreen(this, module));
                    }
                } else {
                    module.toggle();
                    SpaceClient.getConfigManager().save();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int rows = (visibleModules().size() + COLUMNS - 1) / COLUMNS;
        int contentHeight = rows * (CARD_H + GAP);
        int maxScroll = Math.max(0, contentHeight - (height - gridTop() - 40));
        scroll = (int) Math.max(0, Math.min(maxScroll, scroll - vertical * 20));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // E opens the HUD editor, matching the footer hint
        if (keyCode == 69) {
            assert client != null;
            client.setScreen(new HudEditorScreen(this));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
