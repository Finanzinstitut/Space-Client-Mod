package gg.spaceclient.music;

import gg.spaceclient.SpaceClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Reads what the local Spotify or Amazon Music app is playing.
 *
 * Both apps put the current track in their window title, which is the only
 * thing available without signing into a web API. That has a deliberate side
 * effect: music playing in a browser tab is not picked up, because no such
 * process exists - which is exactly the wanted behaviour here.
 *
 * The lookup runs through PowerShell rather than a native library, so nothing
 * has to be shipped alongside the mod. It is therefore Windows only; on other
 * systems the module reports that and stays quiet.
 */
public final class MusicWatcher {
    /** Titles these apps show when nothing is playing. */
    private static final String[] IDLE_TITLES = {
            "spotify", "spotify premium", "spotify free", "amazon music", "advertisement"
    };

    private static final long POLL_MS = 2500;

    private static volatile NowPlaying current = NowPlaying.NOTHING;
    private static volatile boolean polling = false;
    private static volatile boolean supported = isWindows();
    private static volatile String status = supported ? "waiting" : "only available on Windows";

    private static long lastPoll = 0;

    public static NowPlaying current() { return current; }
    public static boolean isSupported() { return supported; }
    public static String status() { return status; }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /** Called every client tick; does its work at most every few seconds. */
    public static void tick() {
        if (!supported || polling) return;

        long now = System.currentTimeMillis();
        if (now - lastPoll < POLL_MS) return;
        lastPoll = now;

        polling = true;
        CompletableFuture.runAsync(() -> {
            try {
                current = read();
            } catch (Throwable t) {
                status = "lookup failed: " + t.getMessage();
                current = NowPlaying.NOTHING;
            } finally {
                polling = false;
            }
        });
    }

    /**
     * Asks Windows for the window titles of the two players.
     *
     * Only these two process names are considered, so a browser playing music
     * is ignored no matter what its tab is called.
     */
    private static NowPlaying read() throws Exception {
        String script =
                "Get-Process -Name 'Spotify','Amazon Music' -ErrorAction SilentlyContinue " +
                "| Where-Object { $_.MainWindowTitle -ne '' } " +
                "| ForEach-Object { $_.ProcessName + '|' + $_.MainWindowTitle }";

        ProcessBuilder builder = new ProcessBuilder(
                "powershell", "-NoProfile", "-NonInteractive", "-Command", script);
        builder.redirectErrorStream(true);

        Process process = builder.start();
        StringBuilder output = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        process.waitFor();

        for (String line : output.toString().split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || !trimmed.contains("|")) continue;

            String process_name = trimmed.substring(0, trimmed.indexOf('|')).trim();
            String windowTitle = trimmed.substring(trimmed.indexOf('|') + 1).trim();

            String source = process_name.equalsIgnoreCase("Spotify") ? "Spotify" : "Amazon Music";

            // An idle title means the app is open but paused or stopped
            boolean idle = false;
            for (String candidate : IDLE_TITLES) {
                if (windowTitle.equalsIgnoreCase(candidate)) idle = true;
            }
            if (idle) {
                status = source + " open, nothing playing";
                continue;
            }

            status = "reading " + source;
            return parse(source, windowTitle);
        }

        status = "no player running";
        return NowPlaying.NOTHING;
    }

    /**
     * Splits the window title into artist and track.
     *
     * Spotify writes "Artist - Title". Amazon Music has used both that order and
     * the reverse across versions, so the split is kept simple and the whole
     * title is shown when it does not match.
     */
    private static NowPlaying parse(String source, String windowTitle) {
        String cleaned = windowTitle;

        // Amazon Music appends its own name on some builds
        for (String suffix : new String[]{" - Amazon Music", " | Amazon Music"}) {
            if (cleaned.endsWith(suffix)) {
                cleaned = cleaned.substring(0, cleaned.length() - suffix.length());
            }
        }

        int separator = cleaned.indexOf(" - ");
        if (separator > 0 && separator < cleaned.length() - 3) {
            String left = cleaned.substring(0, separator).trim();
            String right = cleaned.substring(separator + 3).trim();
            return new NowPlaying(source, left, right, true);
        }
        return new NowPlaying(source, "", cleaned.trim(), true);
    }

    // ---------------- controls ----------------

    /** Virtual key codes for the media keys Windows listens to. */
    private static final int NEXT = 0xB0;
    private static final int PREVIOUS = 0xB1;
    private static final int PLAY_PAUSE = 0xB3;

    public static void next() { sendMediaKey(NEXT); }
    public static void previous() { sendMediaKey(PREVIOUS); }
    public static void playPause() { sendMediaKey(PLAY_PAUSE); }

    /**
     * Presses a media key system wide.
     *
     * Windows routes these to whichever app is currently the media session, so
     * the controls reach the player that is actually making sound rather than
     * needing to talk to it directly.
     */
    private static void sendMediaKey(int virtualKey) {
        if (!supported) return;

        CompletableFuture.runAsync(() -> {
            try {
                String script =
                        "$signature='[DllImport(\"user32.dll\")] public static extern void " +
                        "keybd_event(byte bVk, byte bScan, uint dwFlags, int dwExtraInfo);'; " +
                        "$type=Add-Type -MemberDefinition $signature -Name Keys -Namespace Media " +
                        "-PassThru; " +
                        "$type::keybd_event(" + virtualKey + ",0,0,0); " +
                        "$type::keybd_event(" + virtualKey + ",0,2,0)";

                new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script)
                        .start()
                        .waitFor();

                // Give the player a moment, then read the new track
                Thread.sleep(600);
                lastPoll = 0;

            } catch (Throwable t) {
                SpaceClient.LOGGER.warn("Media key failed: {}", t.getMessage());
            }
        });
    }

    private MusicWatcher() {}
}
