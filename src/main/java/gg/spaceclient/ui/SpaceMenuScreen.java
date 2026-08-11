package gg.spaceclient.ui;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.module.Module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The main menu, opened with Right Shift.
 *
 * Built entirely from Button widgets rather than custom hit testing, so it does
 * not depend on the mouse event API that changed in this version.
 */
public class SpaceMenuScreen extends Screen {
    private static final int BTN_W = 200;
    private static final int BTN_H = 20;
    private static final int GAP = 6;
    private static final int COLUMNS = 2;

    public SpaceMenuScreen() {
        super(Component.literal("Space Client"));
    }

    @Override
    protected void init() {
        List<Module> modules = SpaceClient.getModuleManager().getAll();

        int gridWidth = COLUMNS * BTN_W + (COLUMNS - 1) * GAP;
        int left = (this.width - gridWidth) / 2;
        int top = 70;

        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = left + col * (BTN_W + GAP);
            int y = top + row * (BTN_H + GAP);

            Button button = Button.builder(labelFor(module), btn -> {
                module.toggle();
                SpaceClient.getConfigManager().save();
                // Rebuild so every label reflects the new state
                this.rebuildWidgets();
            }).bounds(x, y, BTN_W, BTN_H).build();

            this.addRenderableWidget(button);
        }

        int closeY = top + ((modules.size() + COLUMNS - 1) / COLUMNS) * (BTN_H + GAP) + 12;
        this.addRenderableWidget(Button.builder(Component.literal("Close"), btn -> this.onClose())
                .bounds((this.width - 120) / 2, closeY, 120, BTN_H)
                .build());
    }

    private Component labelFor(Module module) {
        String state = module.isEnabled() ? "ON" : "OFF";
        return Component.literal(module.getName() + "  -  " + state);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, Theme.BACKDROP);
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        graphics.text(this.font, "SPACE CLIENT", 30, 26, Theme.ACCENT_LIGHT, true);
        graphics.text(this.font, "Right Shift to close", 30, 40, Theme.TEXT_DIM, false);

        // Description of whichever module the mouse is over
        List<Module> modules = SpaceClient.getModuleManager().getAll();
        int gridWidth = COLUMNS * BTN_W + (COLUMNS - 1) * GAP;
        int left = (this.width - gridWidth) / 2;
        int top = 70;

        for (int i = 0; i < modules.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = left + col * (BTN_W + GAP);
            int y = top + row * (BTN_H + GAP);

            if (mouseX >= x && mouseX <= x + BTN_W && mouseY >= y && mouseY <= y + BTN_H) {
                String desc = modules.get(i).getDescription();
                int textX = (this.width - this.font.width(desc)) / 2;
                graphics.text(this.font, desc, textX, this.height - 30, Theme.TEXT, true);
                break;
            }
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
