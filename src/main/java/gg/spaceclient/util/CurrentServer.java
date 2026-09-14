package gg.spaceclient.util;

import net.minecraft.client.Minecraft;

/**
 * Which server you are on, as a plain address.
 *
 * Reflective, because getCurrentServer is one of the names this mod has never
 * been able to compile against, and cached, because the answer changes when you
 * join something and at no other time - while the things that ask run every
 * tick.
 *
 * ServerModule resolves this for itself as well, and the two were left apart on
 * purpose. That one caches on its own schedule and strips the port for display;
 * this one wants the address exactly as the game holds it, because it is used as
 * a key. Sharing the code would mean one of them getting a value shaped for the
 * other's purpose.
 */
public final class CurrentServer {

    private static final long REFRESH_MS = 1000L;

    private static long checkedAt = 0L;
    private static String cached = null;

    private CurrentServer() {}

    /**
     * The current server's address, or null in singleplayer and in the menus.
     *
     * Null is a real answer here and not a failure - "no server" is a state
     * worth having its own layout for.
     */
    public static String address() {
        long now = System.currentTimeMillis();
        if (now - checkedAt < REFRESH_MS) return cached;
        checkedAt = now;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            cached = null;
            return null;
        }

        Object server = Reflect.call(mc, "getCurrentServer", "getCurrentServerEntry");
        if (server == null) {
            cached = null;
            return null;
        }

        Object ip = Reflect.call(server, "getIp", "getAddress", "ip");
        if (ip instanceof String text && !text.isEmpty()) {
            cached = text;
            return cached;
        }

        // The field, for a version where the accessor is named differently
        try {
            var field = server.getClass().getDeclaredField("ip");
            field.setAccessible(true);
            Object value = field.get(server);
            cached = value instanceof String text && !text.isEmpty() ? text : null;
        } catch (Throwable ignored) {
            cached = null;
        }
        return cached;
    }

    /** Forces the next call to look again, for the moment a connection changes. */
    public static void forget() {
        checkedAt = 0L;
        cached = null;
    }
}
