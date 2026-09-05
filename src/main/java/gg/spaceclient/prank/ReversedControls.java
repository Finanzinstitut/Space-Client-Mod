package gg.spaceclient.prank;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

import java.lang.reflect.Field;

/**
 * Swaps left and right while the reversed-controls prank runs.
 *
 * Done by exchanging the two KeyMapping objects on the Options, not by forcing
 * key states - the "press this key" call is unproven on this version, while the
 * key fields themselves are read all over this mod. Swapping the bindings makes
 * A do what D did, which is the whole joke, and swapping them back restores it
 * exactly.
 *
 * Local to the last degree: it changes which of your own keys walks which way.
 * The server sees ordinary movement, other players see nothing unusual, and the
 * moment the effect ends it is as if nothing happened.
 */
public final class ReversedControls {

    private static boolean swapped = false;

    /** Called every client tick. Swaps on when the effect starts, off when it ends. */
    public static void tick(Minecraft mc) {
        boolean shouldSwap = Pranks.active() == Pranks.Effect.REVERSED_CONTROLS
                && !Pranks.expired();

        if (shouldSwap && !swapped) {
            if (swap(mc.options)) swapped = true;
        } else if (!shouldSwap && swapped) {
            swap(mc.options);   // swapping again puts them back
            swapped = false;
        }
    }

    /**
     * Exchanges the left and right bindings.
     *
     * Reflective on the field, because the two are swapped by reassigning the
     * fields and there is no public setter. If the fields turn out to be final
     * on this version the swap simply does not happen and the prank is a no-op,
     * which is a fair failure for a joke.
     */
    private static boolean swap(Options options) {
        try {
            Field left = findField(options, options.keyLeft);
            Field right = findField(options, options.keyRight);
            if (left == null || right == null) return false;

            KeyMapping a = (KeyMapping) left.get(options);
            KeyMapping b = (KeyMapping) right.get(options);
            left.set(options, b);
            right.set(options, a);
            return true;

        } catch (Throwable t) {
            return false;
        }
    }

    private static Field findField(Options options, KeyMapping value) {
        for (Field field : Options.class.getDeclaredFields()) {
            if (field.getType() != KeyMapping.class) continue;
            try {
                field.setAccessible(true);
                if (field.get(options) == value) return field;
            } catch (Throwable ignored) {
                // Keep looking
            }
        }
        return null;
    }

    private ReversedControls() {}
}
