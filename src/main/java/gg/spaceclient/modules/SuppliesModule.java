package gg.spaceclient.modules;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.ColorSetting;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;

/**
 * How many of the things that decide a fight are left.
 *
 * The inventory element answers this too, but it answers it by being read. This
 * answers it by being glanced at, which is a different thing when someone is
 * hitting you: a number that has gone from 3 to 2 is noticed without looking
 * away from the fight, and a grid of icons is not.
 *
 * The set is deliberately short. Totems, gapples, pearls and arrows are the
 * items whose count changes what you do next; adding blocks and swords to the
 * list would turn a glance back into a read.
 */
public class SuppliesModule extends HudModule {

    /** Matched against the item's description id, which is a plain string. */
    private static final String TOTEM = "totem_of_undying";
    private static final String GAPPLE = "golden_apple";
    private static final String PEARL = "ender_pearl";
    private static final String ARROW = "arrow";

    /** Every slot including armour and offhand. */
    private static final int SLOTS = 41;

    private final BooleanSetting showTotems = new BooleanSetting(
            "totems", "Totems", "Count totems of undying", true);

    private final BooleanSetting showGapples = new BooleanSetting(
            "gapples", "Golden apples", "Count both kinds of golden apple", true);

    private final BooleanSetting showPearls = new BooleanSetting(
            "pearls", "Ender pearls", "Count ender pearls", true);

    private final BooleanSetting showArrows = new BooleanSetting(
            "arrows", "Arrows", "Count arrows", false);

    private final BooleanSetting warnLow = new BooleanSetting(
            "warn_low", "Warn when low",
            "Colour a count red once it reaches one", true);

    private final ColorSetting textColor = new ColorSetting(
            "text_color", "Text colour", "Colour of the counts", 0xFFFFFFFF);

    /**
     * Resolved once.
     *
     * `getCount` is almost certainly still on ItemStack, but `getDescriptionId`
     * was not, and that one only turned up as a failed build. Going through a
     * cached lookup costs nothing measurable and turns the same surprise into a
     * zero on screen.
     */
    private static Method countMethod;
    private static boolean countResolved = false;

    public SuppliesModule() {
        super("supplies", "Supplies",
                "Totems, gapples and pearls left, at a glance",
                0.02f, 0.44f, false);
        addSettings(showTotems, showGapples, showPearls, showArrows, warnLow, textColor);
    }

    @Override
    protected long refreshMillis() { return 250; }

    private static int countOf(ItemStack stack) {
        if (!countResolved) {
            countResolved = true;
            try {
                countMethod = ItemStack.class.getMethod("getCount");
            } catch (Throwable ignored) {
                countMethod = null;
            }
        }
        if (countMethod == null) return 1;
        try {
            Object value = countMethod.invoke(stack);
            return value instanceof Integer number ? number : 1;
        } catch (Throwable ignored) {
            return 1;
        }
    }

    /** Total across the whole inventory, matched on the item's id. */
    private int total(String needle) {
        int total = 0;
        try {
            if (mc.player == null) return 0;
            for (int slot = 0; slot < SLOTS; slot++) {
                ItemStack stack = mc.player.getInventory().getItem(slot);
                if (stack == null || stack.isEmpty()) continue;

                String id = gg.spaceclient.config.ItemSizes.keyFor(stack);
                if (id == null || !id.contains(needle)) continue;

                total += countOf(stack);
            }
        } catch (Throwable ignored) {
            // A count of zero is a better answer than a broken frame
        }
        return total;
    }

    private record Entry(String label, int count) {}

    private java.util.List<Entry> entries() {
        java.util.List<Entry> out = new java.util.ArrayList<>();
        if (showTotems.get()) out.add(new Entry("Totem", total(TOTEM)));
        if (showGapples.get()) out.add(new Entry("Gap", total(GAPPLE)));
        if (showPearls.get()) out.add(new Entry("Pearl", total(PEARL)));
        if (showArrows.get()) out.add(new Entry("Arrow", total(ARROW)));
        return out;
    }

    @Override
    public int getWidth() {
        int width = 0;
        for (Entry entry : entries()) {
            width = Math.max(width, mc.font.width(entry.label() + "  " + entry.count()));
        }
        return Math.max(width, 40);
    }

    @Override
    public int getHeight() {
        return Math.max(1, entries().size()) * (mc.font.lineHeight + 1);
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int x, int y) {
        int line = 0;
        for (Entry entry : entries()) {
            int lineY = y + line * (mc.font.lineHeight + 1);

            graphics.text(mc.font, entry.label(), x, lineY, 0xFFAAAAAA, true);

            // Red at one, amber at nothing: running out and being out are
            // different problems, and the first is the one worth catching
            int color = textColor.get();
            if (warnLow.get()) {
                if (entry.count() == 0) color = 0xFFE86A6A;
                else if (entry.count() == 1) color = 0xFFE8C46A;
            }

            String count = Integer.toString(entry.count());
            graphics.text(mc.font, count,
                    x + getWidth() - mc.font.width(count), lineY, color, true);
            line++;
        }
    }
}
