package gg.spaceclient.render;

import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Takes the totem pop away from the game so this client can draw it instead.
 *
 * Two ways in, because the first version had only the second and it did not
 * work. The game hands the stack to a public method on its renderer and then
 * draws it, growing, in the middle of the screen; the size of that is a
 * constant inside the drawing method, which is why it has to be taken over
 * rather than adjusted.
 *
 * The hook is the method, which is public and named in these mappings, and it
 * can refuse the call outright so the game never starts its animation. The
 * fallback is the field that method writes to: emptying it stops the animation
 * just as completely, and it needs nothing but a field of a type that is
 * obvious from the signature of everything around it.
 *
 * The fallback only runs while the hook has never fired, so a version where
 * both work does not end up claiming twice. A version where neither works
 * costs the setting and nothing else - the game draws its own pop, exactly as
 * it always did, and the diagnostics page says which half is missing.
 */
public final class TotemActivation {

    /** Whether the injection into the renderer's method has ever run. */
    private static volatile boolean hookRan = false;

    /** How many pops this client has taken over, for the diagnostics page. */
    private static volatile int pops = 0;

    /** When the pop being drawn arrived, or 0 when there is none. */
    private static volatile long popAt = 0L;

    private static boolean resolved = false;
    private static Field field = null;
    private static String fieldReport = "not looked up yet";

    private TotemActivation() {}

    public static boolean hookRan() { return hookRan; }

    public static long popAt() { return popAt; }

    public static void clear() { popAt = 0L; }

    /**
     * Shows one pop without one having happened.
     *
     * Switching the module on plays it once, which is the difference between
     * "this does nothing" and "this draws, but the game never told it to". Two
     * failures that look identical otherwise, and the first version of this
     * module left no way at all to tell them apart short of dying.
     */
    public static void preview() {
        popAt = System.currentTimeMillis();
    }

    public static String status() {
        return "hook " + (hookRan ? "ok" : "not yet")
                + ", field " + fieldReport
                + ", pops " + pops;
    }

    /**
     * Offered the stack the game is about to animate.
     *
     * @return true when this client has taken it over, which is the caller's
     *         signal to cancel. False leaves the game to draw its own.
     */
    public static boolean offer(ItemStack stack) {
        hookRan = true;
        if (!wanted()) return false;
        if (!isTotem(stack)) return false;

        popAt = System.currentTimeMillis();
        pops++;
        return true;
    }

    /**
     * Hands over the stack the game was about to animate, and empties the field
     * so it does not. Only used on a version where the hook never applied.
     */
    public static void claim(Object renderer) {
        if (renderer == null || hookRan) return;
        if (!wanted()) return;

        resolve(renderer);
        if (field == null) return;

        try {
            Object value = field.get(renderer);
            if (!(value instanceof ItemStack stack) || stack.isEmpty()) return;

            field.set(renderer, null);
            if (!isTotem(stack)) return;

            popAt = System.currentTimeMillis();
            pops++;

        } catch (Throwable t) {
            // One failure is enough to stop trying: a field that cannot be
            // written is a field that would be read again next frame, and
            // claiming a pop the game then also draws is the one outcome worse
            // than not claiming it at all.
            field = null;
            fieldReport = "could not be written";
        }
    }

    /** Whether the module is switched on right now. */
    private static boolean wanted() {
        try {
            var manager = gg.spaceclient.SpaceClient.getModuleManager();
            if (manager == null) return false;
            var module = manager.get("totempop");
            return module != null && module.isEnabled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isTotem(ItemStack stack) {
        try {
            String id = gg.spaceclient.config.ItemSizes.keyFor(stack);
            return id != null && id.endsWith("totem_of_undying");
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Finds the field by type rather than by name: mappings rename fields, and
     * a renderer holds exactly one ItemStack.
     */
    private static synchronized void resolve(Object renderer) {
        if (resolved) return;
        resolved = true;

        int candidates = 0;
        Field named = null;
        Field first = null;

        for (Class<?> type = renderer.getClass(); type != null; type = type.getSuperclass()) {
            for (Field f : type.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (!ItemStack.class.equals(f.getType())) continue;

                candidates++;
                if (first == null) first = f;
                // In a development mapping the name says so outright; in a
                // production one it says nothing and the type has to carry it.
                if (named == null && f.getName().toLowerCase().contains("activation")) named = f;
            }
        }

        field = named != null ? named : first;

        if (field == null) {
            fieldReport = "none on " + renderer.getClass().getSimpleName();
            return;
        }

        try {
            field.setAccessible(true);
        } catch (Throwable t) {
            field = null;
            fieldReport = "found but sealed";
            return;
        }

        fieldReport = field.getName() + " (" + candidates + ")";
    }
}
