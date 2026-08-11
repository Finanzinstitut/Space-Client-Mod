package gg.spaceclient.modules.hud;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.IntSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

/**
 * Warns before a tool or piece of armour breaks.
 *
 * The addition: it estimates *hits remaining* rather than showing a percentage,
 * because "12 hits left" tells you whether to keep fighting and "8%" does not.
 */
public class DurabilityAlertModule extends HudModule {
    private final IntSetting threshold = new IntSetting(
            "threshold", "Warn below (%)", "Start warning at this durability", 15, 5, 50);

    private final BooleanSetting showHits = new BooleanSetting(
            "show_hits", "Show hits remaining", "Estimate remaining uses instead of a percentage", true);

    private final BooleanSetting flash = new BooleanSetting(
            "flash", "Flash when critical", "Pulse below 5%", true);

    public DurabilityAlertModule() {
        super("durability", "Durability Alert", "Warns before equipment breaks", 0.45f, 0.72f);
        addSettings(threshold, showHits, flash);
    }

    private String describe(ItemStack stack, String slot) {
        if (stack.isEmpty() || !stack.isDamageable()) return null;

        int remaining = stack.getMaxDamage() - stack.getDamage();
        float pct = (remaining / (float) stack.getMaxDamage()) * 100f;
        if (pct > threshold.get()) return null;

        if (showHits.get()) {
            return String.format("%s: %d hits left", slot, remaining);
        }
        return String.format("%s: %.0f%%", slot, pct);
    }

    private String[] warnings() {
        if (mc.player == null) return new String[0];

        String[] slots = {"Boots", "Legs", "Chest", "Helmet"};
        java.util.List<String> out = new java.util.ArrayList<>();

        for (int i = 0; i < 4; i++) {
            String line = describe(mc.player.getInventory().getArmorStack(i), slots[i]);
            if (line != null) out.add(line);
        }
        String hand = describe(mc.player.getMainHandStack(), "Hand");
        if (hand != null) out.add(hand);

        return out.toArray(new String[0]);
    }

    @Override
    public int getWidth() {
        int max = 0;
        for (String w : warnings()) max = Math.max(max, mc.textRenderer.getWidth(w));
        return Math.max(40, max);
    }

    @Override
    public int getHeight() {
        return Math.max(mc.textRenderer.fontHeight, warnings().length * (mc.textRenderer.fontHeight + 2));
    }

    @Override
    public void render(DrawContext context, int x, int y) {
        int offset = 0;
        boolean pulse = flash.get() && (System.currentTimeMillis() / 350) % 2 == 0;

        for (String warning : warnings()) {
            int color = pulse ? 0xFFFF6B81 : 0xFFFFD9A0;
            context.drawText(mc.textRenderer, warning, x, y + offset, color, true);
            offset += mc.textRenderer.fontHeight + 2;
        }
    }
}
