package gg.spaceclient.render;

import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;

/**
 * Silences the spin and the float by emptying what drives them.
 *
 * The turning and the bobbing are worked out inside the drawing method from
 * the age and bob offset the render state carries, and the injection this
 * client uses runs before that. So there is no way to undo them afterwards -
 * but they come out as nothing when those two are zeroed while the state is
 * still being filled in.
 *
 * Both are public fields on 26.3 and written directly. The reflective search
 * this replaced could miss without a word, the way the rotation lookup did.
 */
public final class ItemStateFields {

    private static String report = "not used yet";

    private ItemStateFields() {}

    public static String status() { return report; }

    /** Zeroes the age and bob offset. @return true if the state was an item's. */
    public static boolean still(Object state) {
        if (!(state instanceof ItemEntityRenderState item)) return false;
        item.ageInTicks = 0f;
        item.bobOffset = 0f;
        report = "stilling ageInTicks, bobOffset";
        return true;
    }
}
