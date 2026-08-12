package gg.spaceclient.session;

import gg.spaceclient.SpaceClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;

/**
 * Notices when a join fails because the session went stale, and mints a new one
 * on the spot.
 *
 * There is no reliable way to read the disconnect reason without reaching into
 * the screen's private state, so this reacts to the situation instead: a
 * disconnect that happens while the stored token is past its lifetime is almost
 * always the session, and refreshing when it is not costs nothing but one HTTP
 * round trip.
 */
public class SessionWatcher {
    /** Do not retry more often than this, so a failing refresh cannot loop. */
    private static final long COOLDOWN_MS = 30_000;

    private static boolean wasDisconnected = false;
    private static long lastAttempt = 0;

    public static void tick(Minecraft client) {
        boolean disconnected = client.screen instanceof DisconnectedScreen;

        // Only act on the transition into the screen, not every frame it is up
        if (disconnected && !wasDisconnected) {
            onDisconnected();
        }
        wasDisconnected = disconnected;
    }

    private static void onDisconnected() {
        long now = System.currentTimeMillis();
        if (now - lastAttempt < COOLDOWN_MS) return;
        if (SessionManager.isBusy()) return;

        if (!LauncherAccounts.isAvailable()) return;

        // Offline profiles have nothing to refresh
        String playing = Minecraft.getInstance().getUser().getName();
        boolean offline = LauncherAccounts.load().stream()
                .filter(a -> a.username().equalsIgnoreCase(playing))
                .anyMatch(LauncherAccount::offline);
        if (offline) return;

        lastAttempt = now;
        SpaceClient.LOGGER.info("Disconnected - refreshing the session in case it expired");
        SessionManager.refreshCurrent();
    }

    private SessionWatcher() {}
}
