package gg.spaceclient.modules;

import gg.spaceclient.module.Module;
import gg.spaceclient.render.TotemActivation;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.IntSetting;
import gg.spaceclient.ui.Scale;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;

/**
 * Draws the totem pop, at a size you choose.
 *
 * The game's own version is a fixed size decided years ago for a screen much
 * smaller than most people now play on. On a wide monitor it is a small thing
 * happening in the middle of a fight, and the moment it marks - you did not die
 * - is the single most important thing that happens in a fight.
 *
 * Switching this on takes the animation over rather than adding to it: the
 * stack is claimed out of the game's renderer, which stops its animation
 * completely, and this one is drawn in its place. If the field cannot be found
 * on this version nothing is claimed, the game keeps drawing its own, and the
 * diagnostics page says so instead of leaving a setting that does nothing.
 *
 * Off by default, because it replaces something that already works.
 */
public class TotemPopModule extends Module {

    /** The icon's own size. Everything below is a multiple of this. */
    private static final int ICON = 16;

    /** How large the totem draws at 100%, in scaled screen pixels. */
    private static final float BASE = 72f;

    private final IntSetting size = new IntSetting(
            "size", "Pop size",
            "How large the totem is drawn, in percent", 100, 25, 400);

    private final IntSetting duration = new IntSetting(
            "duration", "How long", "How long the totem stays, in tenths of a second", 16, 4, 60);

    private final BooleanSetting spin = new BooleanSetting(
            "spin", "Turn", "Let the totem turn as it appears", false);

    /** When the last pop was claimed, or 0 for no pop in flight. */
    private long popAt = 0L;

    private Field rendererField = null;
    private boolean rendererLooked = false;

    public TotemPopModule() {
        super("totempop", "Totem pop", "Draw the totem pop at your own size", false);
        addSettings(size, duration, spin);
    }

    /** Tenths of a second to milliseconds. */
    private long durationMillis() { return duration.get() * 100L; }

    /**
     * Called every frame from the HUD pass, whether or not a pop is in flight.
     *
     * The claim has to happen on the frame the game sets the field, so this
     * cannot wait until there is something to draw.
     */
    public void draw(GuiGraphicsExtractor graphics, int width, int height) {
        Object renderer = gameRenderer();
        if (renderer == null) return;

        ItemStack claimed = TotemActivation.claim(renderer);
        if (claimed != null && isTotem(claimed)) {
            held = claimed;
            popAt = System.currentTimeMillis();
        }

        if (popAt == 0L || held == null || held.isEmpty()) return;

        long age = System.currentTimeMillis() - popAt;
        if (age >= durationMillis()) {
            popAt = 0L;
            return;
        }

        float t = age / (float) durationMillis();
        float scale = BASE / ICON * (size.get() / 100f) * curve(t);
        if (scale <= 0f) return;

        int centreX = width / 2;
        int centreY = height / 2;

        // The icon is drawn from its corner, so the corner is what has to land
        // half an icon up and left of the middle.
        int x = centreX - ICON / 2;
        int y = centreY - ICON / 2;

        if (!Scale.push(graphics, centreX, centreY, scale)) return;
        try {
            Scale.translate(graphics, -centreX, -centreY);
            var gui = (gg.spaceclient.mixin.GuiItemInvoker) (Object) graphics;
            gui.spaceclient$item(mc.player, mc.level, held, x, y, spin.get() ? (int) age : 0);
        } catch (Throwable ignored) {
            // A version where the icon call is not reachable draws nothing,
            // which is the same as the setting being off
        } finally {
            Scale.pop(graphics);
        }
    }

    /**
     * How large the totem is through the animation.
     *
     * Grows in over the first fifth, holds, then pulls back over the last
     * quarter. Pulling back rather than fading because a fade needs a colour
     * the item drawing path does not take, and a totem that shrinks away reads
     * as clearly finished as one that dims.
     */
    private static float curve(float t) {
        if (t < 0.2f) {
            float p = t / 0.2f;
            return 0.35f + 0.65f * (1f - (1f - p) * (1f - p));
        }
        if (t > 0.75f) {
            float p = (t - 0.75f) / 0.25f;
            return 1f - p * p;
        }
        return 1f;
    }

    /** The stack that was claimed, kept because the drawing outlives the frame
     *  the game handed it over on. */
    private ItemStack held = null;

    private static boolean isTotem(ItemStack stack) {
        try {
            String id = gg.spaceclient.config.ItemSizes.keyFor(stack);
            return id != null && id.endsWith("totem_of_undying");
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * The object holding the activation field.
     *
     * Through a method first and a field second, because the game has spelled
     * this both ways and neither spelling is worth a hard dependency.
     */
    private Object gameRenderer() {
        Object viaMethod = gg.spaceclient.util.Reflect.call(mc, "gameRenderer", "getGameRenderer");
        if (viaMethod != null) return viaMethod;

        if (!rendererLooked) {
            rendererLooked = true;
            for (Class<?> type = mc.getClass(); type != null; type = type.getSuperclass()) {
                for (Field f : type.getDeclaredFields()) {
                    // By name where the mapping says one, by type where it does
                    // not - a production mapping leaves the field called
                    // something like f_91063_ and only the type still speaks.
                    boolean byName = f.getName().toLowerCase().contains("gamerenderer");
                    boolean byType = "GameRenderer".equals(f.getType().getSimpleName());
                    if (!byName && !byType) continue;
                    try {
                        f.setAccessible(true);
                        rendererField = f;
                    } catch (Throwable ignored) {
                        // Leave it null; the module simply does nothing
                    }
                    break;
                }
                if (rendererField != null) break;
            }
        }
        if (rendererField == null) return null;
        try {
            return rendererField.get(mc);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
