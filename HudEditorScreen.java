package gg.spaceclient.ui;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.module.HudModule;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Moves HUD elements with the mouse.
 *
 * Click an element to pick it up, move the mouse, click again to drop it.
 * Holding the button and dragging would need the mouse event API, whose
 * signatures changed in this version; picking up and putting down needs only a
 * click and the cursor position, both of which are already available.
 */
public class HudEditorScreen extends Screen {
    private final Screen parent;

    private HudModule grabbed;
    /** Where inside the element it was grabbed, so it does not jump. */
    private int grabOffsetX;
    private int grabOffsetY;

    private final List<GrabHandle> handles = new ArrayList<>();

    public HudEditorScreen(Screen parent) {
        super(Component.literal("HUD Editor"));
        this.parent = parent;
    }

    /** An invisible button covering one HUD element. */
    private class GrabHandle extends net.minecraft.client.gui.components.Button {
        private final HudModule module;

        /** Cursor position from the last frame, used when picking up. */
        private int lastHoverX;
        private int lastHoverY;

        GrabHandle(HudModule module, int width, int height, Runnable onClick) {
            super(0, 0, width, height, Component.empty(),
                    btn -> onClick.run(), DEFAULT_NARRATION);
            this.module = module;
        }

        /** Nothing is drawn: the element underneath is the visual. */
        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        }
    }

    private void grab(HudModule module, GrabHandle handle) {
        grabbed = module;
        // Offset from the element's corner to the cursor, so it does not jump
        grabOffsetX = handle.lastHoverX - module.getX(this.width);
        grabOffsetY = handle.lastHoverY - module.getY(this.height);
    }

    private void drop() {
        grabbed = null;
        SpaceClient.getConfigManager().save();
    }

    @Override
    protected void init() {
        handles.clear();
        for (HudModule module : SpaceClient.getModuleManager().getHudModules()) {
            if (!module.isEnabled()) continue;
            // The click action needs the handle itself, which does not exist
            // yet at that point - a one element array bridges the gap.
            GrabHandle[] holder = new GrabHandle[1];
            holder[0] = new GrabHandle(module,
                    Math.max(20, module.getWidth()),
                    Math.max(10, module.getHeight()),
                    () -> {
                        if (grabbed == module) {
                            drop();
                        } else if (grabbed == null) {
                            grab(module, holder[0]);
                        }
                    });
            handles.add(holder[0]);
            this.addRenderableWidget(holder[0]);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0x9002010A);

        // A grabbed element follows the cursor
        if (grabbed != null) {
            float px = (mouseX - grabOffsetX) / (float) this.width;
            float py = (mouseY - grabOffsetY) / (float) this.height;
            grabbed.setPosition(px, py);
        }

        for (GrabHandle handle : handles) {
            HudModule module = handle.module;
            int x = module.getX(this.width);
            int y = module.getY(this.height);
            int w = Math.max(20, module.getWidth());
            int h = Math.max(10, module.getHeight());

            // Keep the invisible button on top of its element
            handle.setX(x);
            handle.setY(y);
            handle.lastHoverX = mouseX;
            handle.lastHoverY = mouseY;

            module.draw(graphics, x, y);

            boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
            boolean active = grabbed == module;
            int color = active ? Theme.CYAN : (hovered ? Theme.accent() : Theme.BORDER);

            graphics.fill(x - 2, y - 2, x + w + 2, y - 1, color);
            graphics.fill(x - 2, y + h + 1, x + w + 2, y + h + 2, color);
            graphics.fill(x - 2, y - 2, x - 1, y + h + 2, color);
            graphics.fill(x + w + 1, y - 2, x + w + 2, y + h + 2, color);

            if (hovered || active) {
                graphics.text(this.font, module.getName(), x, y - 12,
                        active ? Theme.CYAN : Theme.TEXT, false);
            }
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);

        JupiterIcon.draw(graphics, 24, 24, 20);
        graphics.text(this.font, "HUD EDITOR", 52, 26, Theme.CYAN, false);
        graphics.text(this.font,
                grabbed == null
                        ? "Click an element to pick it up"
                        : "Move the mouse, then click again to place it",
                52, 38, Theme.TEXT_DIM, false);
        graphics.text(this.font, "Esc to go back", 24, this.height - 24, Theme.TEXT_DIM, false);
    }

    @Override
    public void onClose() {
        if (grabbed != null) drop();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
