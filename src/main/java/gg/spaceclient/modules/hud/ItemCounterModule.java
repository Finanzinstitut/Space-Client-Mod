package gg.spaceclient.modules.hud;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.ColorSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Counts the PvP consumables you actually care about.
 *
 * The addition: a burn-rate readout. It tracks how fast you are consuming each
 * item and estimates how long your current stock will last at that rate, so you
 * know mid-fight whether you can keep trading or need to disengage.
 */
public class ItemCounterModule extends HudModule {
    private final BooleanSetting showBurnRate = new BooleanSetting(
            "burn_rate", "Show burn rate", "Estimate how long your stock will last", true);

    private final BooleanSetting hideEmpty = new BooleanSetting(
            "hide_empty", "Hide empty entries", "Skip items you are not carrying", true);

    private final ColorSetting textColor = new ColorSetting(
            "text_color", "Text colour", "Colour of the counters", 0xFFFFFFFF);

    private final Map<String, Integer> lastCounts = new LinkedHashMap<>();
    private final Map<String, Double> consumptionPerMinute = new LinkedHashMap<>();
    private long lastSample = 0;

    public ItemCounterModule() {
        super("itemcounter", "Item Counter", "Counts key items in your inventory", 0.85f, 0.40f);
        addSettings(showBurnRate, hideEmpty, textColor);
    }

    private Map<String, Integer> currentCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("Gapples", 0);
        counts.put("Pearls", 0);
        counts.put("Blocks", 0);
        counts.put("Arrows", 0);

        if (mc.player == null) return counts;

        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            if (stack.isOf(Items.GOLDEN_APPLE) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)) {
                counts.merge("Gapples", stack.getCount(), Integer::sum);
            } else if (stack.isOf(Items.ENDER_PEARL)) {
                counts.merge("Pearls", stack.getCount(), Integer::sum);
            } else if (stack.isOf(Items.ARROW) || stack.isOf(Items.SPECTRAL_ARROW)) {
                counts.merge("Arrows", stack.getCount(), Integer::sum);
            } else if (stack.getItem().toString().contains("cobblestone")
                    || stack.getItem().toString().contains("obsidian")) {
                counts.merge("Blocks", stack.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    @Override
    public void onTick() {
        long now = System.currentTimeMillis();
        if (now - lastSample < 5000) return;

        Map<String, Integer> counts = currentCounts();
        if (!lastCounts.isEmpty()) {
            double minutes = (now - lastSample) / 60000.0;
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                int before = lastCounts.getOrDefault(e.getKey(), e.getValue());
                int used = before - e.getValue();
                double rate = used > 0 ? used / minutes : 0;
                // Smooth so one big trade does not dominate the estimate
                consumptionPerMinute.merge(e.getKey(), rate, (old, fresh) -> old * 0.7 + fresh * 0.3);
            }
        }

        lastCounts.clear();
        lastCounts.putAll(counts);
        lastSample = now;
    }

    private String lineFor(String name, int count) {
        String base = name + ": " + count;
        if (showBurnRate.get()) {
            double rate = consumptionPerMinute.getOrDefault(name, 0.0);
            if (rate > 0.05 && count > 0) {
                base += String.format("  ~%.0fm left", count / rate);
            }
        }
        return base;
    }

    @Override
    public int getWidth() {
        int max = 70;
        for (Map.Entry<String, Integer> e : currentCounts().entrySet()) {
            if (hideEmpty.get() && e.getValue() == 0) continue;
            max = Math.max(max, mc.textRenderer.getWidth(lineFor(e.getKey(), e.getValue())));
        }
        return max;
    }

    @Override
    public int getHeight() {
        int rows = 0;
        for (Integer v : currentCounts().values()) {
            if (hideEmpty.get() && v == 0) continue;
            rows++;
        }
        return Math.max(mc.textRenderer.fontHeight, rows * (mc.textRenderer.fontHeight + 2));
    }

    @Override
    public void render(DrawContext context, int x, int y) {
        int offset = 0;
        for (Map.Entry<String, Integer> e : currentCounts().entrySet()) {
            if (hideEmpty.get() && e.getValue() == 0) continue;
            context.drawText(mc.textRenderer, lineFor(e.getKey(), e.getValue()),
                    x, y + offset, textColor.get(), true);
            offset += mc.textRenderer.fontHeight + 2;
        }
    }
}
