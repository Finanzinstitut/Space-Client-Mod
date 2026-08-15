package gg.spaceclient.ui;

import gg.spaceclient.util.Diagnostics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Shows what the mod could and could not find on this version. */
public class DiagnosticsScreen extends Screen {
    private static final int PANEL_W = 420;

    private final Screen parent;
    private List<Diagnostics.Check> checks = List.of();

    public DiagnosticsScreen(Screen parent) {
        super(Component.literal("Diagnostics"));
        this.parent = parent;
    }

    private int panelLeft() { return (this.width - PANEL_W) / 2; }

    @Override
    protected void init() {
        checks = Diagnostics.run();

        this.addRenderableWidget(new FlatButton(
                panelLeft(), this.height - 46, PANEL_W, 24,
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
        graphics.text(this.font, "DIAGNOSTICS", left + 34, 38, Theme.CYAN, false);
        graphics.text(this.font, "What this Minecraft version does and does not expose",
                left + 34, 50, Theme.TEXT_DIM, false);
        graphics.fill(left, 74, left + PANEL_W, 75, Theme.BORDER);

        int y = 88;
        for (Diagnostics.Check check : checks) {
            String mark = check.ok() ? "OK" : "--";
            int color = check.ok() ? 0xFF4ADE80 : 0xFFFF6B81;

            graphics.text(this.font, mark, left, y, color, false);
            graphics.text(this.font, check.name(), left + 24, y, Theme.TEXT, false);

            // Details can get long; wrap rather than run off the panel
            String detail = check.detail();
            int detailX = left + 190;
            int room = PANEL_W - 190;

            while (!detail.isEmpty()) {
                String line = detail;
                while (this.font.width(line) > room && line.length() > 1) {
                    line = line.substring(0, line.length() - 1);
                }
                graphics.text(this.font, line, detailX, y, Theme.TEXT_DIM, false);
                detail = detail.substring(line.length());
                y += this.font.lineHeight + 2;
            }
            y += 2;
        }

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
