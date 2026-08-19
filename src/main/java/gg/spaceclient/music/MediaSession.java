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

    public static String status() { return status; }

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

            String text = output.toString();

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

            for (String line : output.toString().split("\n")) {
                // appId | status | artist | title
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
                if (track.playing()) return track;
                if (paused == null) paused = track;
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
            Path path = Path.of(System.getProperty("java.io.tmpdir"), "spaceclient-media-2.ps1");
            if (Files.exists(path)) return path;

            String script = String.join("\n",
                    "$ErrorActionPreference = 'Stop'",
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
                    "  foreach ($session in $manager.GetSessions()) {",
                    "    $appId = $session.SourceAppUserModelId",
                    "    $status = $session.GetPlaybackInfo().PlaybackStatus",
                    "    $properties = Await ($session.TryGetMediaPropertiesAsync()) " +
                            "([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])",
                    "    Write-Output (\"$appId|$status|\" + $properties.Artist + '|' + $properties.Title)",
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

    private MediaSession() {}
}
