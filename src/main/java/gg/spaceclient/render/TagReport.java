package gg.spaceclient.render;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Finds the camera distance on a render state, and says whether it found one.
 *
 * This exists because the name tag half of the FPS boost never did anything.
 * It asked for the distance through Reflect.call, which searches methods only -
 * and 26.2 keeps this data in public fields, not getters. The lookup returned
 * null every time, the caller read that as "nothing to judge by", and no tag
 * was ever hidden. Nothing logged, nothing failed, the setting simply did
 * nothing. The same shape of bug as the cape hook, found the same way.
 *
 * Two things follow from that, and both are the point of this class.
 *
 * The accessor is resolved once and cached. Reflect walks the class hierarchy
 * on every single call, which is fine for a diagnostics page and wrong for a
 * path that runs per name tag per frame.
 *
 * And no field name is trusted. The likely spellings are tried first, then
 * anything numeric on the state whose name mentions a distance, so a renamed
 * field degrades to a slower match rather than to silence. Whatever it settles
 * on is reported by name, so the diagnostics screen shows what was actually
 * found instead of promising that something was.
 */
public final class TagReport {

    private static final String[] CANDIDATES = {
            "distanceToCameraSq", "distanceToCameraSquared", "cameraDistanceSq",
            "distanceToCamera", "cameraDistance", "distanceSq"
    };

    private static boolean looked = false;
    private static Field field = null;
    private static Method method = null;

    /** Whether the value found is already squared, which decides the comparison. */
    private static boolean squared = false;

    private static String how = null;
    private static String stateName = null;

    private static int hidden = 0;
    private static int kept = 0;

    private TagReport() {}

    /**
     * The squared camera distance for this state, or null if nothing was found.
     *
     * Always squared on the way out, whatever was found on the way in: hiding
     * that difference here means the caller cannot get the comparison wrong,
     * and a field named without "Sq" that holds a plain distance would
     * otherwise be judged against a squared limit and hide every tag in sight.
     */
    public static Double distanceSqOf(Object state) {
        if (state == null) return null;
        resolve(state);

        Object value = null;
        try {
            if (field != null) value = field.get(state);
            else if (method != null) value = method.invoke(state);
        } catch (Throwable ignored) {
            return null;
        }

        if (!(value instanceof Number number)) return null;
        double distance = number.doubleValue();
        return squared ? distance : distance * distance;
    }

    private static void resolve(Object state) {
        if (looked) return;
        looked = true;
        stateName = state.getClass().getSimpleName();

        for (String name : CANDIDATES) {
            Field found = findField(state.getClass(), name);
            if (found != null && isNumeric(found.getType())) {
                field = found;
                field.setAccessible(true);
                squared = isSquared(name);
                how = "field " + name;
                return;
            }
        }

        for (String name : CANDIDATES) {
            Method found = findMethod(state.getClass(), name);
            if (found != null && isNumeric(found.getReturnType())) {
                method = found;
                method.setAccessible(true);
                squared = isSquared(name);
                how = "method " + name + "()";
                return;
            }
        }

        // Nothing matched a name we know. Anything numeric that calls itself a
        // distance beats giving up, because giving up here is the silent
        // failure this class was written to end.
        Class<?> current = state.getClass();
        while (current != null && current != Object.class) {
            for (Field candidate : current.getDeclaredFields()) {
                if (Modifier.isStatic(candidate.getModifiers())) continue;
                if (!candidate.getName().toLowerCase().contains("distance")) continue;
                if (!isNumeric(candidate.getType())) continue;
                field = candidate;
                field.setAccessible(true);
                squared = isSquared(candidate.getName());
                how = "field " + candidate.getName() + " (found by scanning)";
                return;
            }
            current = current.getSuperclass();
        }
    }

    private static boolean isSquared(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith("sq") || lower.endsWith("squared");
    }

    private static boolean isNumeric(Class<?> type) {
        return type == double.class || type == float.class
                || type == int.class || type == long.class
                || Number.class.isAssignableFrom(type);
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            for (Method candidate : current.getDeclaredMethods()) {
                if (candidate.getParameterCount() == 0 && candidate.getName().equals(name)) {
                    return candidate;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    public static void hidden() { hidden++; }

    public static void kept() { kept++; }

    /** False only when a name tag was judged and the distance could not be read. */
    public static boolean working() {
        return !looked || how != null;
    }

    public static String status() {
        if (!looked) {
            return "no name tags drawn yet - stand near a player or a named mob";
        }
        if (how == null) {
            return "no distance found on " + stateName + " - tags are never hidden";
        }
        if (hidden == 0 && kept == 0) {
            return how + " resolved, module off or nothing judged yet";
        }
        return how + ", " + hidden + " hidden, " + kept + " drawn";
    }
}
