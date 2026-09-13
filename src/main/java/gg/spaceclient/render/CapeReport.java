package gg.spaceclient.render;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Says what the cape hook actually did, not just that it was attached.
 *
 * Worth its own class because the failure here was invisible for weeks. The
 * injection named a method that exists three times, the injector could not
 * choose, and the mod was configured not to complain - so the cape simply
 * never moved and nothing anywhere said why. A hook that reports having run is
 * the difference between "capes are broken" and knowing which half to look at.
 *
 * "Attached" turned out not to be enough either. The hook reports running and
 * the cape still does not move, which leaves four different faults wearing the
 * same face: the module is off, the numbers it returns are the ones it was
 * given, the hook only ever ran for other players, or something else draws the
 * cape and never reads vanilla's angles at all. Each of those gets its own
 * answer below so the next look settles it instead of narrowing it.
 */
public final class CapeReport {

    private static boolean everRan = false;
    private static boolean everSawSelf = false;
    private static boolean everShaped = false;

    /** The largest angle the module has moved the cape by, in degrees. */
    private static float largestChange = 0f;

    /** The most recent pair, so the screen shows live numbers and not just a peak. */
    private static float lastFlapBefore = 0f;
    private static float lastFlapAfter = 0f;

    /** Why nothing was written, when nothing was. Cleared as soon as something is. */
    private static String skipReason = null;

    /** A one time reading of the cape fields vanilla keeps, taken before we touch them. */
    private static String stateFields = null;

    private CapeReport() {}

    public static void ran() { everRan = true; }

    /** The hook reached the player holding the camera, rather than somebody else. */
    public static void sawSelf() { everSawSelf = true; }

    public static void skipped(String why) { skipReason = why; }

    public static void applied(float flapBefore, float leanBefore, float lean2Before,
                               float flapAfter, float leanAfter, float lean2After) {
        everShaped = true;
        skipReason = null;
        lastFlapBefore = flapBefore;
        lastFlapAfter = flapAfter;

        float change = Math.max(Math.abs(flapAfter - flapBefore),
                       Math.max(Math.abs(leanAfter - leanBefore),
                                Math.abs(lean2After - lean2Before)));
        if (change > largestChange) largestChange = change;
    }

    /**
     * Reads whatever this version calls its cape fields, once.
     *
     * Deliberately not a list of field names: 26.2 moved plenty of them and a
     * guessed name would report "missing" for a field that is merely spelled
     * differently. Everything on the render state whose name mentions a cape is
     * reported with whatever it holds, which answers the real question - does
     * vanilla still have a cape here at all - without naming anything.
     *
     * Runs once and only for the local player, because this walks the class
     * hierarchy with reflection and the caller is a render hook.
     */
    public static void snapshot(Object state) {
        if (stateFields != null || state == null) return;

        List<String> parts = new ArrayList<>();
        Class<?> current = state.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (!field.getName().toLowerCase().contains("cape")) continue;
                try {
                    field.setAccessible(true);
                    parts.add(field.getName() + "=" + describe(field.get(state)));
                } catch (Throwable ignored) {
                    parts.add(field.getName() + "=unreadable");
                }
            }
            current = current.getSuperclass();
        }

        stateFields = parts.isEmpty()
                ? "no field mentioning a cape on " + state.getClass().getSimpleName()
                : String.join(", ", parts);
    }

    private static String describe(Object value) {
        if (value == null) return "null";
        if (value instanceof Float number) return String.format("%.1f", number);
        if (value instanceof Double number) return String.format("%.1f", number);
        String text = String.valueOf(value);
        return text.length() > 48 ? text.substring(0, 45) + "..." : text;
    }

    public static boolean hooked() { return everRan; }

    public static boolean sawSelfYet() { return everSawSelf; }

    /**
     * What the hook has managed to do, in words that are true.
     *
     * The order matters: each state rules out the one below it, so the first
     * line that applies is the one worth acting on.
     */
    public static String status() {
        if (!everRan) {
            return "hook never ran - check the method name for this version";
        }
        if (!everSawSelf) {
            return "hooked, but never yet for your own player - press F5 so your cape is drawn, then look again";
        }
        if (!everShaped) {
            return skipReason == null
                    ? "hooked and reached you, but nothing was written"
                    : "hooked and reached you, but " + skipReason;
        }
        if (largestChange < 0.01f) {
            return "writing, but the angles come back unchanged - raise Sway strength or Idle drift";
        }
        return String.format("writing, up to %.1f deg of change (flap %.1f -> %.1f)",
                largestChange, lastFlapBefore, lastFlapAfter);
    }

    /** What vanilla's own render state holds for the cape, read before we touched it. */
    public static String stateReport() {
        return stateFields == null
                ? "not read yet - press F5 so your own cape is drawn"
                : stateFields;
    }
}
