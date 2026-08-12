package gg.spaceclient.session;

import gg.spaceclient.SpaceClient;
import net.minecraft.client.Minecraft;

/**
 * Notices when a server connection drops and mints a fresh session, so the next
 * join attempt does not fail on an expired token.
 *
 * The disconnect is spotted by watching the network connection rather than the
 * screen: a connection that was there and now is not means the session is worth
 * refreshing. Reading the disconnect reason would need the screen's private
 * state, and a refresh costs one HTTP round trip whether it was needed or not.
 */
public class SessionWatcher {
    /** Do not retry more often than this, so a failing refresh cannot loop. */
    private static final long COOLDOWN_MS = 30_000;

    private static boolean wasConnected = false;
    private static long lastAttempt = 0;

    public static void tick(Minecraft client) {
        boolean connected = client.getConnection() != null;

        // Act on the transition from connected to not, not on every frame after
        if (wasConnected && !connected) {
            onDisconnected();
        }
        wasConnected = connected;
    }

    private static void onDisconnected() {
        long now = System.currentTimeMillis();
        if (now - lastAttempt < COOLDOWN_MS) return;
        if (SessionManager.isBusy()) return;
        if (!LauncherAccounts.isAvailable()) return;

        // Offline profiles have no token to refresh
        String playing = Minecraft.getInstance().getUser().getName();
        boolean offline = LauncherAccounts.load().stream()
                .filter(a -> a.username().equalsIgnoreCase(playing))
                .anyMatch(LauncherAccount::offline);
        if (offline) return;

        lastAttempt = now;
        SpaceClient.LOGGER.info("Left a server - refreshing the session in case it expired");
        SessionManager.refreshCurrent();
    }

    private SessionWatcher() {}
}
