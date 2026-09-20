package gg.spaceclient.modules;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.module.Module;
import gg.spaceclient.input.KeyBinds;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.IntSetting;
import gg.spaceclient.setting.KeySetting;
import gg.spaceclient.util.Screens;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Keep the last few seconds of play, and write them out on a key press.
 *
 * <h2>What this class is, and what it is not</h2>
 *
 * It is the switch and the key. It is not the recorder, and it cannot be: the
 * ask includes the whole computer's sound, and a mod runs inside the game's
 * JVM, where there is no way to hear what the speakers are playing - no
 * loopback exists in the Java audio API on any platform. Getting the picture
 * out is no better: reading the framebuffer back every frame costs more than
 * the feature is worth.
 *
 * The launcher is a native process that is already running alongside the game,
 * so it keeps FFmpeg recording a ring of short segments and stitches the last
 * few together when one is asked for. That leaves this class with two jobs,
 * both of which it can actually do: say whether recording is wanted and for how
 * long, and drop a note in a folder when the key goes down.
 *
 * <h2>The folder</h2>
 *
 * A file in a known place rather than a socket. No port to be taken, no
 * firewall prompt on first launch, and a press that happens while the launcher
 * is closed is still waiting when it opens. The place is the platform's data
 * directory, because it is the one location both a Rust program and a Java one
 * work out identically without being told.
 */
public class ClipModule extends Module {

    private final IntSetting seconds = new IntSetting(
            "seconds", "Clip length (seconds)",
            "How much of the past is kept and written out", 30, 5, 300);

    private final BooleanSetting announce = new BooleanSetting(
            "announce", "Say so in chat",
            "Print a line when a clip has been asked for", true);

    /**
     * The key, here rather than only in the game's controls screen.
     *
     * It is the same binding either way - this row reads and writes the game's
     * own, so the two screens cannot drift apart. Having it here is the whole
     * point: the key belongs to this feature, and looking for it in a list of
     * ninety other bindings is a worse way to find it than looking where the
     * feature is.
     */
    private final KeySetting key = new KeySetting(
            "key", "Clip key", "Press the chip, then your key",
            () -> KeyBinds.codeOf(SpaceClient.getClipKey()),
            code -> KeyBinds.bind(SpaceClient.getClipKey(), code));

    /** Guards against a held key turning into a hundred clips. */
    private static final long COOLDOWN_MS = 1500;
    private long lastRequest = 0L;

    /** What was last written to the launcher, so it is not rewritten per tick. */
    private String lastWish = "";

    private static String status = "idle";

    public static String lastResult() { return status; }

    public ClipModule() {
        super("clip", "Clips",
                "Keeps the last seconds of play - press the clip key to save them. "
                        + "Clips are recorded and watched in the launcher.",
                false);
        addSettings(key, seconds, announce);
    }

    public int getSeconds() { return seconds.get(); }

    // ---------------------------------------------------------------- the link

    /**
     * The folder both sides agree on without being told.
     *
     * Matches what Rust's dirs::data_dir() answers on each platform, which is
     * the whole requirement - one of these two programs has to be able to find
     * the other's folder, and neither can ask.
     */
    public static Path clipDir() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path base;

        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            base = appData != null && !appData.isEmpty()
                    ? Path.of(appData)
                    : Path.of(System.getProperty("user.home"), "AppData", "Roaming");
        } else if (os.contains("mac")) {
            base = Path.of(System.getProperty("user.home"), "Library", "Application Support");
        } else {
            String xdg = System.getenv("XDG_DATA_HOME");
            base = xdg != null && !xdg.isEmpty()
                    ? Path.of(xdg)
                    : Path.of(System.getProperty("user.home"), ".local", "share");
        }

        return base.resolve("SpaceClient").resolve("clips");
    }

    /**
     * Tells the launcher whether to record, and for how long.
     *
     * Written only when it changes. The launcher reads this file on its own
     * clock, and a file rewritten twenty times a second would be read
     * half-written sooner or later.
     */
    private void writeWish() {
        String wish = "{\n  \"enabled\": " + isEnabled()
                + ",\n  \"seconds\": " + seconds.get() + "\n}\n";
        if (wish.equals(lastWish)) return;

        try {
            Path dir = clipDir();
            Files.createDirectories(dir);

            // Written beside the real file and moved into place, so the
            // launcher never reads half a line
            Path temp = dir.resolve("mod.json.tmp");
            Files.writeString(temp, wish, StandardCharsets.UTF_8);
            Files.move(temp, dir.resolve("mod.json"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            lastWish = wish;
            status = isEnabled() ? "recording " + seconds.get() + "s" : "off";
        } catch (IOException e) {
            status = "could not reach the clips folder: " + e.getMessage();
            warnOnce(e);
        }
    }

    private static boolean warned = false;

    private static void warnOnce(Throwable cause) {
        if (warned) return;
        warned = true;
        SpaceClient.LOGGER.warn("Clips: could not write to the clips folder", cause);
    }

    @Override
    public void onTick() {
        writeWish();
    }

    @Override
    protected void onEnable() {
        lastWish = "";
        writeWish();
    }

    @Override
    protected void onDisable() {
        lastWish = "";
        writeWish();
    }

    // ---------------------------------------------------------------- the key

    /**
     * Asks the launcher for a clip of the last few seconds.
     *
     * Called from the key handler whether or not this module is on, so that a
     * press with it off can say why nothing happened. A key that does nothing
     * and says nothing is indistinguishable from a broken one.
     */
    public void request() {
        if (!isEnabled()) {
            if (announce.get()) {
                Screens.chat("[Space Client] Clips are switched off - turn them on in the menu.");
            }
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastRequest < COOLDOWN_MS) return;
        lastRequest = now;

        try {
            Path dir = clipDir().resolve("requests");
            Files.createDirectories(dir);

            String body = "{\n  \"seconds\": " + seconds.get()
                    + ",\n  \"at\": " + now + "\n}\n";

            // Same trick as above, and it matters more here: the launcher is
            // watching this folder and would otherwise read the file the
            // instant it appears, empty.
            Path temp = dir.resolve(now + ".json.tmp");
            Files.writeString(temp, body, StandardCharsets.UTF_8);
            Files.move(temp, dir.resolve(now + ".json"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            status = "asked for a " + seconds.get() + "s clip";
            if (announce.get()) {
                Screens.chat("[Space Client] Clip of the last " + seconds.get()
                        + " seconds saved - watch it in the launcher.");
            }
        } catch (IOException e) {
            status = "could not ask for a clip: " + e.getMessage();
            warnOnce(e);
            if (announce.get()) {
                Screens.chat("[Space Client] Could not reach the clips folder - is the launcher installed?");
            }
        }
    }
}
