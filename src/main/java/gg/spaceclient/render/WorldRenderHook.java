package gg.spaceclient.render;

import gg.spaceclient.SpaceClient;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Subscribes to Fabric's world render event without naming it at compile time.
 *
 * The event classes moved out of their old package in this version, and simply
 * importing them was enough to fail the build. Since the callback is a single
 * method interface, a dynamic proxy can stand in for it: the class is looked up
 * by name at runtime, the proxy is built against whatever interface it declares,
 * and if none of the candidates exist the hook reports that and the caller falls
 * back.
 */
public final class WorldRenderHook {

    /**
     * Where the event class has lived across versions. Fabric API 26.1 renamed
     * WorldRenderEvents to LevelRenderEvents and moved it into a level
     * subpackage, which is why the older names alone found nothing.
     */
    private static final String[] CANDIDATES = {
            "net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents",
            "net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents",
            "net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents",
    };

    /** Event field names worth preferring, if any of them exist. */
    private static final String[] PREFERRED = {
            "AFTER_ENTITIES", "END_MAIN", "LAST", "END", "AFTER_TRANSLUCENT"
    };

    /** Why the last subscription attempt failed, for the diagnostics page. */
    private static String failure = "not attempted";

    public static String failure() { return failure; }

    /**
     * @param callback receives the render context as an Object
     * @return true when the subscription succeeded
     */
    public static boolean subscribe(Consumer<Object> callback) {
        Class<?> eventsClass = findEventsClass();
        if (eventsClass == null) {
            failure = "no known event class on the classpath";
            SpaceClient.LOGGER.warn("World render event class not found");
            return false;
        }

        // Rather than trusting a fixed list of field names, every static field
        // that looks like an event is considered, preferred ones first. Field
        // names move as often as class names do.
        List<Field> candidates = new ArrayList<>();
        for (String wanted : PREFERRED) {
            for (Field field : eventsClass.getFields()) {
                if (field.getName().equals(wanted)) candidates.add(field);
            }
        }
        for (Field field : eventsClass.getFields()) {
            if (!candidates.contains(field) && Modifier.isStatic(field.getModifiers())) {
                candidates.add(field);
            }
        }

        StringBuilder tried = new StringBuilder();

        for (Field field : candidates) {
            try {
                Object event = field.get(null);
                if (event == null) continue;

                Method register = findRegister(event.getClass());
                if (register == null) {
                    tried.append(field.getName()).append(": no register method; ");
                    continue;
                }

                // The callback interface comes from the field's generic type,
                // not from the register parameter: Event<T>.register(T) erases
                // to register(Object), so asking the method yields Object and
                // every event looked unusable.
                Class<?> listenerType = listenerFromField(field);
                if (listenerType == null) {
                    tried.append(field.getName()).append(": callback type not readable; ");
                    continue;
                }

                Object proxy = Proxy.newProxyInstance(
                        listenerType.getClassLoader(),
                        new Class<?>[]{listenerType},
                        (self, method, args) -> {
                            // Object methods must behave, or the proxy misbehaves
                            switch (method.getName()) {
                                case "toString" -> { return "SpaceClientWorldRenderHook"; }
                                case "hashCode" -> { return System.identityHashCode(self); }
                                case "equals" -> { return self == (args == null ? null : args[0]); }
                                default -> { }
                            }
                            if (args != null && args.length >= 1) {
                                try {
                                    callback.accept(args[0]);
                                } catch (Throwable t) {
                                    SpaceClient.LOGGER.warn("World render callback failed", t);
                                }
                            }
                            // Some callbacks return boolean; false would cancel
                            Class<?> returns = method.getReturnType();
                            if (returns == boolean.class) return true;
                            return null;
                        });

                register.invoke(event, proxy);
                failure = "subscribed via " + field.getName();
                SpaceClient.LOGGER.info("Subscribed to world rendering via {}", field.getName());
                return true;

            } catch (Throwable t) {
                tried.append(field.getName()).append(": ").append(t.getClass().getSimpleName())
                        .append("; ");
            }
        }

        failure = tried.length() == 0 ? "no usable event field" : tried.toString();
        SpaceClient.LOGGER.warn("Could not subscribe to world rendering: {}", failure);
        return false;
    }

    private static Class<?> findEventsClass() {
        for (String name : CANDIDATES) {
            try {
                return Class.forName(name);
            } catch (Throwable ignored) {
                // Try the next package
            }
        }
        return null;
    }

    /**
     * Reads the callback interface out of a field declared as Event&lt;Something&gt;.
     * Generic information survives on fields even though it is erased from the
     * method signature, which is what makes this work at all.
     */
    private static Class<?> listenerFromField(Field field) {
        try {
            Type generic = field.getGenericType();
            if (generic instanceof ParameterizedType parameterized) {
                Type[] arguments = parameterized.getActualTypeArguments();
                if (arguments.length > 0 && arguments[0] instanceof Class<?> type) {
                    return type.isInterface() ? type : null;
                }
            }
        } catch (Throwable ignored) {
            // Falls through to null
        }
        return null;
    }

    private static Method findRegister(Class<?> eventClass) {
        for (Method method : eventClass.getMethods()) {
            if (method.getName().equals("register") && method.getParameterCount() == 1) {
                return method;
            }
        }
        return null;
    }

    private WorldRenderHook() {}
}
