package gg.spaceclient.modules;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.ColorSetting;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.server.IntegratedServer;

/**
 * How fast the server is actually running, in ticks per second.
 *
 * <h2>Where the number comes from</h2>
 *
 * On a server, from the time packets it sends: each one carries the server's
 * own game time, stamped when it was sent. The ticks between two of them,
 * divided by the real time between their arrivals, is the server's rate. Over
 * a ten-second window the network's jitter averages out.
 *
 * This replaced a reading of the client's own world clock. The client advances
 * that clock itself, twenty times a second, whatever the server does, and the
 * server's corrections only nudged it - so a lagging server still read close to
 * 20, which is exactly the case the readout exists for.
 *
 * In singleplayer the server is in the same process, so its clock is read
 * directly every tick instead of waiting for packets.
 *
 * The ceiling is the server's target rate, not a fixed 20 - a server running
 * /tick rate 40 really does run forty a second.
 */
public class TpsModule extends HudModule {
    private static TpsModule instance;

    /** How much history the estimate covers. */
    private static final long WINDOW_NANOS = 10_000_000_000L;
    /** No time packet for this long: the server stalled or does not send them. */
    private static final long STALE_NANOS = 5_000_000_000L;

    private final BooleanSetting colorCoded = new BooleanSetting(
            "color_coded", "Colour by health",
            "Green when healthy, amber when slipping, red when struggling", true);
    private final BooleanSetting showMspt = new BooleanSetting(
            "show_mspt", "Show MSPT", "Milliseconds per tick, in singleplayer where it can be measured", false);
    private final ColorSetting textColor = new ColorSetting(
            "text_color", "Text colour", "Used when colour by health is off", 0xFFFFFFFF);

    // Arrivals of time packets: real time and the server's game time
    private static final int SAMPLES = 64;
    private static final long[] arrivedAt = new long[SAMPLES];
    private static final long[] gameTimes = new long[SAMPLES];
    private static int head = 0, count = 0;

    private double tps = -1;
    private double mspt = -1;
    private boolean stale = false;

    public TpsModule() {
        super("tps", "TPS", "How fast the server is ticking", 0.02f, 0.26f, false);
        addSettings(colorCoded, showMspt, textColor);
        instance = this;
    }

    /** From the network thread whenever the server sends its time. */
    public static void onServerTime(long gameTime) {
        long now = System.nanoTime();
        synchronized (arrivedAt) {
            // The packet is handled twice - once on the network thread, then
            // again on the game thread - and only the first arrival counts
            if (count > 0 && gameTimes[(head - 1 + SAMPLES) % SAMPLES] == gameTime) return;
            // The clock went back: the server set it. Start over.
            if (count > 0 && gameTime < gameTimes[(head - 1 + SAMPLES) % SAMPLES]) count = 0;
            arrivedAt[head] = now;
            gameTimes[head] = gameTime;
            head = (head + 1) % SAMPLES;
            if (count < SAMPLES) count++;
        }
    }

    private static void reset() {
        synchronized (arrivedAt) {
            count = 0;
        }
    }

    @Override
    public void onTick() {
        if (mc.level == null) {
            tps = -1;
            mspt = -1;
            reset();
            return;
        }
        float target = mc.level.tickRateManager().tickrate();

        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            // The server's own clock, read directly - the same measurement as
            // on a server, minus the network. Its average tick time is shown
            // as MSPT but not used for the rate: it does not cover everything
            // a tick can spend time on, and read 20 while the world crawled.
            mspt = server.getAverageTickTimeNanos() / 1_000_000.0;
            var overworld = server.overworld();
            if (overworld != null) onServerTime(overworld.getGameTime());
        } else {
            mspt = -1;
        }

        long now = System.nanoTime();
        synchronized (arrivedAt) {
            if (count < 2) {
                stale = count == 1 && now - arrivedAt[(head - 1 + SAMPLES) % SAMPLES] > STALE_NANOS;
                return;
            }
            int newest = (head - 1 + SAMPLES) % SAMPLES;
            stale = now - arrivedAt[newest] > STALE_NANOS;
            // Oldest sample still inside the window
            int oldest = newest;
            for (int i = 1; i < count; i++) {
                int index = (newest - i + SAMPLES) % SAMPLES;
                if (arrivedAt[newest] - arrivedAt[index] > WINDOW_NANOS) break;
                oldest = index;
            }
            if (oldest == newest) return;
            double seconds = (arrivedAt[newest] - arrivedAt[oldest]) / 1e9;
            long ticks = gameTimes[newest] - gameTimes[oldest];
            if (seconds < 0.5) return;
            tps = Math.min(target, ticks / seconds);
        }
    }

    /** Cached; see HudModule.cachedText for why. */
    private String text() { return cachedText(this::buildText); }

    private String buildText() {
        if (mc.level == null) return "-- tps";
        if (mc.level.tickRateManager().isFrozen()) return "frozen";
        if (tps < 0) return "... tps";
        String value = String.format("%.1f tps", tps);
        if (stale) value += " ?";
        if (showMspt.get() && mspt >= 0) value += String.format("  %.1f ms", mspt);
        return value;
    }

    private int color() {
        if (!colorCoded.get() || tps < 0 || mc.level == null) return textColor.get();
        double share = tps / Math.max(1f, mc.level.tickRateManager().tickrate());
        if (stale) return 0xFFFFAA00;
        if (share >= 0.95) return 0xFF55FF55;
        if (share >= 0.75) return 0xFFFFAA00;
        return 0xFFFF5555;
    }

    @Override
    public int getWidth() { return mc.font.width(text()); }

    @Override
    public int getHeight() { return mc.font.lineHeight; }

    @Override
    public void render(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.text(mc.font, text(), x, y, color(), true);
    }
}
