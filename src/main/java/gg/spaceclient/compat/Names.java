package gg.spaceclient.compat;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Finds the game's classes and members by their Mojang names, whatever names
 * the running game uses.
 *
 * On 26.1 and later, and in the development environment, the game's names
 * are Mojang's own and this is a plain name comparison. A real 1.21.11 game
 * runs under Fabric's intermediary names instead (class_442, method_1531), so
 * the 1.21.11 jar carries a table, generated at build time, from each Mojang
 * name the mod uses to the intermediary names it can stand for. Without it,
 * every lookup by name would quietly find nothing on that version.
 */
public final class Names {

    private Names() {}

    private static volatile boolean loaded = false;
    private static final Map<String, String> CLASSES = new HashMap<>();
    private static final Map<String, Set<String>> RUNTIME_CLASS_TO_NAMED = new HashMap<>();
    private static final Map<String, Set<String>> METHODS = new HashMap<>();
    private static final Map<String, Set<String>> FIELDS = new HashMap<>();

    private static void load() {
        if (loaded) return;
        synchronized (Names.class) {
            if (loaded) return;
            try (InputStream in = Names.class.getResourceAsStream("/assets/spaceclient/names.tsv")) {
                if (in != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] parts = line.split("\t");
                        if (parts.length != 3) continue;
                        switch (parts[0]) {
                            case "c" -> {
                                CLASSES.put(parts[1], parts[2]);
                                RUNTIME_CLASS_TO_NAMED.computeIfAbsent(parts[2], k -> new HashSet<>()).add(parts[1]);
                            }
                            case "m" -> METHODS.computeIfAbsent(parts[1], k -> new HashSet<>()).add(parts[2]);
                            case "f" -> FIELDS.computeIfAbsent(parts[1], k -> new HashSet<>()).add(parts[2]);
                            default -> { }
                        }
                    }
                }
            } catch (Throwable ignored) {
                // No table: names are taken as they are
            }
            loaded = true;
        }
    }

    /** Whether a method's runtime name is the given Mojang name. */
    public static boolean isMethod(String runtimeName, String mojangName) {
        if (runtimeName.equals(mojangName)) return true;
        load();
        Set<String> names = METHODS.get(mojangName);
        return names != null && names.contains(runtimeName);
    }

    /** Whether a field's runtime name is the given Mojang name. */
    public static boolean isField(String runtimeName, String mojangName) {
        if (runtimeName.equals(mojangName)) return true;
        load();
        Set<String> names = FIELDS.get(mojangName);
        return names != null && names.contains(runtimeName);
    }

    /** The runtime name of a class given its Mojang name. */
    public static String className(String mojangName) {
        load();
        return CLASSES.getOrDefault(mojangName, mojangName);
    }

    public static Class<?> forName(String mojangName) throws ClassNotFoundException {
        String runtime = className(mojangName);
        try {
            return Class.forName(runtime);
        } catch (ClassNotFoundException e) {
            if (runtime.equals(mojangName)) throw e;
            return Class.forName(mojangName);
        }
    }

    /** Whether a class is, by its Mojang name, the one given (full or simple name). */
    public static boolean isClass(Class<?> type, String mojangName) {
        if (type == null) return false;
        String runtime = type.getName();
        if (runtime.equals(mojangName) || type.getSimpleName().equals(mojangName)) return true;
        load();
        Set<String> named = RUNTIME_CLASS_TO_NAMED.get(runtime);
        if (named == null) return false;
        for (String n : named) {
            if (n.equals(mojangName) || n.endsWith("." + mojangName) || n.endsWith("$" + mojangName)) return true;
        }
        return false;
    }

    /** Like Class.getDeclaredField, but by Mojang name. */
    public static Field declaredField(Class<?> type, String mojangName) throws NoSuchFieldException {
        try {
            return type.getDeclaredField(mojangName);
        } catch (NoSuchFieldException e) {
            for (Field field : type.getDeclaredFields()) {
                if (isField(field.getName(), mojangName)) return field;
            }
            throw e;
        }
    }

    /** Like Class.getMethod, but by Mojang name. */
    public static java.lang.reflect.Method method(Class<?> type, String mojangName, Class<?>... params)
            throws NoSuchMethodException {
        try {
            return type.getMethod(mojangName, params);
        } catch (NoSuchMethodException e) {
            for (java.lang.reflect.Method method : type.getMethods()) {
                if (isMethod(method.getName(), mojangName)
                        && java.util.Arrays.equals(method.getParameterTypes(), params)) return method;
            }
            throw e;
        }
    }
}
