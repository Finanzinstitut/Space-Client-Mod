package gg.spaceclient.music;

import gg.spaceclient.SpaceClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Asks Windows itself what is playing, through the media transport controls.
 *
 * This is the same information the volume overlay shows: every app that plays
 * audio registers a session with a title, an artist and the app it belongs to.
 *
 * It exists because reading window titles only works for players that put the
 * track there. Spotify does; Amazon Music's newer app does not - its window is
 * simply called "Amazon Music" whatever is playing, which is why it looked
 * permanently idle. The session list has the track regardless.
 */
public final class MediaSession {

    /** Why the last lookup failed, for the diagnostics page. */
    private static volatile String status = "not run yet";

    /**
     * Where the current track is, in seconds, or -1 when Windows did not say.
     *
     * Reported separately from NowPlaying on purpose: a track that plays is
     * useful on its own, and this may well be missing. Whether it is here at
     * all decides whether a synced lyric line is even possible - without a
     * position there is nothing to sync to.
     */
    private static volatile double position = -1;
    private static volatile double duration = -1;

    /** When the position above was measured, so it can be advanced between polls. */
    private static volatile long measuredAt = 0;

    /** Whether the track was playing at that reading - a paused one stands still. */
    private static volatile boolean running = false;

    /** The previous reading, to tell a running timeline from a frozen one. */
    private static volatile double previousPosition = -1;
    private static volatile long previousAt = 0;
    private static volatile int stuckReadings = 0;

    public static String status() { return status; }

    /**
     * The playback position right now, in seconds, or -1 when unknown.
     *
     * Windows only updates the timeline when the player pushes it, which can be
     * seconds apart. So the last reading is carried forward by the time since
     * it was taken - close enough for picking a lyric line, and far better than
     * a value that jumps in steps.
     */
    public static double position() {
        if (position < 0 || stuckReadings >= 3) return -1;
        if (!running) return position;
        double now = position + (System.currentTimeMillis() - measuredAt) / 1000.0;
        // Past the end means the next track has started and the next poll has
        // not seen it yet; holding at the end is closer than running on.
        return duration > 0 ? Math.min(now, duration) : now;
    }

    public static double duration() { return duration; }

    /** What the timeline lookup produced, in words, for the diagnostics page. */
    public static String timelineStatus() {
        if (position < 0) return "no position reported";
        if (stuckReadings >= 3) {
            return "position reported but frozen - unusable for syncing";
        }
        return String.format("%.0fs of %.0fs", position(), duration);
    }

    /** Which apps count. Anything else, a browser included, is ignored. */
    private static boolean isWanted(String appId) {
        String lower = appId.toLowerCase();
        return lower.contains("spotify") || lower.contains("amazon");
    }

    private static String sourceOf(String appId) {
        return appId.toLowerCase().contains("spotify") ? "Spotify" : "Amazon Music";
    }

    /**
     * @return what is playing, or null when the lookup could not run at all
     *
     * The difference matters more than it looks. Null sends the caller to the
     * window title fallback; NOTHING tells it the interface answered and there
     * really is no music. Conflating the two was the bug that made this work
     * only sometimes: a failed WinRT call came back looking exactly like
     * silence, the fallback never ran, and the overlay went blank until the
     * next poll happened to succeed.
     */
    public static NowPlaying read() {
        // The running host first: no process to start, so nothing to feel.
        // Only when it cannot be used does this start a script of its own.
        java.util.List<String> fromHost = MusicHost.read();
        if (fromHost != null) {
            // From the host an empty list is a real answer - no player has a
            // session - not the silently broken call it can be from a script
            if (fromHost.isEmpty()) {
                status = "ok";
                return NowPlaying.NOTHING;
            }
            return parse(String.join("\n", fromHost), 0);
        }

        Path script = scriptFile();
        if (script == null) {
            status = "script could not be written";
            return null;
        }

        try {
            Process process = new ProcessBuilder(
                    "powershell", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-File", script.toString())
                    .redirectErrorStream(true)
                    .start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            int exit = process.waitFor();
            return parse(output.toString(), exit);

        } catch (Throwable t) {
            status = "session lookup threw: " + t.getMessage();
            SpaceClient.LOGGER.warn("Media session lookup failed: {}", t.getMessage());
            return null;
        }
    }

    /** Turns the lines either source wrote into a track; see read() for null. */
    private static NowPlaying parse(String text, int exit) {
        try {

            // The script reports its own failures on a line of this shape
            if (text.contains("ERROR|")) {
                int start = text.indexOf("ERROR|") + 6;
                int end = text.indexOf('\n', start);
                status = "session interface failed: "
                        + text.substring(start, end < 0 ? text.length() : end).trim();
                return null;
            }

            if (exit != 0) {
                status = "session lookup exited " + exit;
                return null;
            }

            if (text.isBlank()) {
                // No sessions at all reads the same as a silently broken call,
                // so this goes to the fallback rather than claiming silence
                status = "session list came back empty";
                return null;
            }

            NowPlaying paused = null;

            for (String line : text.split("\n")) {
                // appId | status | artist | title | position | duration
                String[] parts = line.split("\\|", -1);
                if (parts.length < 4) continue;

                String appId = parts[0].trim();
                String playback = parts[1].trim();
                String artist = parts[2].trim();
                String title = parts[3].trim();

                if (title.isEmpty() || !isWanted(appId)) continue;

                NowPlaying track = new NowPlaying(
                        sourceOf(appId), artist, title, playback.equalsIgnoreCase("Playing"));

                // A playing session wins; a paused one is kept in case nothing
                // is actually playing right now.
                if (track.playing()) {
                    readTimeline(parts, true);
                    return track;
                }
                if (paused == null) {
                    paused = track;
                    readTimeline(parts, false);
                }
            }

            status = "ok";
            return paused != null ? paused : NowPlaying.NOTHING;

        } catch (Throwable t) {
            status = "session lookup threw: " + t.getMessage();
            SpaceClient.LOGGER.warn("Media session lookup failed: {}", t.getMessage());
            return null;
        }
    }

    /**
     * Picks the timeline fields out of a line, if the script managed to read them.
     *
     * The position Windows hands out is not "now". It is where the player was
     * the last time it pushed an update, and players push rarely - Spotify
     * mostly on play, pause and seek. Taken at face value it lagged by however
     * long ago that was, which is why lyrics ran seconds behind the song, and
     * because it stood still between pushes it also looked frozen and was
     * thrown away, which is why they sometimes did not show at all. The script
     * now reports how old the reading is; a playing track is moved on by that,
     * and the moment of measuring is the script's clock rather than whenever
     * Java got round to parsing the output.
     */
    private static void readTimeline(String[] parts, boolean playing) {
        try {
            if (parts.length < 6) {
                position = -1;
                duration = -1;
                return;
            }
            double raw = Double.parseDouble(parts[4].trim());
            double length = Double.parseDouble(parts[5].trim());

            // A track with no length is a player that fills the fields in
            // without meaning them. Better to call that unknown than to hand
            // out a position with nothing to measure it against.
            if (length <= 0 || raw < 0) {
                position = -1;
                duration = -1;
                return;
            }

            long now = System.currentTimeMillis();
            long taken = now;
            double age = 0;
            if (parts.length >= 8) {
                age = Double.parseDouble(parts[6].trim()) / 1000.0;
                long scriptNow = Long.parseLong(parts[7].trim());
                // The script's clock is this machine's clock; anything far off
                // is a parse of something else, and "now" is the safer guess
                if (Math.abs(now - scriptNow) < 30_000) taken = scriptNow;
                // A negative or absurd age would push the position anywhere
                if (age < 0 || age > 24 * 3600) age = 0;
            }

            double current = playing ? raw + age : raw;
            if (current > length) current = length;

            // Some players report a timeline that never moves, and moving it on
            // by its age does not help when the age is always zero. So a
            // playing track whose position keeps not advancing is treated as
            // having no timeline at all.
            if (playing && previousPosition >= 0 && previousAt > 0) {
                double elapsed = (taken - previousAt) / 1000.0;
                double moved = current - previousPosition;
                if (elapsed > 1.0 && Math.abs(moved) < elapsed * 0.25) {
                    if (stuckReadings < 10) stuckReadings++;
                } else {
                    stuckReadings = 0;
                }
            } else {
                stuckReadings = 0;
            }
            previousPosition = current;
            previousAt = taken;

            position = current;
            duration = length;
            running = playing;
            measuredAt = taken;

        } catch (Throwable ignored) {
            // A player that reports no timeline is normal, not an error
            position = -1;
            duration = -1;
        }
    }

    /**
     * Writes the query script once and reuses it.
     *
     * The transport controls are a WinRT interface, and its calls are
     * asynchronous. PowerShell has no await, so the returned operation is turned
     * into a Task through the runtime extensions and waited on - the usual way
     * of reaching WinRT from a script.
     */
    private static Path scriptFile() {
        try {
            // Versioned: the file is cached in temp and only written when missing,
            // so a fixed script would never reach a machine that already had the
            // broken one. Bump this whenever the script below changes.
            Path path = Path.of(System.getProperty("java.io.tmpdir"), "spaceclient-media-5.ps1");
            if (Files.exists(path)) return path;

            String script = String.join("\n",
                    "param([string]$thumb = '')",
                    "$ErrorActionPreference = 'Stop'",
                    "try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }",
                    "try {",
                    "  Add-Type -AssemblyName System.Runtime.WindowsRuntime",
                    "  $asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() |",
                    "    Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and",
                    "      $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]",
                    "",
                    "  function Await($operation, $resultType) {",
                    "    $method = $asTask.MakeGenericMethod($resultType)",
                    "    $task = $method.Invoke($null, @($operation))",
                    "    if (-not $task.Wait(4000)) { throw 'media session call timed out' }",
                    "    $task.Result",
                    "  }",
                    "",
                    "  $managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager," +
                            "Windows.Media.Control,ContentType=WindowsRuntime]",
                    "  $manager = Await ($managerType::RequestAsync()) ([Windows.Media.Control." +
                            "GlobalSystemMediaTransportControlsSessionManager])",
                    "",
                    "  if ($thumb -ne '') {",
                    "    # Cover mode: the picture of the session that is playing,",
                    "    # written to the given file. One call per song, not per poll.",
                    "    $null = [Windows.Storage.Streams.DataReader,Windows.Storage.Streams,ContentType=WindowsRuntime]",
                    "    $pick = $null",
                    "    foreach ($session in $manager.GetSessions()) {",
                    "      $id = $session.SourceAppUserModelId.ToLower()",
                    "      if (-not ($id.Contains('spotify') -or $id.Contains('amazon'))) { continue }",
                    "      if ($pick -eq $null) { $pick = $session }",
                    "      if ($session.GetPlaybackInfo().PlaybackStatus -eq 'Playing') { $pick = $session; break }",
                    "    }",
                    "    if ($pick -eq $null) { Write-Output 'THUMB|none'; return }",
                    "    $props = Await ($pick.TryGetMediaPropertiesAsync()) " +
                            "([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])",
                    "    if ($props.Thumbnail -eq $null) { Write-Output 'THUMB|none'; return }",
                    "    $stream = Await ($props.Thumbnail.OpenReadAsync()) " +
                            "([Windows.Storage.Streams.IRandomAccessStreamWithContentType])",
                    "    $size = [uint32]$stream.Size",
                    "    if ($size -le 0) { Write-Output 'THUMB|none'; return }",
                    "    $reader = [Windows.Storage.Streams.DataReader]::new($stream.GetInputStreamAt(0))",
                    "    $null = Await ($reader.LoadAsync($size)) ([uint32])",
                    "    $bytes = New-Object byte[] $size",
                    "    $reader.ReadBytes($bytes)",
                    "    [System.IO.File]::WriteAllBytes($thumb, $bytes)",
                    "    Write-Output ('THUMB|ok|' + $size)",
                    "    return",
                    "  }",
                    "",
                    "  foreach ($session in $manager.GetSessions()) {",
                    "    $appId = $session.SourceAppUserModelId",
                    "    $status = $session.GetPlaybackInfo().PlaybackStatus",
                    "    $properties = Await ($session.TryGetMediaPropertiesAsync()) " +
                            "([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])",
                    "",
                    "    # The timeline is optional - a player may report none at all,",
                    "    # so this must never take the whole line down with it",
                    "    $pos = -1",
                    "    $dur = -1",
                    "    $age = 0",
                    "    try {",
                    "      $timeline = $session.GetTimelineProperties()",
                    "      if ($timeline -ne $null) {",
                    "        $pos = $timeline.Position.TotalSeconds",
                    "        $dur = $timeline.EndTime.TotalSeconds",
                    "        # How long ago the player last pushed that position",
                    "        $age = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() - " +
                            "$timeline.LastUpdatedTime.ToUnixTimeMilliseconds()",
                    "      }",
                    "    } catch { }",
                    "    $now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()",
                    "",
                    "    Write-Output (\"$appId|$status|\" + $properties.Artist + '|' + " +
                            "$properties.Title + '|' + $pos + '|' + $dur + '|' + $age + '|' + $now)",
                    "  }",
                    "} catch {",
                    "  Write-Output ('ERROR|' + $_.Exception.Message)",
                    "}");

            Files.writeString(path, script, StandardCharsets.UTF_8);
            return path;

        } catch (Throwable t) {
            SpaceClient.LOGGER.warn("Could not write the media session script: {}", t.getMessage());
            return null;
        }
    }

    /**
     * Writes the cover of the playing track to a file.
     *
     * @return true if a picture was written
     */
    public static boolean thumbnail(Path target) {
        try {
            String reply = MusicHost.thumbnail(target);
            if (reply != null) {
                if (reply.startsWith("THUMB|ok")) {
                    coverStatus = "ok";
                    return Files.isRegularFile(target) && Files.size(target) > 0;
                }
                coverStatus = reply.startsWith("THUMB|none")
                        ? "player offers no cover" : "cover lookup failed: " + reply;
                return false;
            }
        } catch (Throwable ignored) {
            // Falls through to the one-off script
        }

        Path script = scriptFile();
        if (script == null) return false;

        try {
            Files.deleteIfExists(target);
            Process process = new ProcessBuilder(
                    "powershell", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-File", script.toString(),
                    "-thumb", target.toString())
                    .redirectErrorStream(true)
                    .start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) output.append(line).append('\n');
            }
            process.waitFor();

            String text = output.toString();
            if (text.contains("ERROR|")) {
                coverStatus = "cover lookup failed: " + text.substring(text.indexOf("ERROR|") + 6).trim();
                return false;
            }
            if (!text.contains("THUMB|ok")) {
                coverStatus = "player offers no cover";
                return false;
            }
            coverStatus = "ok";
            return Files.isRegularFile(target) && Files.size(target) > 0;

        } catch (Throwable t) {
            coverStatus = "cover lookup threw: " + t.getMessage();
            return false;
        }
    }

    private static volatile String coverStatus = "not asked yet";

    /** What the last cover lookup did, for the diagnostics page. */
    public static String coverStatus() { return coverStatus; }

    private MediaSession() {}
}
