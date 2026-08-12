package gg.spaceclient.ui;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.config.ClientSettings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Background style and accent colour for the whole interface. */
public class AppearanceScreen extends Screen {
    private static final int ROW_H = 26;
    private static final int GAP = 8;
    private static final int PANEL_W = 320;

    private final Screen parent;

    public AppearanceScreen(Screen parent) {
        super(Component.literal("Appearance"));
        this.parent = parent;
    }

    private int panelLeft() { return (this.width - PANEL_W) / 2; }

    @Override
    protected void init() {
        ClientSettings settings = SpaceClient.getSettings();
        int left = panelLeft();
        int y = 90;

        this.addRenderableWidget(new FlatButton(
                left, y, PANEL_W, ROW_H,
                () -> "Background: " + settings.backgroundStyle(),
                () -> false,
                () -> {
                    settings.cycleBackground();
                    SpaceClient.getConfigManager().save();
                }
        ));
        y += ROW_H + GAP * 2;

        // The accent is picked on a wheel; a hex field would mean typing.
        this.addRenderableWidget(new ColorWheel(
                left, y, 96, settings.accentSetting(),
                () -> SpaceClient.getConfigManager().save()
        ));
        y += 96 + GAP * 2;


        this.addRenderableWidget(new FlatButton(
                left, y, PANEL_W, ROW_H,
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
        graphics.text(this.font, "APPEARANCE", left + 34, 38, Theme.CYAN, false);
        graphics.text(this.font, "Accent colour and menu background",
                left + 34, 50, Theme.TEXT_DIM, false);
        graphics.fill(left, 74, left + PANEL_W, 75, Theme.BORDER);

        super.extractRenderState(graphics, mouseX, mouseY, delta);

        // Live preview swatch of the current accent
        int swatchY = this.height - 60;
        graphics.text(this.font, "Preview", left, swatchY - 12, Theme.TEXT_DIM, false);
        graphics.fill(left, swatchY, left + PANEL_W, swatchY + 18, Theme.accentDim());
        graphics.fill(left, swatchY, left + 3, swatchY + 18, Theme.accent());
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
