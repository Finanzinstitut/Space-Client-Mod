package gg.spaceclient.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Says why the Cosmetica menu did not open.
 *
 * Shown instead of the real menu when the mod is missing, so a dead-feeling
 * button becomes a sentence the player can act on.
 */
public class CosmeticaMissingScreen extends Screen {

    private static final int SIDEBAR_W = 148;

    private final Screen parent;

    public CosmeticaMissingScreen(Screen parent) {
        super(Component.literal("Cosmetica"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(new FlatButton(
                SIDEBAR_W + 18, this.height - 44, 100, 24,
                () -> "Back", () -> false,
                () -> Minecraft.getInstance().gui.setScreen(parent)
        ).asAction());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Backdrop.draw(graphics, this.width, this.height);
        graphics.fill(0, 0, this.width, this.height, Theme.CONTENT);

        int left = SIDEBAR_W + 18;
        JupiterIcon.draw(graphics, 16, 24, 22);
        graphics.text(this.font, "Cosmetica", left, 30, Theme.TEXT, false);

        String[] lines = {
                "Cosmetica is not installed.",
                "",
                "Space Client uses Cosmetica for capes, wings,",
                "bandanas and horns rather than shipping its own.",
                "Cosmetics live on your Cosmetica account, so they",
                "follow you onto any server.",
                "",
                "Get the mod at cosmetica.cc, drop it in your mods",
                "folder alongside Space Client, and this entry will",
                "open its menu.",
        };

        int y = 70;
        for (String line : lines) {
            if (!line.isEmpty()) {
                graphics.text(this.font, line, left, y,
                        line.endsWith("installed.") ? Theme.TEXT : Theme.TEXT_DIM, false);
            }
            y += 14;
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
