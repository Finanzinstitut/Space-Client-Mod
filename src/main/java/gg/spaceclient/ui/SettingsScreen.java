package gg.spaceclient.ui;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.setting.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders a list of settings, and a button for each sub-group.
 *
 * Used both for a module's top level and for each of its groups, so a module
 * with four categories of three options each reads as four buttons rather than
 * twelve rows.
 */
public class SettingsScreen extends Screen {
    private static final int ROW_H = 26;
    private static final int GAP = 6;
    private static final int PANEL_W = 340;

    private final Screen parent;
    private final String heading;
    private final String subheading;
    private final List<Setting> settings;
    private final List<SettingGroup> groups;

    /** Y positions of colour wheels, so their names can be drawn above them. */
    private final List<int[]> colourRows = new ArrayList<>();

    public SettingsScreen(Screen parent, String heading, String subheading,
                          List<Setting> settings, List<SettingGroup> groups) {
        super(Component.literal(heading));
        this.parent = parent;
        this.heading = heading;
        this.subheading = subheading;
        this.settings = settings;
        this.groups = groups;
    }

    private int panelLeft() { return (this.width - PANEL_W) / 2; }

    @Override
    protected void init() {
        colourRows.clear();
        int left = panelLeft();
        int y = 92;

        // Sub-groups first, so the categories are the first thing you see
        for (SettingGroup group : groups) {
            this.addRenderableWidget(new FlatButton(
                    left, y, PANEL_W, ROW_H,
                    () -> group.name() + "  >",
                    () -> false,
                    () -> Minecraft.getInstance().gui.setScreen(new SettingsScreen(
                            this, group.name(), group.description(),
                            group.settings(), List.of()))
            ).asAction());
            y += ROW_H + GAP;
        }
        if (!groups.isEmpty()) y += GAP;

        for (Setting setting : settings) {
            if (setting instanceof BooleanSetting b) {
                this.addRenderableWidget(new FlatButton(
                        left, y, PANEL_W, ROW_H,
                        setting::getName, b::get,
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
                colourRows.add(new int[]{y});
                this.addRenderableWidget(new ColorWheel(
                        left, y + 14, 84, c,
                        () -> SpaceClient.getConfigManager().save()
                ));
                y += 84 + 22;

                // Opacity keeps a slider; it has no place on a hue wheel
                this.addRenderableWidget(new SliderRow(left, y, PANEL_W, ROW_H,
                        setting.getName() + " opacity", c.getAlpha(), 255, value -> {
                    c.setComponents(value, c.getRed(), c.getGreen(), c.getBlue());
                    SpaceClient.getConfigManager().save();
                }));
                y += ROW_H + GAP;
            }
        }

        this.addRenderableWidget(new FlatButton(
                left, y + 8, PANEL_W, ROW_H,
                () -> "Back",
                () -> false,
                this::onClose
        ).asAction());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Backdrop.draw(graphics, this.width, this.height);

        int left = panelLeft();
        graphics.fill(left - 18, 20, left + PANEL_W + 18, this.height - 20, Theme.PANEL);
        graphics.fill(left - 18, 20, left + PANEL_W + 18, 21, Theme.BORDER);

        JupiterIcon.draw(graphics, left, 34, 24);
        graphics.text(this.font, heading.toUpperCase(), left + 34, 38, Theme.CYAN, false);
        graphics.text(this.font, subheading, left + 34, 50, Theme.TEXT_DIM, false);
        graphics.fill(left, 74, left + PANEL_W, 75, Theme.BORDER);

        super.extractRenderState(graphics, mouseX, mouseY, delta);

        // Names above the colour wheels, which draw no label of their own
        int index = 0;
        for (Setting setting : settings) {
            if (!(setting instanceof ColorSetting)) continue;
            if (index >= colourRows.size()) break;
            graphics.text(this.font, setting.getName(),
                    left, colourRows.get(index)[0], Theme.TEXT, false);
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
