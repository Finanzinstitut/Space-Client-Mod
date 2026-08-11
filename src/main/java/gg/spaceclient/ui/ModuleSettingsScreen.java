package gg.spaceclient.ui;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.module.Module;
import gg.spaceclient.setting.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Per-module settings: toggles, sliders, mode pickers and colour channels. */
public class ModuleSettingsScreen extends Screen {
    private static final int ROW_H = 34;
    private static final int PANEL_W = 380;

    private final Screen parent;
    private final Module module;
    private int scroll = 0;

    public ModuleSettingsScreen(Screen parent, Module module) {
        super(Text.literal(module.getName()));
        this.parent = parent;
        this.module = module;
    }

    private int panelLeft() { return (width - PANEL_W) / 2; }
    private int contentTop() { return 80; }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.fill(0, 0, width, height, Theme.BACKDROP);

        context.drawText(textRenderer, module.getName().toUpperCase(), panelLeft(), 40, Theme.ACCENT_LIGHT, true);
        context.drawText(textRenderer, module.getDescription(), panelLeft(), 54, Theme.TEXT_DIM, false);

        int x = panelLeft();
        int y = contentTop() - scroll;

        for (Setting setting : module.getSettings()) {
            if (y + ROW_H >= contentTop() && y <= height) {
                drawSetting(context, setting, x, y, mouseX, mouseY);
            }
            y += ROW_H;
        }

        context.drawText(textRenderer, "Backspace to go back", panelLeft(), height - 22, Theme.TEXT_DIM, false);
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawSetting(DrawContext context, Setting setting, int x, int y, int mouseX, int mouseY) {
        context.fill(x, y, x + PANEL_W, y + ROW_H - 4, Theme.PANEL_LIGHT);
        context.drawText(textRenderer, setting.getName(), x + 10, y + 6, Theme.TEXT, false);

        if (setting instanceof BooleanSetting b) {
            int tx = x + PANEL_W - 46;
            int ty = y + 8;
            context.fill(tx, ty, tx + 36, ty + 14, b.get() ? Theme.ACCENT : Theme.CARD_OFF);
            int knobX = b.get() ? tx + 22 : tx + 2;
            context.fill(knobX, ty + 2, knobX + 12, ty + 12, Theme.TEXT);

        } else if (setting instanceof IntSetting i) {
            int barX = x + 10;
            int barY = y + 22;
            int barW = PANEL_W - 70;
            context.fill(barX, barY, barX + barW, barY + 4, Theme.CARD_OFF);
            float pct = (i.get() - i.getMin()) / (float) Math.max(1, i.getMax() - i.getMin());
            context.fill(barX, barY, barX + (int) (barW * pct), barY + 4, Theme.ACCENT_LIGHT);
            context.drawText(textRenderer, String.valueOf(i.get()),
                    x + PANEL_W - 46, y + 18, Theme.TEXT_DIM, false);

        } else if (setting instanceof ModeSetting m) {
            String value = m.get();
            int tw = textRenderer.getWidth(value);
            context.drawText(textRenderer, value, x + PANEL_W - tw - 14, y + 6, Theme.ACCENT_LIGHT, false);
            context.drawText(textRenderer, "click to cycle", x + 10, y + 20, Theme.TEXT_DIM, false);

        } else if (setting instanceof ColorSetting c) {
            int sw = x + PANEL_W - 46;
            context.fill(sw, y + 6, sw + 36, y + 22, c.get());
            context.fill(sw, y + 6, sw + 36, y + 7, Theme.BORDER);
            context.drawText(textRenderer, "R/G/B: scroll over swatch", x + 10, y + 20, Theme.TEXT_DIM, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = panelLeft();
        int y = contentTop() - scroll;

        for (Setting setting : module.getSettings()) {
            boolean inRow = mouseY >= y && mouseY <= y + ROW_H - 4 && mouseX >= x && mouseX <= x + PANEL_W;
            if (inRow) {
                if (setting instanceof BooleanSetting b) {
                    b.toggle();
                } else if (setting instanceof ModeSetting m) {
                    m.cycle();
                } else if (setting instanceof IntSetting i) {
                    int barX = x + 10;
                    int barW = PANEL_W - 70;
                    float pct = (float) ((mouseX - barX) / barW);
                    i.set(Math.round(i.getMin() + pct * (i.getMax() - i.getMin())));
                }
                SpaceClient.getConfigManager().save();
                return true;
            }
            y += ROW_H;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        // Scrolling over a colour swatch nudges its channels; elsewhere it scrolls the list
        int x = panelLeft();
        int y = contentTop() - scroll;

        for (Setting setting : module.getSettings()) {
            if (setting instanceof ColorSetting c) {
                int sw = x + PANEL_W - 46;
                if (mouseX >= sw && mouseX <= sw + 36 && mouseY >= y + 6 && mouseY <= y + 22) {
                    int step = (int) (vertical * 8);
                    c.setComponents(
                            c.getAlpha(),
                            Math.max(0, Math.min(255, c.getRed() + step)),
                            Math.max(0, Math.min(255, c.getGreen() + step)),
                            Math.max(0, Math.min(255, c.getBlue() + step)));
                    SpaceClient.getConfigManager().save();
                    return true;
                }
            }
            y += ROW_H;
        }

        int content = module.getSettings().size() * ROW_H;
        int maxScroll = Math.max(0, content - (height - contentTop() - 40));
        scroll = (int) Math.max(0, Math.min(maxScroll, scroll - vertical * 20));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 259) { // backspace
            assert client != null;
            client.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
