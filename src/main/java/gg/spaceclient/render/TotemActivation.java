package gg.spaceclient.render;

import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Takes the totem pop away from the game so this client can draw it instead.
 *
 * When a totem saves you, the game puts the stack into a field on its renderer
 * and draws it, growing, in the middle of the screen for about two seconds. The
 * size of that is not a setting - it is a constant inside the drawing method.
 *
 * Rather than guess that method's name and signature on this version, this goes
 * at the field it reads. Emptying the field stops the game's animation as
 * completely as cancelling the draw would, and it needs nothing but a field of
 * a type that is right there in the signature of everything around it.
 *
 * Which field is found by type, not by name: mappings rename fields and this
 * one is the only ItemStack a renderer holds. A version where that stops being
 * true costs the setting and nothing else - the field is never emptied, and the
 * game draws its own animation exactly as it always did.
 */
public final class TotemActivation {

    private static boolean resolved = false;
    private static Field field = null;
    private static String report = "not looked up yet";

    private TotemActivation() {}

    public static String status() { return report; }

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
            report = "no item field on " + renderer.getClass().getSimpleName()
                    + " - the game draws its own pop";
            return;
        }

        try {
            field.setAccessible(true);
        } catch (Throwable t) {
            field = null;
            report = "item field found but sealed - the game draws its own pop";
            return;
        }

        report = "using " + field.getName() + " (" + candidates + " candidate"
                + (candidates == 1 ? "" : "s") + ")";
    }

    /**
     * Hands over the stack the game was about to animate, and empties the field
     * so it does not.
     *
     * @return the stack, or null when there is no pop to take.
     */
    public static ItemStack claim(Object renderer) {
        if (renderer == null) return null;

        resolve(renderer);
        if (field == null) return null;

        try {
            Object value = field.get(renderer);
            if (!(value instanceof ItemStack stack) || stack.isEmpty()) return null;

            field.set(renderer, null);
            return stack;

        } catch (Throwable t) {
            // One failure is enough to stop trying: a field that cannot be
            // written is a field that would be read again next frame, and
            // claiming a pop the game then also draws is the one outcome worse
            // than not claiming it at all.
            field = null;
            report = "item field could not be written - the game draws its own pop";
            return null;
        }
    }
}
