package gg.spaceclient.render;

import gg.spaceclient.net.Badges;
import gg.spaceclient.ui.Ease;
import gg.spaceclient.ui.Glass;
import gg.spaceclient.ui.Theme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Says so, top right, when your own mark changes.
 *
 * Drawn by this client rather than handed to the game's own notification
 * system: that system's shape is one more thing to guess at on this version,
 * and this needs a panel, two lines and the mark itself - all of which the menu
 * already knows how to draw.
 *
 * It only ever fires on a change, never on the first answer of a session.
 * Telling somebody their rank is what it already was, every time they start the
 * game, is not news.
 */
public final class RankToast {

    /** How long it stays, in milliseconds. */
    private static final long LIFE_MS = 5000L;

    private static final int WIDTH = 168;
    private static final int HEIGHT = 40;
    private static final int MARGIN = 8;

    private static volatile long shownAt = 0L;
    private static volatile Badges.Rank rank = null;

    private RankToast() {}

    /** Starts the five seconds. Called when the rank actually changed. */
    public static void show(Badges.Rank newRank) {
        rank = newRank;
        shownAt = System.currentTimeMillis();
    }

    public static void draw(GuiGraphicsExtractor graphics, int width, int height) {
        long at = shownAt;
        if (at == 0L) return;

        long age = System.currentTimeMillis() - at;
        if (age >= LIFE_MS) {
            shownAt = 0L;
            return;
        }

        float t = age / (float) LIFE_MS;

        // In from the right over the first fifth, out over the last fifth. The
        // slide is what makes it read as arriving rather than blinking on.
        float in = t < 0.2f ? Ease.outCubic(t / 0.2f) : 1f;
        float out = t > 0.8f ? 1f - (t - 0.8f) / 0.2f : 1f;
        float alpha = Math.min(in, out);
        if (alpha <= 0.01f) return;

        int slide = Math.round((1f - in) * (WIDTH + MARGIN));
        int x = width - WIDTH - MARGIN + slide;
        int y = MARGIN;
        int ink = Math.round(255 * alpha);

        Glass.panel(graphics, x, y, WIDTH, HEIGHT, scale(0xE614113A, ink), 8);

        // Colour and name come off the rank itself. They were two switch
        // statements here, and a switch over an enum has to list every
        // constant - which is the compiler telling you about the tenth rank in
        // the least useful possible place.
        Badges.Rank shown = rank == null ? Badges.Rank.STANDARD : rank;
        int accent = shown.accent();

        // The mark itself, in the badge font, so the message shows the thing it
        // is talking about rather than only naming it.
        var font = Minecraft.getInstance().font;
        int textX = x + 12;

        graphics.text(font, "Rang aktualisiert", textX, y + 9,
                scale(Theme.TEXT_DIM, ink), false);
        graphics.text(font, shown.label(), textX, y + 22, scale(accent, ink), false);

        graphics.fill(x, y + 6, x + 3, y + HEIGHT - 6, scale(accent, ink));
    }

    /** Multiplies a colour's alpha, so the whole panel fades as one. */
    private static int scale(int colour, int alpha) {
        int a = ((colour >>> 24) & 0xFF) * alpha / 255;
        return (a << 24) | (colour & 0xFFFFFF);
    }
}
