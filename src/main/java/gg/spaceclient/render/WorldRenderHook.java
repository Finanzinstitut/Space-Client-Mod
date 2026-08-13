package gg.spaceclient.render;

import gg.spaceclient.SpaceClient;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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

    /** Where the event class has lived across versions. */
    private static final String[] CANDIDATES = {
            "net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents",
            "net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents",
            "net.fabricmc.fabric.api.client.render.v1.WorldRenderEvents",
    };

    /** Event fields worth trying, in the order we would prefer them. */
    private static final String[] EVENTS = {"AFTER_ENTITIES", "LAST", "END", "AFTER_TRANSLUCENT"};

    /**
     * @param callback receives the render context as an Object
     * @return true when the subscription succeeded
     */
    public static boolean subscribe(Consumer<Object> callback) {
        Class<?> eventsClass = findEventsClass();
        if (eventsClass == null) {
            SpaceClient.LOGGER.warn(
                    "Fabric's world render event was not found - world drawing is unavailable");
            return false;
        }

        for (String name : EVENTS) {
            try {
                Field field = eventsClass.getField(name);
                Object event = field.get(null);
                if (event == null) continue;

                // The event's register method tells us which interface to fake
                Method register = findRegister(event.getClass());
                if (register == null) continue;

                Class<?> listenerType = register.getParameterTypes()[0];
                if (!listenerType.isInterface()) continue;

                Object proxy = Proxy.newProxyInstance(
                        listenerType.getClassLoader(),
                        new Class<?>[]{listenerType},
                        (self, method, args) -> {
                            // The callback takes the single context argument
                            if (args != null && args.length == 1) {
                                try {
                                    callback.accept(args[0]);
                                } catch (Throwable t) {
                                    SpaceClient.LOGGER.warn("World render callback failed", t);
                                }
                            }
                            return null;
                        });

                register.invoke(event, proxy);
                SpaceClient.LOGGER.info("Subscribed to world rendering via {}", name);
                return true;

            } catch (NoSuchFieldException ignored) {
                // Try the next event name
            } catch (Throwable t) {
                SpaceClient.LOGGER.warn("Could not subscribe to {}: {}", name, t.getMessage());
            }
        }

        SpaceClient.LOGGER.warn("No usable world render event found");
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
