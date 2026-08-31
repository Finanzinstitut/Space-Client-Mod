package gg.spaceclient.ui;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.module.HudModule;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Moves and resizes HUD elements with the mouse.
 *
 * Click an element to pick it up, move, click again to drop it. Click the
 * corner grip instead and the same gesture resizes. Holding the button and
 * dragging would need the mouse event API, whose signatures changed in this
 * version; picking up and putting down needs only a click and the cursor
 * position, both of which are already available.
 *
 * The scroll wheel resizes whatever is under the pointer, which is the faster
 * gesture once you know it is there. Both exist because the grip is the one
 * people find without being told.
 */
public class HudEditorScreen extends Screen {

    private static final int GRIP = 8;

    private final Screen parent;

    private HudModule grabbed;
    private HudModule resizing;

    /** Where inside the element it was grabbed, so it does not jump. */
    private int grabOffsetX;
    private int grabOffsetY;

    private final List<GrabHandle> handles = new ArrayList<>();

    /** Per element outline fade, so the highlight arrives rather than snaps. */
    private final Map<HudModule, Float> glow = new HashMap<>();

    /** Where each element is drawn, which trails where it actually is. */
    private final Map<HudModule, float[]> shown = new HashMap<>();

    public HudEditorScreen(Screen parent) {
        super(Component.literal("HUD Editor"));
        this.parent = parent;
    }

    /** An invisible button covering one HUD element, or its corner grip. */
    private class GrabHandle extends net.minecraft.client.gui.components.Button {
        private final HudModule module;
        private final boolean grip;

        private int lastHoverX;
        private int lastHoverY;

        GrabHandle(HudModule module, boolean grip, int width, int height, Runnable onClick) {
            super(0, 0, width, height, Component.empty(),
                    btn -> onClick.run(), DEFAULT_NARRATION);
            this.module = module;
            this.grip = grip;
        }

        /** Nothing is drawn: the element underneath is the visual. */
        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        }
    }

    private void grab(HudModule module, GrabHandle handle) {
        grabbed = module;
        grabOffsetX = handle.lastHoverX - module.getX(this.width);
        grabOffsetY = handle.lastHoverY - module.getY(this.height);
    }

    private void drop() {
        grabbed = null;
        resizing = null;
        SpaceClient.getConfigManager().save();
    }

    @Override
    protected void init() {
        handles.clear();
        for (HudModule module : SpaceClient.getModuleManager().getHudModules()) {
            if (!module.isEnabled()) continue;

            GrabHandle[] body = new GrabHandle[1];
            body[0] = new GrabHandle(module, false,
                    Math.max(20, module.getScaledWidth()),
                    Math.max(10, module.getScaledHeight()),
                    () -> {
                        if (grabbed == module || resizing == module) {
                            drop();
                        } else if (grabbed == null && resizing == null) {
                            grab(module, body[0]);
                        }
                    });

            GrabHandle grip = new GrabHandle(module, true, GRIP, GRIP,
                    () -> {
                        if (resizing == module || grabbed == module) {
                            drop();
                        } else if (grabbed == null && resizing == null) {
                            resizing = module;
                        }
                    });

            // The grip is added after the body so it wins the overlap: the
            // corner belongs to resizing, not to moving
            handles.add(body[0]);
            handles.add(grip);
            this.addRenderableWidget(body[0]);
            this.addRenderableWidget(grip);
        }
    }

    // ---------------- scrolling resizes ----------------

    private boolean scrollResize(double mouseX, double mouseY, double amount) {
        HudModule under = resizing;
        if (under == null) under = elementAt((int) mouseX, (int) mouseY);
        if (under == null) return false;

        under.setScale(under.getScale() + (float) amount * 0.1f);
        SpaceClient.getConfigManager().save();
        return true;
    }

    private HudModule elementAt(int mouseX, int mouseY) {
        for (GrabHandle handle : handles) {
            if (handle.grip) continue;
            HudModule module = handle.module;
            int x = module.getX(this.width);
            int y = module.getY(this.height);
            if (mouseX >= x && mouseX <= x + module.getScaledWidth()
                    && mouseY >= y && mouseY <= y + module.getScaledHeight()) {
                return module;
            }
        }
        return null;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return scrollResize(mouseX, mouseY, scrollY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return scrollResize(mouseX, mouseY, amount);
    }

    // ---------------- paint ----------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0x9002010A);

        if (grabbed != null) {
            float px = (mouseX - grabOffsetX) / (float) this.width;
            float py = (mouseY - grabOffsetY) / (float) this.height;
            grabbed.setPosition(px, py);
        }

        if (resizing != null) {
            // Width follows the cursor directly: the pointer sits where the
            // element's right edge will be, which is easier to aim than a
            // multiplier that depends on how far you started from the corner
            int originX = resizing.getX(this.width);
            int contentWidth = Math.max(1, resizing.getWidth());
            resizing.setScale((mouseX - originX) / (float) contentWidth);
        }

        for (GrabHandle handle : handles) {
            if (handle.grip) continue;

            HudModule module = handle.module;
            int x = module.getX(this.width);
            int y = module.getY(this.height);
            int w = Math.max(20, module.getScaledWidth());
            int h = Math.max(10, module.getScaledHeight());

            boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
            boolean active = grabbed == module || resizing == module;

            // The drawn position trails the real one. Only while carrying it:
            // an element being dropped into place should settle, but one being
            // read should be exactly where it says it is.
            float[] at = shown.computeIfAbsent(module, key -> new float[]{ x, y });
            if (active) {
                at[0] = Ease.approach(at[0], x, 0.5f, delta);
                at[1] = Ease.approach(at[1], y, 0.5f, delta);
            } else {
                at[0] = x;
                at[1] = y;
            }
            int drawX = Math.round(at[0]);
            int drawY = Math.round(at[1]);

            handle.setX(x);
            handle.setY(y);
            handle.lastHoverX = mouseX;
            handle.lastHoverY = mouseY;

            module.draw(graphics, drawX, drawY);

            float target = active ? 1f : (hovered ? 0.6f : 0f);
            float lit = Ease.approach(glow.getOrDefault(module, 0f), target, 0.35f, delta);
            glow.put(module, lit);

            if (lit > 0.01f) {
                int color = Ease.color(Theme.BORDER,
                        active ? Theme.CYAN : Theme.accent(), lit);
                color = (Math.round(255 * Math.min(1f, 0.35f + lit)) << 24) | (color & 0xFFFFFF);

                graphics.fill(drawX - 2, drawY - 2, drawX + w + 2, drawY - 1, color);
                graphics.fill(drawX - 2, drawY + h + 1, drawX + w + 2, drawY + h + 2, color);
                graphics.fill(drawX - 2, drawY - 2, drawX - 1, drawY + h + 2, color);
                graphics.fill(drawX + w + 1, drawY - 2, drawX + w + 2, drawY + h + 2, color);

                // The corner grip, drawn solid so it reads as a thing to grab
                graphics.fill(drawX + w - GRIP + 2, drawY + h - GRIP + 2,
                        drawX + w + 2, drawY + h + 2, color);
            }

            if (hovered || active) {
                String label = module.getName();
                if (active && resizing == module) {
                    label = label + "  " + Math.round(module.getScale() * 100) + "%";
                }
                graphics.text(this.font, label, drawX, drawY - 12,
                        active ? Theme.CYAN : Theme.TEXT, false);
            }
        }

        // Grips are positioned after the bodies so they sit on the final corner
        for (GrabHandle handle : handles) {
            if (!handle.grip) continue;
            HudModule module = handle.module;
            handle.setX(module.getX(this.width) + Math.max(20, module.getScaledWidth()) - GRIP + 2);
            handle.setY(module.getY(this.height) + Math.max(10, module.getScaledHeight()) - GRIP + 2);
            handle.lastHoverX = mouseX;
            handle.lastHoverY = mouseY;
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);

        JupiterIcon.draw(graphics, 24, 24, 20);
        graphics.text(this.font, "HUD EDITOR", 52, 26, Theme.CYAN, false);

        String hint;
        if (resizing != null) {
            hint = "Move the mouse to resize, then click to keep it";
        } else if (grabbed != null) {
            hint = "Move the mouse, then click again to place it";
        } else {
            hint = "Click to pick up, corner to resize, scroll to zoom";
        }
        graphics.text(this.font, hint, 52, 38, Theme.TEXT_DIM, false);
        graphics.text(this.font, "Esc to go back", 24, this.height - 24, Theme.TEXT_DIM, false);
    }

    @Override
    public void onClose() {
        if (grabbed != null || resizing != null) drop();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
