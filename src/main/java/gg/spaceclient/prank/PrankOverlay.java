package gg.spaceclient.prank;

import gg.spaceclient.ui.Fonts;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Random;

/**
 * Draws whatever prank is running, on top of everything else.
 *
 * Called once a frame from the mod's HUD hook. Each effect is a few fills and
 * some text - deliberately cheap, and deliberately incapable of doing anything
 * but drawing. There is no path from here to the network or to another player.
 *
 * The full-screen ones (crash, kick, ban) imitate the real screens closely
 * enough to be convincing on camera, down to the wording and the grey buttons.
 * They are still only paint: the game is running fine behind them.
 */
public final class PrankOverlay {

    private static final Random RANDOM = new Random();

    public static void render(GuiGraphicsExtractor graphics, int width, int height) {
        if (Pranks.expired()) {
            // A timed effect that has run out clears itself. Full-screen ones
            // have a zero duration and never expire here.
            if (!Pranks.isFullScreen()) Pranks.clear();
            return;
        }

        switch (Pranks.active()) {
            case FAKE_CRASH -> crash(graphics, width, height);
            case FAKE_KICK -> disconnect(graphics, width, height, "Disconnected", Pranks.reason());
            case FAKE_BAN -> ban(graphics, width, height);
            case FAKE_LAG -> lag(graphics, width, height);
            case SCREEN_EFFECT -> screen(graphics, width, height);
            case FAKE_CHAT -> chat(graphics, width, height);
            default -> {}
        }
    }

    /** The dirt-background crash report screen. */
    private static void crash(GuiGraphicsExtractor graphics, int width, int height) {
        graphics.fill(0, 0, width, height, 0xFF000000);

        var font = Fonts.ui();
        int x = 24;
        int y = 24;

        graphics.text(font, "\u00a7cMinecraft has crashed!", x, y, 0xFFFF5555, false);
        graphics.text(font, "\u00a77The game crashed whilst rendering overlay", x, y + 20, 0xFFAAAAAA, false);
        graphics.text(font, "\u00a77Error: java.lang.NullPointerException: Rendering overlay",
                x, y + 32, 0xFFAAAAAA, false);

        String[] trace = {
            "  at net.minecraft.client.renderer.GameRenderer.render(GameRenderer.java:830)",
            "  at net.minecraft.client.renderer.LevelRenderer.renderLevel(LevelRenderer.java:1621)",
            "  at net.minecraft.client.Minecraft.runTick(Minecraft.java:1211)",
            "  at net.minecraft.client.Minecraft.run(Minecraft.java:802)",
            "  at net.minecraft.client.main.Main.main(Main.java:243)",
        };
        for (int i = 0; i < trace.length; i++) {
            graphics.text(font, "\u00a78" + trace[i], x, y + 56 + i * 11, 0xFF666666, false);
        }

        graphics.text(font, "\u00a77Press any key to continue...",
                x, height - 30, 0xFF888888, false);
    }

    /** The "you were disconnected" screen with a reason and a grey button. */
    private static void disconnect(GuiGraphicsExtractor graphics, int width, int height,
                                   String title, String message) {
        graphics.fill(0, 0, width, height, 0xEF000000);

        var font = Fonts.ui();
        int cx = width / 2;

        graphics.text(font, title, cx - font.width(title) / 2, height / 2 - 40,
                0xFFFFFFFF, false);
        graphics.text(font, message, cx - font.width(message) / 2, height / 2 - 20,
                0xFFAAAAAA, false);

        // A vanilla-looking grey button
        int bw = 200, bh = 20;
        int bx = cx - bw / 2, by = height / 2 + 20;
        graphics.fill(bx, by, bx + bw, by + bh, 0xFF404040);
        graphics.fill(bx, by, bx + bw, by + 1, 0xFF6A6A6A);
        String label = "Back to Server List";
        graphics.text(font, label, cx - font.width(label) / 2, by + 6, 0xFFFFFFFF, false);

        graphics.text(font, "\u00a78(prank - press any key)",
                cx - font.width("(prank - press any key)") / 2, height - 24, 0xFF555555, false);
    }

    private static void ban(GuiGraphicsExtractor graphics, int width, int height) {
        graphics.fill(0, 0, width, height, 0xEF000000);

        var font = Fonts.ui();
        int cx = width / 2;

        String title = "\u00a7cYou are banned from this server!";
        graphics.text(font, title, cx - font.width(title) / 2, height / 2 - 50, 0xFFFF5555, false);

        String reason = "Reason: " + Pranks.reason();
        graphics.text(font, reason, cx - font.width(reason) / 2, height / 2 - 24, 0xFFAAAAAA, false);

        String until = "Your ban " + (Pranks.banDuration().equals("permanently")
                ? "will not expire" : "expires in " + Pranks.banDuration());
        graphics.text(font, until, cx - font.width(until) / 2, height / 2 - 12, 0xFFAAAAAA, false);

        int bw = 200, bh = 20, bx = cx - 100, by = height / 2 + 20;
        graphics.fill(bx, by, bx + bw, by + bh, 0xFF404040);
        graphics.fill(bx, by, bx + bw, by + 1, 0xFF6A6A6A);
        String label = "Back to Server List";
        graphics.text(font, label, cx - font.width(label) / 2, by + 6, 0xFFFFFFFF, false);
    }

    /**
     * Fake lag: a spinner and a darkening, as if the connection stalled.
     *
     * No freeze of the real game - freezing the client for real would be a poor
     * trade for a joke. It just looks stalled.
     */
    private static void lag(GuiGraphicsExtractor graphics, int width, int height) {
        // Pulse the dim so it looks like it is struggling rather than simply off
        float t = Pranks.elapsedSeconds();
        int alpha = (int) (90 + 60 * Math.abs(Math.sin(t * 2)));
        graphics.fill(0, 0, width, height, (alpha << 24));

        var font = Fonts.ui();
        String msg = "Downloading terrain...";
        graphics.text(font, msg, width / 2 - font.width(msg) / 2, height / 2,
                0xFFFFFFFF, false);
    }

    private static void screen(GuiGraphicsExtractor graphics, int width, int height) {
        switch (Pranks.screenKind()) {
            case STATIC -> {
                // TV snow: a scatter of grey dots, cheap and convincing in motion
                for (int i = 0; i < 1200; i++) {
                    int x = RANDOM.nextInt(width);
                    int y = RANDOM.nextInt(height);
                    int g = RANDOM.nextInt(200) + 55;
                    graphics.fill(x, y, x + 2, y + 2, 0xC0000000 | (g << 16) | (g << 8) | g);
                }
            }
            case CRACKED -> cracked(graphics, width, height);
            case FLIP, SHAKE -> {
                // Flip and shake move the whole view, which needs the pose stack
                // that Scale already resolves; the tint here just marks that the
                // effect is live even if the transform is unavailable
                graphics.fill(0, 0, width, 2, 0x40FFFFFF);
            }
        }
    }

    /**
     * A cracked-glass overlay, drawn as lines fanning from an impact point.
     *
     * The point is fixed per run rather than random per frame, or the cracks
     * would crawl. A seed derived from the start time keeps them still.
     */
    private static void cracked(GuiGraphicsExtractor graphics, int width, int height) {
        Random cracks = new Random(7);
        int ox = width / 2, oy = height / 2;

        for (int i = 0; i < 14; i++) {
            double angle = cracks.nextDouble() * Math.PI * 2;
            int length = 80 + cracks.nextInt(Math.max(1, Math.min(width, height) / 2));
            int ex = ox + (int) (Math.cos(angle) * length);
            int ey = oy + (int) (Math.sin(angle) * length);
            line(graphics, ox, oy, ex, ey, 0x90FFFFFF);
        }
        // The impact star
        graphics.fill(ox - 4, oy - 4, ox + 4, oy + 4, 0xB0FFFFFF);
    }

    /** A crude line, since the renderer only fills rectangles. */
    private static void line(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps == 0) return;
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps;
            int y = y1 + (y2 - y1) * i / steps;
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    /**
     * A fake chat line, drawn where the real chat sits.
     *
     * Not put into the real chat component: that API is unproven on this
     * version, and drawing it ourselves guarantees the line is local. It reads
     * as a server broadcast, and it is gone when the effect clears.
     */
    private static void chat(GuiGraphicsExtractor graphics, int width, int height) {
        String line = Pranks.pendingChat();
        if (line.isEmpty()) return;

        var font = Fonts.ui();
        int y = height - 48;
        // The faint backing the real chat draws behind each line
        graphics.fill(2, y - 2, 4 + font.width(line), y + 10, 0x66000000);
        graphics.text(font, line, 4, y, 0xFFFFFFFF, false);
    }

    private PrankOverlay() {}
}
