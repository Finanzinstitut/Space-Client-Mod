package gg.spaceclient.modules;

import gg.spaceclient.module.Module;
import gg.spaceclient.render.TotemActivation;
import gg.spaceclient.setting.IntSetting;
import gg.spaceclient.ui.TotemArt;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.lang.reflect.Field;

/**
 * Draws the totem pop, at a size you choose, over anything.
 *
 * The game's own version is a fixed size decided for a screen much smaller than
 * most people now play on. On a wide monitor it is a small thing happening in
 * the middle of a fight, and the moment it marks - you did not die - is the
 * most important thing that happens in a fight.
 *
 * It also draws the item's own model, which means a resource pack decides what
 * that moment looks like. This one does not: the picture is rectangles in
 * TotemArt, and there is no asset for a pack to replace.
 *
 * Switching this on takes the animation over rather than adding to it, so there
 * is never more than one totem on screen. Off by default, because it replaces
 * something that already works.
 */
public class TotemPopModule extends Module {

    /** How large the totem is drawn at 100%, in scaled screen pixels. */
    private static final float BASE = 96f;

    private final IntSetting size = new IntSetting(
            "size", "Pop size",
            "How large the totem is drawn, in percent", 100, 25, 400);

    private final IntSetting duration = new IntSetting(
            "duration", "How long",
            "How long the totem stays, in tenths of a second", 16, 4, 60);

    private Field rendererField = null;
    private boolean rendererLooked = false;

    public TotemPopModule() {
        super("totempop", "Totem pop",
                "Draw the totem pop at your own size, over any resource pack", false);
        addSettings(size, duration);
    }

    private long durationMillis() { return duration.get() * 100L; }

    /**
     * Plays one pop when the module is switched on.
     *
     * Only in a world, because the settings are also read back at startup and
     * that path fires this too - there is nothing to draw on yet, and a pop
     * nobody can see is not a preview.
     */
    @Override
    protected void onEnable() {
        if (mc.level != null) TotemActivation.preview();
    }

    /**
     * Called every frame from the HUD pass, whether or not a pop is in flight.
     *
     * The fallback claim has to happen on the frame the game fills its field,
     * so it cannot wait until there is something to draw.
     */
    public void draw(GuiGraphicsExtractor graphics, int width, int height) {
        // Only while the injection has never fired. On a version where it
        // works this costs one boolean read a frame and nothing else.
        if (!TotemActivation.hookRan()) {
            Object renderer = gameRenderer();
            if (renderer != null) TotemActivation.claim(renderer);
        }

        long at = TotemActivation.popAt();
        if (at == 0L) return;

        long age = System.currentTimeMillis() - at;
        if (age >= durationMillis()) {
            TotemActivation.clear();
            return;
        }

        float t = age / (float) durationMillis();
        float scale = BASE * (size.get() / 100f) * grow(t);
        if (scale < 4f) return;

        TotemArt.draw(graphics, width / 2, height / 2, scale, Math.round(255 * fade(t)));
    }

    /**
     * How large the totem is through the animation: up over the first fifth,
     * then held. It does not shrink again - the fade is what ends it, and doing
     * both at once reads as the totem being taken away rather than spent.
     */
    private static float grow(float t) {
        if (t >= 0.2f) return 1f;
        float p = t / 0.2f;
        return 0.45f + 0.55f * (1f - (1f - p) * (1f - p));
    }

    /**
     * Opaque until three quarters through, then out.
     *
     * Drawing the picture from rectangles is what makes this possible at all:
     * the colours are this client's own, so an alpha can be folded into them.
     * The item drawing path took no colour, which is why the first version had
     * to shrink the totem away instead.
     */
    private static float fade(float t) {
        if (t <= 0.75f) return 1f;
        return 1f - (t - 0.75f) / 0.25f;
    }

    /**
     * The object holding the activation field, for the fallback only.
     *
     * Through a method first and a field second, because the game has spelled
     * this both ways and neither spelling is worth a hard dependency.
     */
    private Object gameRenderer() {
        Object viaMethod = gg.spaceclient.util.Reflect.call(mc, "gameRenderer", "getGameRenderer");
        if (viaMethod != null) return viaMethod;

        if (!rendererLooked) {
            rendererLooked = true;
            outer:
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
                        // Leave it null; the fallback simply does nothing
                    }
                    break outer;
                }
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
