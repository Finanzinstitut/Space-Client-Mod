package gg.spaceclient.net;

import gg.spaceclient.SpaceClient;

import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is running Space Client, so the name tag can say so.
 *
 * Deliberately built the opposite way round from NowPlayingShare, and the
 * reason is the free KV quota rather than taste.
 *
 * NowPlayingShare asks the worker about the specific players it can see, every
 * ten seconds, because a song changes constantly and only matters for people
 * in view. Badge membership does neither: it changes when somebody installs
 * the client, which is roughly never, and it is the same answer for everybody.
 * So this fetches the whole roster instead - one request, one cached answer -
 * and every "does this player have a badge" check afterwards is a set lookup
 * with no network and no storage behind it.
 *
 * That distinction is what keeps the badge off the KV bill. Per-player polling
 * at now-playing rates would cost one list operation per client per poll, and
 * the free tier allows a thousand a day in total.
 *
 * The roster is not cleared on disconnect. It is not per world, and a player
 * who rejoins two minutes later would otherwise pay for a fresh fetch to learn
 * exactly what it already knew.
 */
public final class Presence {

    /**
     * How often this client re-announces itself.
     *
     * Twelve hours, not minutes. The worker keeps an entry for thirty days and
     * refreshes its own timestamp on contact, so announcing more often buys
     * nothing and costs a KV write every time. The worker also declines to
     * rewrite an entry it saw recently, so a player who restarts the game
     * repeatedly still only costs one write a day.
     */
    private static final long REGISTER_EVERY_MS = 12 * 60 * 60 * 1000L;

    /**
     * How often the roster is refreshed.
     *
     * Half an hour. Someone who installs the client mid-session waits up to
     * that long before other people see their badge, which is a fair trade for
     * a list operation that would otherwise run every few seconds per player.
     */
    private static final long FETCH_EVERY_MS = 30 * 60 * 1000L;

    /** Retry sooner than the full interval when a call did not get through. */
    private static final long RETRY_MS = 2 * 60 * 1000L;

    private static final Set<UUID> badged = ConcurrentHashMap.newKeySet();

    private static volatile boolean registering = false;
    private static volatile boolean fetching = false;

    private static volatile long nextRegister = 0;
    private static volatile long nextFetch = 0;

    private static volatile boolean rosterLoaded = false;

    /**
     * Whether this player runs Space Client.
     *
     * Called from the render thread for every name tag on screen, so it does
     * nothing but read a set.
     */
    public static boolean hasBadge(UUID uuid) {
        return uuid != null && badged.contains(uuid);
    }

    /**
     * What the badge roster is doing, for the diagnostics page.
     *
     * Reports the network side through SpaceApi rather than a local copy, so a
     * registration that was refused says so here instead of being hidden
     * behind a roster fetch that technically succeeded - which is exactly how
     * an empty roster looked the first time round.
     */
    public static String status() {
        String detail = SpaceApi.badgeStatus();
        if (!rosterLoaded) return detail;
        return badged.size() + " with a badge (" + detail + ")";
    }

    /** Called every client tick. */
    public static void tick() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return;

            long now = System.currentTimeMillis();

            if (!registering && now >= nextRegister) {
                registering = true;
                // Set before the call, not after: a failure pushes it out by
                // the short retry rather than letting a broken worker be
                // hammered once per tick
                nextRegister = now + RETRY_MS;

                CompletableFuture.runAsync(() -> {
                    try {
                        if (SpaceApi.register()) {
                            long done = System.currentTimeMillis();
                            nextRegister = done + REGISTER_EVERY_MS;

                            // Refetch straight away if the roster does not yet
                            // list this account. Both timers start together, so
                            // on a first run the fetch usually finishes before
                            // the registration lands - without this the player
                            // who just installed the mod waits out the full
                            // half hour before their own badge appears.
                            Minecraft self = Minecraft.getInstance();
                            UUID mine = self.player != null ? self.player.getUUID() : null;
                            if (mine != null && !badged.contains(mine)) {
                                nextFetch = done;
                            }
                        }
                    } finally {
                        registering = false;
                    }
                });
            }

            if (!fetching && now >= nextFetch) {
                fetching = true;
                nextFetch = now + RETRY_MS;

                CompletableFuture.runAsync(() -> {
                    try {
                        List<String> users = SpaceApi.badgeUsers();
                        // Null means the call did not get through, which is not
                        // the same as an empty roster - keep what we had
                        if (users == null) return;

                        // Rebuilt rather than merged. Unlike a song, an absent
                        // entry here is meaningful: it is somebody whose
                        // registration expired, and they should lose the badge.
                        Set<UUID> fresh = ConcurrentHashMap.newKeySet();
                        for (String raw : users) {
                            try {
                                fresh.add(UUID.fromString(raw));
                            } catch (Throwable ignored) {
                                // One malformed id must not cost the roster
                            }
                        }

                        badged.clear();
                        badged.addAll(fresh);
                        rosterLoaded = true;
                        nextFetch = System.currentTimeMillis() + FETCH_EVERY_MS;

                    } finally {
                        fetching = false;
                    }
                });
            }

        } catch (Throwable t) {
            SpaceClient.LOGGER.warn("Presence failed: {}", t.getMessage());
        }
    }

    private Presence() {}
}
