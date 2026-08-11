package gg.spaceclient.modules.hud;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.BooleanSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

public class ArmorStatusModule extends HudModule {
    private static final int SLOT = 20;

    private final BooleanSetting showDurability = new BooleanSetting(
            "show_durability", "Show durability", "Print the remaining durability under each piece", true);

    private final BooleanSetting horizontal = new BooleanSetting(
            "horizontal", "Horizontal", "Lay the pieces out in a row instead of a column", true);

    public ArmorStatusModule() {
        super("armorstatus", "Armor Status", "Displays the durability of your equipment", 0.45f, 0.85f);
        addSettings(showDurability, horizontal);
    }

    @Override
    public int getWidth() { return horizontal.get() ? SLOT * 5 : SLOT; }

    @Override
    public int getHeight() { return horizontal.get() ? SLOT + 10 : (SLOT + 10) * 5; }

    @Override
    public void render(DrawContext context, int x, int y) {
        if (mc.player == null) return;

        int index = 0;
        // Armour slots run helmet-first for display; the inventory stores them boots-first
        for (int slot = 3; slot >= 0; slot--) {
            ItemStack stack = mc.player.getInventory().getArmorStack(slot);
            drawPiece(context, stack, x, y, index++);
        }
        drawPiece(context, mc.player.getMainHandStack(), x, y, index);
    }

    private void drawPiece(DrawContext context, ItemStack stack, int x, int y, int index) {
        if (stack.isEmpty()) return;

        int px = horizontal.get() ? x + index * SLOT : x;
        int py = horizontal.get() ? y : y + index * (SLOT + 10);

        context.drawItem(stack, px, py);

        if (showDurability.get() && stack.isDamageable()) {
            int remaining = stack.getMaxDamage() - stack.getDamage();
            String text = String.valueOf(remaining);
            int textX = px + (SLOT - mc.textRenderer.getWidth(text)) / 2;
            context.drawText(mc.textRenderer, text, textX, py + SLOT, 0xFFFFFFFF, true);
        }
    }
}
