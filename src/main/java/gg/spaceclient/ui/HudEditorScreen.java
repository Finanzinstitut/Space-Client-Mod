package gg.spaceclient.ui;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.module.HudModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Drag enabled HUD elements to reposition them. */
public class HudEditorScreen extends Screen {
    private final Screen parent;
    private HudModule dragging;
    private int dragOffsetX;
    private int dragOffsetY;

    public HudEditorScreen(Screen parent) {
        super(Text.literal("HUD Editor"));
        this.parent = parent;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.fill(0, 0, width, height, 0x8002010A);

        for (HudModule module : SpaceClient.getModuleManager().getHudModules()) {
            if (!module.isEnabled()) continue;

            int x = module.getX(width);
            int y = module.getY(height);
            int w = Math.max(20, module.getWidth());
            int h = Math.max(10, module.getHeight());

            module.render(context, x, y);

            boolean hovered = mouseX >= x - 2 && mouseX <= x + w + 2 && mouseY >= y - 2 && mouseY <= y + h + 2;
            int color = (dragging == module || hovered) ? Theme.ACCENT_LIGHT : Theme.BORDER;
            context.fill(x - 2, y - 2, x + w + 2, y - 1, color);
            context.fill(x - 2, y + h + 1, x + w + 2, y + h + 2, color);
            context.fill(x - 2, y - 2, x - 1, y + h + 2, color);
            context.fill(x + w + 1, y - 2, x + w + 2, y + h + 2, color);
        }

        context.drawText(textRenderer, "HUD EDITOR", 30, 26, Theme.ACCENT_LIGHT, true);
        context.drawText(textRenderer, "Drag elements to move    Scroll over one to scale    Backspace to go back",
                30, 40, Theme.TEXT_DIM, false);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (HudModule module : SpaceClient.getModuleManager().getHudModules()) {
            if (!module.isEnabled()) continue;
            int x = module.getX(width);
            int y = module.getY(height);
            int w = Math.max(20, module.getWidth());
            int h = Math.max(10, module.getHeight());

            if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
                dragging = module;
                dragOffsetX = (int) (mouseX - x);
                dragOffsetY = (int) (mouseY - y);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging != null) {
            float px = (float) (mouseX - dragOffsetX) / width;
            float py = (float) (mouseY - dragOffsetY) / height;
            dragging.setPosition(px, py);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging != null) {
            dragging = null;
            SpaceClient.getConfigManager().save();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        for (HudModule module : SpaceClient.getModuleManager().getHudModules()) {
            if (!module.isEnabled()) continue;
            int x = module.getX(width);
            int y = module.getY(height);
            int w = Math.max(20, module.getWidth());
            int h = Math.max(10, module.getHeight());

            if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
                module.setScale(module.getScale() + (float) vertical * 0.1f);
                SpaceClient.getConfigManager().save();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 259) {
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
