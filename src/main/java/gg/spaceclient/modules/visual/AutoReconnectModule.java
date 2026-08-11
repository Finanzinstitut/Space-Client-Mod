package gg.spaceclient.modules.visual;

import gg.spaceclient.module.Category;
import gg.spaceclient.module.Module;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.IntSetting;

import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinJoinMultiplayerScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.ServerData;

/**
 * Reconnects after being disconnected.
 *
 * The sensible addition: exponential backoff with a cap and an attempt limit.
 * Clients that retry every five seconds forever hammer a server that is down
 * and get you rate-limited; this backs off and gives up rather than spamming.
 */
public class AutoReconnectModule extends Module {
    private final IntSetting firstDelay = new IntSetting(
            "delay", "First delay (seconds)", "Wait before the first retry", 5, 1, 60);

    private final IntSetting maxAttempts = new IntSetting(
            "max_attempts", "Max attempts", "Give up after this many tries", 5, 1, 30);

    private final BooleanSetting backoff = new BooleanSetting(
            "backoff", "Back off", "Double the wait after each failed attempt", true);

    private ServerData lastServer;
    private int attempts = 0;
    private long nextAttemptAt = 0;
    private int currentDelay;

    public AutoReconnectModule() {
        super("autoreconnect", "Auto Reconnect", "Reconnects to a server after being disconnected",
                Category.UTILITY);
        addSettings(firstDelay, maxAttempts, backoff);
    }

    @Override
    public void onTick() {
        // Remember the server while we are still on it
        if (mc.getCurrentServer() != null) {
            lastServer = mc.getCurrentServer();
            attempts = 0;
            currentDelay = firstDelay.get();
            nextAttemptAt = 0;
        }

        if (!(mc.screen instanceof DisconnectedScreen)) return;
        if (lastServer == null || attempts >= maxAttempts.get()) return;

        long now = System.currentTimeMillis();
        if (nextAttemptAt == 0) {
            currentDelay = currentDelay <= 0 ? firstDelay.get() : currentDelay;
            nextAttemptAt = now + currentDelay * 1000L;
            return;
        }

        if (now < nextAttemptAt) return;

        attempts++;
        if (backoff.get()) {
            currentDelay = Math.min(120, currentDelay * 2);
        }
        nextAttemptAt = 0;

        ServerData server = lastServer;
        ConnectScreen.connect(
                new JoinMultiplayerScreen(new TitleScreen()),
                mc,
                ServerAddress.parse(server.address),
                server,
                false,
                null
        );
    }

    @Override
    protected void onDisable() {
        attempts = 0;
        nextAttemptAt = 0;
    }
}
