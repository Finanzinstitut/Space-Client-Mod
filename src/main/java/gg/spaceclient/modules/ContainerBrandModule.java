package gg.spaceclient.modules;

import gg.spaceclient.module.Module;
import gg.spaceclient.setting.IntSetting;
import gg.spaceclient.setting.ModeSetting;
import gg.spaceclient.ui.JupiterIcon;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

import java.util.Arrays;

/**
 * The Space Client mark in the corner of every container: chests, shulker
 * boxes, the inventory, furnaces - anything with slots.
 *
 * Drawn after the screen, bottom right, so it never covers a slot or a
 * tooltip's anchor: containers are centred and leave that corner free.
 */
public class ContainerBrandModule extends Module {

    private final ModeSetting size = new ModeSetting(
            "size", "Size", "How large the mark is drawn",
            Arrays.asList("SMALL", "MEDIUM", "LARGE"), "MEDIUM");

    private final IntSetting opacity = new IntSetting(
            "opacity", "Opacity (percent)", "How strongly the mark shows", 85, 20, 100);

    public ContainerBrandModule() {
        super("containerbrand", "Container Logo",
                "Space Client logo in the corner of chests and inventories", true);
        addSettings(size, opacity);

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?>)) return;
            ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> {
                if (!isEnabled()) return;
                try {
                    draw(graphics, s.width, s.height);
                } catch (Throwable ignored) {
                    // A corner without a logo, never a broken chest
                }
            });
        });
    }

    private void draw(GuiGraphicsExtractor graphics, int width, int height) {
        float scale = switch (size.get()) {
            case "SMALL" -> 1.0f;
            case "LARGE" -> 2.0f;
            default -> 1.5f;
        };
        int alpha = Math.round(opacity.get() / 100f * 255);
        int white = (alpha << 24) | 0xFFFFFF;

        Component name = Component.literal("SPACE").withStyle(ChatFormatting.BOLD)
                .append(Component.literal(" CLIENT").withStyle(s -> s.withBold(false)));

        int textW = mc.font.width(name);
        int icon = 12;
        int contentW = icon + 5 + textW;
        int margin = 8;

        graphics.pose().pushMatrix();
        try {
            // Anchored by its bottom right corner, then scaled up from there
            graphics.pose().translate(width - margin, height - margin);
            graphics.pose().scale(scale, scale);
            int x = -contentW;
            int y = -icon;
            JupiterIcon.draw(graphics, x, y, icon);
            graphics.text(mc.font, name, x + icon + 5, y + (icon - 8) / 2, white, true);
        } finally {
            graphics.pose().popMatrix();
        }
    }
}
