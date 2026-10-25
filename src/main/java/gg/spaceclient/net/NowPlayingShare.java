package gg.spaceclient.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.modules.MusicModule;
import gg.spaceclient.music.Lyrics;
import gg.spaceclient.music.MediaSession;
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
 * Built so the free plan of the server it runs on lasts. It used to send the
 * position every few seconds - every small hiccup in the position Windows
 * reports looked like a jump and went out as one - and two people listening
 * for two hours used up a day's allowance. Now a song goes out once: its
 * title, how long it is, where it is and whether it plays. Everyone else works
 * the position out from that themselves for as long as the song plays, so
 * the next message is only due when something they could not have worked out
 * happens: the next song, a pause, a real jump.
 *
 * Looking up runs on a slow beat, plus right after a known song should have
 * ended - which is when the next one is to be expected - and soon after
 * somebody new comes into view.
 *
 * Nothing here runs while the setting is off, with one exception - the moment
 * it is switched off, one last call goes out to clear the entry.
 */
public final class NowPlayingShare {

    /** A song change waits this long, so skipping through three is one message. */
    private static final long REPORT_CHANGE_MS = 1_500;

    /** A jump is only believed when it has held for this long. */
    private static final long JUMP_CONFIRM_MS = 4_000;

    /** And at most one jump correction goes out in this time. */
    private static final long JUMP_EVERY_MS = 30_000;

    /** How far off the worked-out position may be before it counts as a jump. */
    private static final double JUMP_SECONDS = 3.0;

    /** A song of unknown length expires on the server; sent again before that. */
    private static final long REPORT_UNKNOWN_LENGTH_MS = 10 * 60_000;

    /** How often everyone else's songs are looked up. */
    private static final long FETCH_MS = 60_000;

    /** The same, while lyrics are on: a pause somebody else makes shows sooner. */
    private static final long FETCH_LYRICS_MS = 20_000;

    /** Soonest after the last lookup that a newcomer or a song end may ask again. */
    private static final long FETCH_SOON_MS = 3_000;

    /** How long after a song should have ended its successor is looked up. */
    private static final long FETCH_AFTER_END_MS = 2_000;

    /** Who the last lookup covered, so an arrival can be told from a regular. */
    private static final java.util.Set<UUID> asked =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** The worker caps this too; asking for more would be pointless. */
    private static final int MAX_LOOKUP = 100;

    private static final Map<UUID, String> songs = new ConcurrentHashMap<>();

    /**
     * What each visible player is playing, and where they are in it.
     *
     * The position is the point that matters. It arrives every ten seconds but
     * is carried forward locally in between, so the lyric line advances every
     * frame rather than lurching once per poll.
     */
    private record Remote(String artist, String title, String source, double position,
                          boolean playing, double duration, long receivedAt) {

        /** When this song should end, by the local clock, or 0 when unknown. */
        long endsAt() {
            if (!playing || duration <= 0 || position < 0) return 0;
            return receivedAt + (long) ((duration - position) * 1000);
        }
    }

    private static final Map<UUID, Remote> remotes = new ConcurrentHashMap<>();

    private static volatile boolean reporting = false;
    private static volatile boolean fetching = false;

    private static long lastReport = 0;
    private static long lastFetch = 0;
    private static String lastReported = "";
    private static double lastPosition = -1;
    private static boolean lastPlaying = false;
    private static boolean wasSharing = false;

    /** When the song on show first differed from what was last sent. */
    private static long changeSeen = 0;
    /** When the position first disagreed with what listeners work out. */
    private static long jumpSeen = 0;
    private static long lastJumpReport = 0;
    /** How many messages went out, for the diagnostics page. */
    private static int reportsSent = 0;
    private static int lookups = 0;

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

    private static int hookLong = 0;
    private static int hookShort = 0;
    private static int hookDrawn = 0;

    /**
     * Called by the name tag mixin every time it runs.
     *
     * The counters live here rather than in the mixin because mixin classes are
     * consumed by the transformer and never loaded as ordinary classes - so
     * calling one from mod code throws NoClassDefFoundError. The mixin may call
     * into here; nothing may call into the mixin.
     */
    public static void noteHook(boolean longOverload, boolean didDraw) {
        if (longOverload) hookLong++; else hookShort++;
        if (didDraw) hookDrawn++;
    }

    /** Whether the long overload has ever run, so the short one can stand down. */
    public static boolean longHookSeen() {
        return hookLong > 0;
    }

    /** What the name tag hook has been doing, for the diagnostics page. */
    public static String hookStatus() {
        if (hookLong == 0 && hookShort == 0) return "never fired";
        return "fired " + (hookLong + hookShort) + "x (long " + hookLong
                + ", short " + hookShort + "), drew " + hookDrawn;
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
        String counts = " (" + reportsSent + " sent, " + lookups + " lookups; " + Friends.songStatus() + ")";
        if (lastReport == 0) return "nothing sent yet" + counts;
        return (lastReported.isEmpty() ? "sent: (cleared)" : "sent: " + lastReported) + counts;
    }

    /**
     * The lyric path, step by step, for the diagnostics page.
     *
     * Five things have to line up for a line to appear, and from the outside
     * they all fail the same way. This says which one gave out.
     */
    public static String lyricStatus() {
        if (!showsLyrics()) return "setting is off";

        Minecraft mc = Minecraft.getInstance();
        UUID self = mc.player != null ? mc.player.getUUID() : null;

        if (remotes.isEmpty()) return "no positions received (" + songs.size() + " songs)";

        Remote remote = self != null ? remotes.get(self) : null;
        if (remote == null) {
            return remotes.size() + " with position, none of them you";
        }
        if (remote.position() < 0) return "your position never arrived";

        double position = remote.playing()
                ? remote.position() + (System.currentTimeMillis() - remote.receivedAt()) / 1000.0
                : remote.position();

        String line = Lyrics.line(remote.artist(), remote.title(), position);
        return String.format("%.0fs -> %s", position,
                line.isEmpty() ? "(no line at this point)" : line);
    }

    /** What this player is playing, or null. Called from the render thread. */
    public static String songFor(UUID uuid) {
        return uuid == null ? null : songs.get(uuid);
    }

    /**
     * The lyric line for a player, or null.
     *
     * Both sides have to want this: the other player has to be sharing a line,
     * and you have to have the setting on yourself. Somebody else's choice to
     * broadcast lyrics does not put them on your screen.
     */
    public static String lyricFor(UUID uuid) {
        if (uuid == null || !showsLyrics()) return null;

        Remote remote = remotes.get(uuid);
        if (remote == null || remote.title().isEmpty()) return null;

        // A paused track stays where it was left; a playing one has moved on
        // by however long ago this reading arrived
        double position = remote.playing()
                ? remote.position() + (System.currentTimeMillis() - remote.receivedAt()) / 1000.0
                : remote.position();

        return Lyrics.line(remote.artist(), remote.title(), position);
    }

    /** The same, with the word being sung. */
    public static Lyrics.Moment momentFor(UUID uuid) {
        if (uuid == null || !showsLyrics()) return Lyrics.Moment.NONE;
        Remote remote = remotes.get(uuid);
        if (remote == null || remote.title().isEmpty() || remote.position() < 0) return Lyrics.Moment.NONE;
        double position = remote.playing()
                ? remote.position() + (System.currentTimeMillis() - remote.receivedAt()) / 1000.0
                : remote.position();
        return Lyrics.moment(remote.artist(), remote.title(), position);
    }

    /**
     * Everything the card over a player's head shows, or null when that player
     * shares no song. Called from the render thread.
     */
    public static gg.spaceclient.render.SongTag.Card cardFor(UUID uuid) {
        if (uuid == null) return null;

        // Your own card is built from what is playing here, not from the
        // round trip through the song server. That round trip came back empty
        // whenever the server was slow or unreachable, and the card over your
        // own head flickered and then disappeared.
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && uuid.equals(mc.player.getUUID())) {
            MusicModule module = module();
            if (module == null || !module.isEnabled()) return null;
            NowPlaying own = module.track();
            if (own.isEmpty()) return null;
            Lyrics.Moment lyric = Lyrics.Moment.NONE;
            if (module.showsLyrics()) {
                double position = MediaSession.position();
                if (position >= 0) lyric = Lyrics.moment(own.artist(), own.title(), position);
            }
            return new gg.spaceclient.render.SongTag.Card(own.title(), own.artist(), own.source(), lyric);
        }

        String song = songs.get(uuid);
        if (song == null || song.isEmpty()) return null;

        Remote remote = remotes.get(uuid);
        Lyrics.Moment lyric = momentFor(uuid);

        if (remote == null || remote.title().isEmpty()) {
            // Only the combined line is known: shown as the title
            return new gg.spaceclient.render.SongTag.Card(song, "", "", lyric);
        }
        return new gg.spaceclient.render.SongTag.Card(
                remote.title(), remote.artist(), remote.source(), lyric);
    }

    /** Whether this player wants lyrics at all. */
    public static boolean showsLyrics() {
        MusicModule module = module();
        return module != null && module.isEnabled() && module.showsLyrics();
    }

    /** Called every client tick. */
    public static void tick() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) {
                songs.clear();
                remotes.clear();
                return;
            }

            MusicModule module = module();
            boolean sharing = module != null && module.isEnabled() && module.sharesOverName();

            // Fetched for the local track whenever the setting is on, whether
            // or not anything is being shared - the HUD wants the line too
            if (module == null || !module.isEnabled() || !module.showsLyrics()) {
                Lyrics.clear();
            } else {
                // Nothing used to start a lookup except drawing somebody's name
                // tag, so switching lyrics on while alone did nothing at all and
                // the diagnostics line stayed on "off". The local track is reason
                // enough to fetch: the HUD wants the line whether or not anyone
                // else is around to show it to.
                NowPlaying own = module.track();
                if (!own.isEmpty()) {
                    Lyrics.line(own.artist(), own.title(), MediaSession.position());
                }
            }

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
        NowPlaying playing = module.track();
        String line = playing.isEmpty() ? "" : playing.display();
        double position = MediaSession.position();
        double duration = MediaSession.duration();

        // A new song, or play/pause: news, after a moment to settle
        boolean changed = !line.equals(lastReported) || playing.playing() != lastPlaying;
        if (changed) {
            if (changeSeen == 0) changeSeen = now;
            if (now - changeSeen < REPORT_CHANGE_MS) return;
        } else {
            changeSeen = 0;
        }

        // Where listeners believe this song is, worked out from the last message
        double expected = lastPosition < 0 ? -1
                : lastPosition + (lastPlaying ? (now - lastReport) / 1000.0 : 0);

        boolean jumped = false;
        if (!changed && position >= 0 && expected >= 0
                && Math.abs(position - expected) > JUMP_SECONDS) {
            // Believed only once it has held for a while: the position Windows
            // hands out stalls and catches up, and each of those used to go
            // out as a jump - which is what used up the server's allowance
            if (jumpSeen == 0) jumpSeen = now;
            jumped = now - jumpSeen >= JUMP_CONFIRM_MS && now - lastJumpReport >= JUMP_EVERY_MS;
        } else {
            jumpSeen = 0;
        }

        // The first position after a song change, so listeners can show lines
        boolean positionArrived = !changed && lastPosition < 0 && position >= 0;

        // Only a song of unknown length has to be said again before it expires
        boolean refresh = !line.isEmpty() && !(duration > 0)
                && now - lastReport > REPORT_UNKNOWN_LENGTH_MS;

        if (!changed && !jumped && !positionArrived && !refresh) return;

        lastReport = now;
        lastReported = line;
        lastPosition = position;
        lastPlaying = playing.playing();
        changeSeen = 0;
        jumpSeen = 0;
        if (jumped) lastJumpReport = now;
        reporting = true;
        reportsSent++;

        CompletableFuture.runAsync(() -> {
            try {
                if (line.isEmpty()) {
                    Friends.setSong("", "", "", false, -1, 0);
                } else {
                    Friends.setSong(playing.source(), playing.artist(), playing.title(),
                            playing.playing(), position, duration);
                }
            } finally {
                reporting = false;
            }
        });
    }

    private static void clearRemote() {
        CompletableFuture.runAsync(() -> Friends.setSong("", "", "", false, -1, 0));
    }

    private static void maybeFetch(Minecraft mc) {
        if (fetching) return;

        long now = System.currentTimeMillis();

        List<UUID> wanted = new ArrayList<>();

        // Deliberately fetched rather than read from the local module: the
        // point of the self setting is to prove the round trip, and taking a
        // shortcut here would prove nothing.
        // Your own card is built locally (see cardFor), so it is not asked for

        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            UUID uuid = player.getUUID();
            if (uuid != null) wanted.add(uuid);
            if (wanted.size() >= MAX_LOOKUP) break;
        }

        if (wanted.isEmpty()) {
            songs.clear();
            remotes.clear();
            asked.clear();
            return;
        }

        // Somebody who walked into view a moment ago used to wait out the whole
        // ten second cycle before anyone could see their song, which is most of
        // why this looked like it simply did not work. An unfamiliar player
        // brings the next lookup forward instead.
        boolean newcomer = false;
        for (UUID uuid : wanted) {
            if (!asked.contains(uuid)) { newcomer = true; break; }
        }

        // A song somebody here was playing should have ended: the next one is
        // what they are listening to now, and worth asking for straight away
        boolean songEnded = false;
        for (Remote remote : remotes.values()) {
            long end = remote.endsAt();
            if (end > 0 && end > lastFetch && now >= end + FETCH_AFTER_END_MS) { songEnded = true; break; }
        }

        long wait = newcomer || songEnded ? FETCH_SOON_MS
                : showsLyrics() ? FETCH_LYRICS_MS : FETCH_MS;
        if (now - lastFetch < wait) return;
        lookups++;

        lastFetch = now;
        asked.clear();
        asked.addAll(wanted);

        fetching = true;
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject answer = Friends.songsFor(wanted);
                if (answer == null) return;

                // Replaced wholesale rather than merged: someone who stopped
                // sharing has to disappear, and a merge would keep them.
                Map<UUID, String> fresh = new ConcurrentHashMap<>();
                Map<UUID, Remote> freshRemotes = new ConcurrentHashMap<>();
                long arrived = System.currentTimeMillis();

                for (Map.Entry<String, JsonElement> entry : answer.entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(entry.getKey());
                        JsonObject value = entry.getValue().getAsJsonObject();

                        String title = value.has("title") ? value.get("title").getAsString() : "";
                        if (title.isEmpty()) continue;
                        String artist = value.has("artist") ? value.get("artist").getAsString() : "";
                        fresh.put(uuid, artist.isEmpty() ? title : artist + " - " + title);

                        // The worker reports how long ago the position was
                        // taken, so a reading that sat in storage for a while
                        // is not treated as if it had just been measured
                        double position = value.has("position")
                                ? value.get("position").getAsDouble() : -1;
                        double age = value.has("age") ? value.get("age").getAsDouble() : 0;
                        boolean isPlaying = value.has("playing")
                                && value.get("playing").getAsBoolean();

                        // A record written by an older worker has no usable
                        // timestamp, and NaN would poison every sum after it
                        if (!Double.isFinite(age)) age = 0;
                        if (!Double.isFinite(position)) position = -1;

                        double duration = value.has("duration") ? value.get("duration").getAsDouble() : 0;
                        if (!Double.isFinite(duration)) duration = 0;

                        // The worker says how long ago the position was true;
                        // a paused song has stayed where it was
                        double current = position < 0 ? -1 : isPlaying ? position + age : position;
                        freshRemotes.put(uuid, new Remote(
                                artist,
                                title,
                                value.has("source") && !value.get("source").isJsonNull()
                                        ? value.get("source").getAsString() : "",
                                current,
                                isPlaying,
                                duration,
                                arrived));

                    } catch (Throwable ignored) {
                        // Skip anything malformed rather than lose the batch
                    }
                }

                songs.clear();
                songs.putAll(fresh);
                remotes.clear();
                remotes.putAll(freshRemotes);

            } catch (Throwable t) {
                SpaceClient.LOGGER.warn("Could not fetch tracks: {}", t.getMessage());
            } finally {
                fetching = false;
            }
        });
    }

    private NowPlayingShare() {}
}
