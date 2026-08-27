package gg.spaceclient.modules;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.ColorSetting;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * What you are wearing and how much of it is left.
 *
 * Read through getItemBySlot rather than the inventory's armour list: the slot
 * lookup is the same on the server and the client and has outlived several
 * reshuffles of how inventories are laid out.
 */
public class ArmorModule extends HudModule {

    /** Helmet first, boots last - the order people picture themselves in. */
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
    };

    private static final String[] LABELS = { "Helmet", "Chest", "Legs", "Boots" };

    private final BooleanSetting showPercent = new BooleanSetting(
            "percent", "Percent", "Show wear as a percentage instead of a count", false);

    private final BooleanSetting hideEmpty = new BooleanSetting(
            "hide_empty", "Hide empty slots", "Leave out armour you are not wearing", true);

    private final BooleanSetting warnColors = new BooleanSetting(
            "warn_colors", "Warning colours", "Turn amber below 25% and red below 10%", true);

    private final ColorSetting textColor = new ColorSetting(
            "text_color", "Text colour", "Colour of the readout", 0xFFFFFFFF);

    public ArmorModule() {
        super("armor", "Armour", "Durability of the armour you are wearing",
                0.02f, 0.30f, false);
        addSettings(showPercent, hideEmpty, warnColors, textColor);
    }

    /** The pieces to draw, after the empty-slot setting has had its say. */
    private int visibleRows() {
        if (!hideEmpty.get()) return SLOTS.length;
        if (mc.player == null) return 0;

        int rows = 0;
        for (EquipmentSlot slot : SLOTS) {
            if (!mc.player.getItemBySlot(slot).isEmpty()) rows++;
        }
        return rows;
    }

    @Override
    public int getWidth() {
        int widest = 0;
        for (int i = 0; i < SLOTS.length; i++) {
            widest = Math.max(widest, mc.font.width(row(i)));
        }
        return Math.max(70, widest);
    }

    @Override
    public int getHeight() {
        int rows = Math.max(1, visibleRows());
        return rows * (mc.font.lineHeight + 2) - 2;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int x, int y) {
        if (mc.player == null) return;

        int line = y;

        for (int i = 0; i < SLOTS.length; i++) {
            ItemStack stack = mc.player.getItemBySlot(SLOTS[i]);
            if (stack.isEmpty() && hideEmpty.get()) continue;

            graphics.text(mc.font, row(i), x, line, colorFor(stack), true);
            line += mc.font.lineHeight + 2;
        }
    }

    /** One line of text for a slot. */
    private String row(int index) {
        if (mc.player == null) return LABELS[index] + " --";

        ItemStack stack = mc.player.getItemBySlot(SLOTS[index]);
        if (stack.isEmpty()) return LABELS[index] + " --";

        // Elytra and carved pumpkins live in armour slots too, and neither
        // has a bar worth showing
        if (!stack.isDamageableItem()) return LABELS[index] + " \u221E";

        int left = stack.getMaxDamage() - stack.getDamageValue();

        return showPercent.get()
                ? String.format("%s %d%%", LABELS[index], percent(stack))
                : LABELS[index] + " " + left + "/" + stack.getMaxDamage();
    }

    private int percent(ItemStack stack) {
        if (stack.getMaxDamage() <= 0) return 100;
        int left = stack.getMaxDamage() - stack.getDamageValue();
        return Math.round(left * 100f / stack.getMaxDamage());
    }

    private int colorFor(ItemStack stack) {
        if (!warnColors.get() || stack.isEmpty() || !stack.isDamageableItem()) {
            return textColor.get();
        }

        int left = percent(stack);
        if (left <= 10) return 0xFFFF6B81;
        if (left <= 25) return 0xFFFFD9A0;
        return textColor.get();
    }
}
