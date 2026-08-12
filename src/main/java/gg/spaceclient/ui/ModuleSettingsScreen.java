package gg.spaceclient.ui;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.module.Module;
import gg.spaceclient.setting.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Per-module options: toggles, sliders, modes and colours. */
public class ModuleSettingsScreen extends Screen {
    private static final int ROW_H = 26;
    private static final int GAP = 6;
    private static final int PANEL_W = 340;

    private final Screen parent;
    private final Module module;

    public ModuleSettingsScreen(Screen parent, Module module) {
        super(Component.literal(module.getName()));
        this.parent = parent;
        this.module = module;
    }

    private int panelLeft() { return (this.width - PANEL_W) / 2; }

    @Override
    protected void init() {
        int left = panelLeft();
        int y = 92;

        for (Setting setting : module.getSettings()) {
            if (setting instanceof BooleanSetting b) {
                this.addRenderableWidget(new FlatButton(
                        left, y, PANEL_W, ROW_H,
                        setting::getName,
                        b::get,
                        () -> {
                            b.toggle();
                            SpaceClient.getConfigManager().save();
                        }
                ));
                y += ROW_H + GAP;

            } else if (setting instanceof ModeSetting m) {
                this.addRenderableWidget(new FlatButton(
                        left, y, PANEL_W, ROW_H,
                        () -> setting.getName() + ": " + m.get(),
                        () -> false,
                        () -> {
                            m.cycle();
                            SpaceClient.getConfigManager().save();
                        }
                ));
                y += ROW_H + GAP;

            } else if (setting instanceof IntSetting i) {
                this.addRenderableWidget(new SliderRow(
                        left, y, PANEL_W, ROW_H,
                        setting.getName(), i.get(), i.getMax(),
                        value -> {
                            i.set(value);
                            SpaceClient.getConfigManager().save();
                        }
                ));
                y += ROW_H + GAP;

            } else if (setting instanceof ColorSetting c) {
                // One slider per channel, so any colour is reachable
                this.addRenderableWidget(new SliderRow(left, y, PANEL_W, ROW_H,
                        setting.getName() + " R", c.getRed(), 255, value -> {
                    c.setComponents(c.getAlpha(), value, c.getGreen(), c.getBlue());
                    SpaceClient.getConfigManager().save();
                }));
                y += ROW_H + GAP;

                this.addRenderableWidget(new SliderRow(left, y, PANEL_W, ROW_H,
                        setting.getName() + " G", c.getGreen(), 255, value -> {
                    c.setComponents(c.getAlpha(), c.getRed(), value, c.getBlue());
                    SpaceClient.getConfigManager().save();
                }));
                y += ROW_H + GAP;

                this.addRenderableWidget(new SliderRow(left, y, PANEL_W, ROW_H,
                        setting.getName() + " B", c.getBlue(), 255, value -> {
                    c.setComponents(c.getAlpha(), c.getRed(), c.getGreen(), value);
                    SpaceClient.getConfigManager().save();
                }));
                y += ROW_H + GAP;

                this.addRenderableWidget(new SliderRow(left, y, PANEL_W, ROW_H,
                        setting.getName() + " Alpha", c.getAlpha(), 255, value -> {
                    c.setComponents(value, c.getRed(), c.getGreen(), c.getBlue());
                    SpaceClient.getConfigManager().save();
                }));
                y += ROW_H + GAP * 2;
            }
        }

        this.addRenderableWidget(new FlatButton(
                left, y + 8, PANEL_W, ROW_H,
                () -> "Back",
                () -> false,
                this::onClose
        ));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Backdrop.draw(graphics, this.width, this.height);

        int left = panelLeft();
        graphics.fill(left - 18, 20, left + PANEL_W + 18, this.height - 20, Theme.PANEL);
        graphics.fill(left - 18, 20, left + PANEL_W + 18, 21, Theme.BORDER);

        JupiterIcon.draw(graphics, left, 34, 24);
        graphics.text(this.font, module.getName().toUpperCase(), left + 34, 38, Theme.CYAN, false);
        graphics.text(this.font, module.getDescription(), left + 34, 50, Theme.TEXT_DIM, false);
        graphics.fill(left, 74, left + PANEL_W, 75, Theme.BORDER);

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
