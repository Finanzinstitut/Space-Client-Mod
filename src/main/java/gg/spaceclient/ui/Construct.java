package gg.spaceclient.ui;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Builds instances of the game's own screens without naming their shapes.
 *
 * The screens this client hands off to - the server editor, the options pages,
 * the connection flow - all take different arguments than they did two
 * versions ago, and none of them has been compiled against here. Rather than
 * write out a guess per screen and find out at build time, each call describes
 * what it has to offer and takes back whatever fits.
 *
 * Arguments are matched by type, not by position, so a constructor that took
 * (Screen, Options) and now takes (Options, Screen) still resolves. Anything
 * left unfilled becomes null or false, which is the shape most of these
 * optional trailing arguments already had.
 */
public final class Construct {

    /**
     * The first constructor whose arguments can all be supplied from the pool.
     *
     * Longer constructors are tried first: where a class carries both a short
     * and a long form, the long one is usually the current one and the short
     * one a deprecated shim.
     */
    public static Object of(String className, Object... pool) {
        try {
            Class<?> type = Class.forName(className);
            Constructor<?>[] constructors = type.getConstructors();

            java.util.Arrays.sort(constructors,
                    (a, b) -> b.getParameterCount() - a.getParameterCount());

            for (Constructor<?> constructor : constructors) {
                Object[] args = match(constructor.getParameterTypes(), pool);
                if (args == null) continue;
                try {
                    return constructor.newInstance(args);
                } catch (Throwable ignored) {
                    // Built but rejected what it was given; try the next shape
                }
            }
        } catch (Throwable ignored) {
            // Not present on this version
        }
        return null;
    }

    /**
     * As above, but refusing rather than filling a gap with null.
     *
     * Passing null for an argument nothing in the pool matched is convenient
     * and occasionally right, and it is also how a settings screen ends up
     * built with no settings behind it - constructed successfully, opening
     * onto nothing. Where every argument genuinely matters, this returns null
     * instead so the caller can say so.
     */
    public static Object strict(String className, Object... pool) {
        try {
            Class<?> type = Class.forName(className);
            Constructor<?>[] constructors = type.getConstructors();

            java.util.Arrays.sort(constructors,
                    (a, b) -> b.getParameterCount() - a.getParameterCount());

            for (Constructor<?> constructor : constructors) {
                Object[] args = match(constructor.getParameterTypes(), pool);
                if (args == null) continue;

                boolean complete = true;
                Class<?>[] params = constructor.getParameterTypes();
                for (int i = 0; i < args.length; i++) {
                    if (args[i] == null && !params[i].isPrimitive()) {
                        complete = false;
                        break;
                    }
                }
                if (!complete) continue;

                try {
                    return constructor.newInstance(args);
                } catch (Throwable ignored) {
                    // Built but rejected what it was given; try the next shape
                }
            }
        } catch (Throwable ignored) {
            // Not present on this version
        }
        return null;
    }

    /** The same idea for a static factory method. */
    public static Object call(String className, String methodName, Object... pool) {
        try {
            Class<?> type = Class.forName(className);
            Method[] methods = type.getMethods();

            java.util.Arrays.sort(methods,
                    (a, b) -> b.getParameterCount() - a.getParameterCount());

            for (Method method : methods) {
                if (!method.getName().equals(methodName)) continue;
                if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) continue;

                Object[] args = match(method.getParameterTypes(), pool);
                if (args == null) continue;
                try {
                    return method.invoke(null, args);
                } catch (Throwable ignored) {
                    // Try the next overload
                }
            }
        } catch (Throwable ignored) {
            // Not present on this version
        }
        return null;
    }

    /**
     * Fills a parameter list from the pool, one value per slot.
     *
     * A value is used at most once, so a constructor wanting two screens does
     * not get the same one twice - it gets the second screen if one was
     * offered and null if not, which is the right answer for a parent that has
     * genuinely not been supplied.
     */
    private static Object[] match(Class<?>[] params, Object[] pool) {
        Object[] args = new Object[params.length];
        boolean[] used = new boolean[pool.length];

        for (int i = 0; i < params.length; i++) {
            Class<?> want = params[i];
            Object found = null;

            for (int p = 0; p < pool.length; p++) {
                if (used[p] || pool[p] == null) continue;
                if (!want.isInstance(pool[p])) continue;
                found = pool[p];
                used[p] = true;
                break;
            }

            if (found != null) {
                args[i] = found;
            } else if (want == boolean.class) {
                args[i] = Boolean.FALSE;
            } else if (want == int.class) {
                args[i] = 0;
            } else if (want.isPrimitive()) {
                // Some other primitive with no sensible blank; give up rather
                // than pass a number that means something
                return null;
            } else {
                args[i] = null;
            }
        }
        return args;
    }

    private Construct() {}
}
