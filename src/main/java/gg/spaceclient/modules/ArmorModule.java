package gg.spaceclient.modules;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.ModeSetting;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * What you are wearing, and how much of it is left.
 *
 * The point of this on the HUD is the moment before a piece breaks, so the
 * numbers are coloured by how much is left rather than shown flat: a boot at
 * eight percent should catch the eye without being read.
 */
public class ArmorModule extends HudModule {

    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET,
    };

    private static final String[] LABELS = { "Helm", "Chest", "Legs", "Boots" };

    private final ModeSetting style = new ModeSetting(
            "style", "Show as", "Percentage left, or points remaining",
            List.of("Percent", "Points"), "Percent");

    private final BooleanSetting showHeld = new BooleanSetting(
            "show_held", "Include held item",
            "Adds whatever is in your main hand", true);

    private final BooleanSetting hideFull = new BooleanSetting(
            "hide_full", "Hide undamaged",
            "Only list pieces that have taken damage", false);

    private final BooleanSetting hideEmpty = new BooleanSetting(
            "hide_empty", "Hide empty slots",
            "Leave out slots with nothing in them", true);

    public ArmorModule() {
        super("armor", "Armour", "Durability of what you are wearing",
                0.90f, 0.40f, false);
        addSettings(style, showHeld, hideFull, hideEmpty);
    }

    /** One line per piece, built at most as often as the base class allows. */
    private List<String> lines() {
        List<String> out = new ArrayList<>();
        if (mc.player == null) return out;

        for (int i = 0; i < SLOTS.length; i++) {
            String line = describe(LABELS[i], slot(SLOTS[i]));
            if (line != null) out.add(line);
        }

        if (showHeld.get()) {
            String line = describe("Hand", slot(EquipmentSlot.MAINHAND));
            if (line != null) out.add(line);
        }

        if (out.isEmpty()) out.add("no armour");
        return out;
    }

    private ItemStack slot(EquipmentSlot which) {
        try {
            return mc.player.getItemBySlot(which);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Null when this row should not appear at all. */
    private String describe(String label, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return hideEmpty.get() ? null : label + "  -";
        }

        int max = maxDamage(stack);
        if (max <= 0) {
            // Something unbreakable, or with no durability to speak of
            return hideFull.get() ? null : label + "  ok";
        }

        int left = max - damage(stack);
        if (left >= max && hideFull.get()) return null;

        if ("Points".equals(style.get())) {
            return label + "  " + left + "/" + max;
        }
        return label + "  " + Math.round(left * 100f / max) + "%";
    }

    private static int maxDamage(ItemStack stack) {
        try { return stack.getMaxDamage(); } catch (Throwable ignored) { return 0; }
    }

    private static int damage(ItemStack stack) {
        try { return stack.getDamageValue(); } catch (Throwable ignored) { return 0; }
    }

    /**
     * Green while there is room to spare, amber once it is worth noticing, red
     * when the piece is about to go. The thresholds are deliberately late: a
     * warning that starts at half is a warning people learn to ignore.
     */
    private int colorFor(String line) {
        int pct = percentIn(line);
        if (pct < 0) return 0xFFFFFFFF;
        if (pct <= 10) return 0xFFFF5555;
        if (pct <= 30) return 0xFFFFAA00;
        return 0xFF55FF55;
    }

    /** Reads the share back out of the finished line, so colour follows text. */
    private int percentIn(String line) {
        try {
            if (line.endsWith("%")) {
                int space = line.lastIndexOf(' ');
                return Integer.parseInt(line.substring(space + 1, line.length() - 1));
            }
            int slash = line.indexOf('/');
            if (slash < 0) return -1;
            int space = line.lastIndexOf(' ', slash);
            int left = Integer.parseInt(line.substring(space + 1, slash).trim());
            int max = Integer.parseInt(line.substring(slash + 1).trim());
            return max <= 0 ? -1 : Math.round(left * 100f / max);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    /**
     * The lines, cached.
     *
     * Joined into one string so the base class's single text cache covers a
     * multi-line element too, then split again for drawing - cheaper than
     * caching a list and no different to read.
     */
    private List<String> cachedLines() {
        String joined = cachedText(() -> String.join("\n", lines()));
        return List.of(joined.split("\n"));
    }

    @Override
    public int getWidth() {
        int widest = 0;
        for (String line : cachedLines()) {
            widest = Math.max(widest, mc.font.width(line));
        }
        return widest;
    }

    @Override
    public int getHeight() {
        return cachedLines().size() * (mc.font.lineHeight + 1);
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int x, int y) {
        int row = y;
        for (String line : cachedLines()) {
            graphics.text(mc.font, line, x, row, colorFor(line), true);
            row += mc.font.lineHeight + 1;
        }
    }
}
