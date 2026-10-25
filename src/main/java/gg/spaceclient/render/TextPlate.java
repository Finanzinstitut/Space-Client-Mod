package gg.spaceclient.render;

import gg.spaceclient.ui.TextureLoader;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A line of text as a picture, drawn over a player's head like a cover.
 *
 * The song card's lines used to be submitted as game text. That path differs
 * from version to version - from 26.3 the game sorts name tag text into phases
 * of its own - and on 26.3 the cover (a plain textured quad) showed while the
 * lines did not. A picture goes through exactly the path the cover does, so it
 * shows wherever the cover shows, whatever the text renderer does.
 *
 * The line is drawn by Java2D off the render thread, with the plate behind it
 * and a shadow under it in the same picture, and registered as a texture on the
 * render thread once it is ready - the same hand-over Artwork uses.
 * Java2D's logical "Dialog" font falls back to whatever the system has for
 * scripts it lacks, so Cyrillic and CJK titles come out as letters.
 */
public final class TextPlate {

    /** One piece of a line, in its own colour (0xRRGGBB). */
    public record Run(String text, int rgb) {}

    /** A finished picture, with its size in the card's text pixels. */
    public record Plate(Identifier texture, float width, float height) {}

    /** Picture pixels per text pixel of the card. */
    private static final int SCALE = 3;
    /** A line is nine text pixels tall; the plate adds one above and below. */
    private static final int LINE = 9;
    private static final int PAD_X = 3;
    private static final int PAD_Y = 1;
    private static final int MAX_CHARS = 90;
    /** One picture per word of a lyric line now, for everyone in sight. */
    private static final int MAX = 160;
    private static final long RETRY_FAILED_MS = 30_000;

    /** Plate height in text pixels, for laying rows out. */
    public static final int HEIGHT = LINE + 2 * PAD_Y;

    private static final class Entry {
        volatile byte[] png;
        volatile int width;
        volatile int height;
        volatile boolean failed;
        volatile long failedAt;
        Identifier texture;
        final int number;
        Entry(int number) { this.number = number; }
    }

    private static int counter = 0;

    /** Least recently used last, so the eldest is the one given up when full. */
    private static final Map<String, Entry> entries = new LinkedHashMap<>(64, 0.75f, true);

    private TextPlate() {}

    /**
     * The picture for a line, or null while it is being drawn. Render thread
     * only: it may register a texture.
     */
    public static synchronized Plate get(List<Run> runs) {
        if (runs == null || runs.isEmpty()) return null;

        StringBuilder keyBuilder = new StringBuilder();
        for (Run run : runs) {
            keyBuilder.append(run.rgb()).append('\u0001').append(run.text()).append('\u0000');
        }
        String key = keyBuilder.toString();

        Entry entry = entries.get(key);
        if (entry != null && entry.failed
                && System.currentTimeMillis() - entry.failedAt > RETRY_FAILED_MS) {
            release(entry);
            entries.remove(key);
            entry = null;
        }
        if (entry == null) {
            if (entries.size() >= MAX) evictOldest();
            entry = new Entry(counter++);
            entries.put(key, entry);
            draw(runs, entry);
            return null;
        }
        if (entry.failed) return null;

        // Registered only while the window is showing, like the covers
        if (entry.texture == null && entry.png != null
                && !Minecraft.getInstance().getWindow().isIconified()) {
            Identifier id = Identifier.fromNamespaceAndPath("spaceclient", "music/line_" + entry.number);
            entry.texture = TextureLoader.register(entry.png, id);
            entry.png = null;
            if (entry.texture == null) {
                entry.failed = true;
                entry.failedAt = System.currentTimeMillis();
            }
        }
        if (entry.texture == null) return null;
        return new Plate(entry.texture, entry.width / (float) SCALE, entry.height / (float) SCALE);
    }

    private static void evictOldest() {
        var it = entries.entrySet().iterator();
        if (!it.hasNext()) return;
        Entry oldest = it.next().getValue();
        it.remove();
        release(oldest);
    }

    private static void release(Entry entry) {
        if (entry.texture == null) return;
        try {
            Minecraft.getInstance().getTextureManager().release(entry.texture);
        } catch (Throwable ignored) {
            // A texture that outlives its entry is a small leak, not a fault
        }
    }

    private static void draw(List<Run> runs, Entry entry) {
        CompletableFuture.runAsync(() -> {
            try {
                Font font = new Font(Font.DIALOG, Font.PLAIN, LINE * SCALE);

                // A scratch image only to measure with
                BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
                var probe = scratch.createGraphics();
                setHints(probe);
                probe.setFont(font);
                FontMetrics metrics = probe.getFontMetrics();

                String[] pieces = new String[runs.size()];
                int total = 0;
                int[] widths = new int[pieces.length];
                int budget = MAX_CHARS;
                for (int i = 0; i < pieces.length; i++) {
                    String text = runs.get(i).text();
                    if (text.length() > budget) {
                        text = budget > 1 ? text.substring(0, budget - 1) + "\u2026" : "";
                    }
                    budget = Math.max(0, budget - text.length());
                    pieces[i] = text;
                    widths[i] = metrics.stringWidth(text);
                    total += widths[i];
                }
                int ascent = metrics.getAscent();
                probe.dispose();

                int padX = PAD_X * SCALE;
                int padY = PAD_Y * SCALE;
                int width = total + 2 * padX;
                int height = LINE * SCALE + 2 * padY;

                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                var g = image.createGraphics();
                try {
                    setHints(g);
                    g.setFont(font);

                    // The plate: the dark rounded backing a name tag has
                    g.setColor(new Color(0, 0, 0, 110));
                    g.fillRoundRect(0, 0, width, height, 2 * SCALE, 2 * SCALE);

                    // Text is centred in the picture's height by its own metrics
                    int baseline = padY + (LINE * SCALE - (metrics.getAscent() + metrics.getDescent())) / 2 + ascent;
                    int x = padX;
                    for (int i = 0; i < pieces.length; i++) {
                        g.setColor(new Color(0, 0, 0, 150));
                        g.drawString(pieces[i], x + SCALE, baseline + SCALE);
                        g.setColor(new Color(runs.get(i).rgb() | 0xFF000000, true));
                        g.drawString(pieces[i], x, baseline);
                        x += widths[i];
                    }
                } finally {
                    g.dispose();
                }

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                javax.imageio.ImageIO.write(image, "png", out);
                entry.width = width;
                entry.height = height;
                entry.png = out.toByteArray();
            } catch (Throwable t) {
                entry.failedAt = System.currentTimeMillis();
                entry.failed = true;
            }
        });
    }

    private static void setHints(java.awt.Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }
}
