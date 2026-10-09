package gg.spaceclient.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small reflection helpers for API surfaces that could not be verified against
 * this Minecraft version.
 *
 * The rule used throughout this mod: anything proven by a successful compile is
 * called directly, anything guessed goes through here. A wrong guess then costs
 * a null and a log line instead of a failed build, which matters when every
 * build round trip means uploading from a phone.
 *
 * Every lookup is remembered, per class and name, including the ones that
 * found nothing. These helpers are called from HUD elements and renderers
 * every tick or every frame, and an uncached lookup walks the whole class
 * chain with getDeclaredMethods - which copies every Method object of every
 * class on the way, hundreds for an entity. Done per entity per frame that
 * was a steady stream of garbage and a real part of the frame drops.
 */
public final class Reflect {

    /** Stands in for "looked, found nothing", which a ConcurrentHashMap cannot hold as null. */
    private static final Object NONE = new Object();

    private static final Map<Class<?>, Map<String, Object>> NO_ARG = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, Object>> FIELDS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, List<Method>>> NAMED = new ConcurrentHashMap<>();

    /** Calls a no-argument method, trying each name in order. */
    public static Object call(Object target, String... names) {
        if (target == null) return null;
        for (String name : names) {
            try {
                Method method = noArg(target.getClass(), name);
                if (method == null) continue;
                return method.invoke(target);
            } catch (Throwable ignored) {
                // Try the next name
            }
        }
        return null;
    }

    /** Calls a method with arguments, matched by name and argument count. */
    public static Object callWith(Object target, String name, Object... args) {
        if (target == null) return null;
        for (Method method : named(target.getClass(), name)) {
            if (method.getParameterCount() != args.length) continue;
            try {
                return method.invoke(target, args);
            } catch (Throwable ignored) {
                // Try the next overload
            }
        }
        return null;
    }

    /**
     * Reads a field by name, searching up the class chain; null when there is
     * no such field or it cannot be read.
     */
    public static Object get(Object target, String name) {
        if (target == null) return null;
        Field field = field(target.getClass(), name);
        if (field == null) return null;
        try {
            return field.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** The field of that name on the class or a superclass, made accessible; cached. */
    public static Field field(Class<?> type, String name) {
        Object found = FIELDS.computeIfAbsent(type, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(name, k -> {
                    for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                        for (Field f : c.getDeclaredFields()) {
                            if (!gg.spaceclient.compat.Names.isField(f.getName(), name)) continue;
                            try {
                                f.setAccessible(true);
                                return f;
                            } catch (Throwable ignored) {
                                return NONE;
                            }
                        }
                    }
                    return NONE;
                });
        return found instanceof Field f ? f : null;
    }

    private static Method noArg(Class<?> type, String name) {
        Object found = NO_ARG.computeIfAbsent(type, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(name, k -> {
                    Method method = findNoArg(type, name);
                    if (method == null) return NONE;
                    try {
                        method.setAccessible(true);
                        return method;
                    } catch (Throwable ignored) {
                        return NONE;
                    }
                });
        return found instanceof Method m ? m : null;
    }

    private static List<Method> named(Class<?> type, String name) {
        return NAMED.computeIfAbsent(type, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(name, k -> {
                    List<Method> out = new java.util.ArrayList<>();
                    for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                        for (Method method : c.getDeclaredMethods()) {
                            if (!gg.spaceclient.compat.Names.isMethod(method.getName(), name)) continue;
                            try {
                                method.setAccessible(true);
                                out.add(method);
                            } catch (Throwable ignored) {
                                // Not reachable from here; skip it
                            }
                        }
                    }
                    return List.copyOf(out);
                });
    }

    public static Double asDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private static Method findNoArg(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getParameterCount() == 0 && gg.spaceclient.compat.Names.isMethod(method.getName(), name)) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private Reflect() {}
}
