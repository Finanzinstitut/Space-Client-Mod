package gg.spaceclient.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.modules.MusicModule;
import gg.spaceclient.music.NowPlaying;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Publishes the local track and keeps what other people are playing.
 *
 * Two timers, deliberately different: reporting only happens when the track
 * actually changed, or once a minute so the entry does not expire; looking up
 * runs on a slower beat, because a song lasting three minutes does not need to
 * be fetched every second.
 *
 * Nothing here runs while the setting is off, with one exception - the moment
 * it is switched off, one last call goes out to clear the entry. Otherwise the
 * last song anyone saw would keep hanging over the name until the worker's own
 * expiry caught up with it, which is not what switching a thing off means.
 */
public final class NowPlayingShare {

    /** How often the local track may be sent, at the very most. */
    private static final long REPORT_MIN_MS = 5_000;

    /** Sent again after this long even when nothing changed, to stay alive. */
    private static final long REPORT_KEEPALIVE_MS = 60_000;

    /** How often everyone else's tracks are fetched. */
    private static final long FETCH_MS = 10_000;

    /** The worker caps this too; asking for more would be pointless. */
    private static final int MAX_LOOKUP = 100;

    private static final Map<UUID, String> songs = new ConcurrentHashMap<>();

    private static volatile boolean reporting = false;
    private static volatile boolean fetching = false;

    private static long lastReport = 0;
    private static long lastFetch = 0;
    private static String lastReported = "";
    private static boolean wasSharing = false;

    /**
     * Whether your own track should be drawn over your own head.
     *
     * Read from the render thread every frame, so it goes through here rather
     * than making the mixin dig the module out of the manager itself.
     */
    public static boolean showOnSelf() {
        MusicModule module = module();
        return module != null && module.isEnabled()
                && module.sharesOverName() && module.showsOnSelf();
    }

    /** What the last lookup came back with, for the diagnostics page. */
    public static String cacheStatus() {
        Minecraft mc = Minecraft.getInstance();
        UUID self = mc.player != null ? mc.player.getUUID() : null;
        boolean mine = self != null && songs.containsKey(self);

        if (lastFetch == 0) return "not fetched yet";
        return songs.size() + " known, yours: " + (mine ? "yes" : "no");
    }

    /** What the local track last sent was, for the diagnostics page. */
    public static String reportStatus() {
        if (lastReport == 0) return "nothing sent yet";
        return lastReported.isEmpty() ? "sent: (cleared)" : "sent: " + lastReported;
    }

    /** What this player is playing, or null. Called from the render thread. */
    public static String songFor(UUID uuid) {
        return uuid == null ? null : songs.get(uuid);
    }

    /** Called every client tick. */
    public static void tick() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) {
                songs.clear();
                return;
            }

            MusicModule module = module();
            boolean sharing = module != null && module.isEnabled() && module.sharesOverName();

            if (sharing) {
                maybeReport(module);
            } else if (wasSharing) {
                // Switched off - take the entry down rather than let it linger
                wasSharing = false;
                lastReported = "";
                clearRemote();
            }
            wasSharing = sharing;

            maybeFetch(mc);

        } catch (Throwable t) {
            // A sharing problem must never disturb the tick loop
            SpaceClient.LOGGER.warn("Now playing share failed: {}", t.getMessage());
        }
    }

    private static MusicModule module() {
        var manager = SpaceClient.getModuleManager();
        if (manager == null) return null;
        return manager.get("music") instanceof MusicModule music ? music : null;
    }

    private static void maybeReport(MusicModule module) {
        if (reporting) return;

        long now = System.currentTimeMillis();
        if (now - lastReport < REPORT_MIN_MS) return;

        NowPlaying playing = module.track();
        String line = playing.isEmpty() ? "" : playing.display();

        boolean changed = !line.equals(lastReported);
        boolean stale = now - lastReport > REPORT_KEEPALIVE_MS;
        if (!changed && !stale) return;

        lastReport = now;
        lastReported = line;
        reporting = true;

        CompletableFuture.runAsync(() -> {
            try {
                SpaceApi.report(playing.source(), playing.artist(),
                        playing.title(), playing.playing());
            } finally {
                reporting = false;
            }
        });
    }

    private static void clearRemote() {
        CompletableFuture.runAsync(() -> SpaceApi.report("", "", "", false));
    }

    private static void maybeFetch(Minecraft mc) {
        if (fetching) return;

        long now = System.currentTimeMillis();
        if (now - lastFetch < FETCH_MS) return;
        lastFetch = now;

        List<UUID> wanted = new ArrayList<>();

        // Deliberately fetched rather than read from the local module: the
        // point of the self setting is to prove the round trip, and taking a
        // shortcut here would prove nothing.
        if (showOnSelf() && mc.player.getUUID() != null) {
            wanted.add(mc.player.getUUID());
        }

        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            UUID uuid = player.getUUID();
            if (uuid != null) wanted.add(uuid);
            if (wanted.size() >= MAX_LOOKUP) break;
        }

        if (wanted.isEmpty()) {
            songs.clear();
            return;
        }

        fetching = true;
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject answer = SpaceApi.songsFor(wanted);
                if (answer == null) return;

                // Replaced wholesale rather than merged: someone who stopped
                // sharing has to disappear, and a merge would keep them.
                Map<UUID, String> fresh = new ConcurrentHashMap<>();
                for (Map.Entry<String, JsonElement> entry : answer.entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(entry.getKey());
                        String line = entry.getValue().getAsString();
                        if (!line.isEmpty()) fresh.put(uuid, line);
                    } catch (Throwable ignored) {
                        // Skip anything malformed rather than lose the batch
                    }
                }

                songs.clear();
                songs.putAll(fresh);

            } catch (Throwable t) {
                SpaceClient.LOGGER.warn("Could not fetch tracks: {}", t.getMessage());
            } finally {
                fetching = false;
            }
        });
    }

    private NowPlayingShare() {}
}
