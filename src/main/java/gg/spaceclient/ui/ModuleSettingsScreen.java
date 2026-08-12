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

    /** Row positions of colour settings, so their names can be drawn above. */
    private final java.util.List<int[]> colourLabels = new java.util.ArrayList<>();
    private int colourIndex = 0;

    public ModuleSettingsScreen(Screen parent, Module module) {
        super(Component.literal(module.getName()));
        this.parent = parent;
        this.module = module;
    }

    private int panelLeft() { return (this.width - PANEL_W) / 2; }

    @Override
    protected void init() {
        colourLabels.clear();
        colourIndex = 0;

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
                // A wheel is quicker to aim than four sliders, and shows what
                // the colour will actually look like while choosing it.
                this.addRenderableWidget(new ColorWheel(
                        left, y + 14, 84, c,
                        () -> SpaceClient.getConfigManager().save()
                ));
                colourLabels.add(new int[]{y, colourIndex++});
                y += 84 + 22;

                // Alpha still needs a slider: it has no place on a hue wheel
                this.addRenderableWidget(new SliderRow(left, y, PANEL_W, ROW_H,
                        setting.getName() + " opacity", c.getAlpha(), 255, value -> {
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

        // Names for the colour wheels, drawn over the widgets
        int index = 0;
        for (Setting setting : module.getSettings()) {
            if (!(setting instanceof ColorSetting)) continue;
            if (index >= colourLabels.size()) break;
            int rowY = colourLabels.get(index)[0];
            graphics.text(this.font, setting.getName(), left, rowY, Theme.TEXT, false);
            index++;
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
