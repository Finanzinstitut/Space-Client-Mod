package gg.spaceclient.render;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.modules.MusicModule;
import gg.spaceclient.music.Lyrics;
import gg.spaceclient.ui.TextureLoader;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The song's lyrics in the sky: one line, large, above the horizon ahead of
 * the player, the sung part bright, the word being sung in the accent and
 * what is still to come faded - the way Apple Music shows lyrics.
 *
 * It sits in the world rather than on the screen. Look up and it is there,
 * look down and it is gone, like the sun. Its direction follows where the
 * player faces, slowly, so turning moves the view across it the way it would
 * across anything far away, and it drifts back in front after.
 *
 * The line is not game text. Minecraft's text is a pixel font, or a TrueType
 * one cut into small pixel glyphs; scaled up to the sky either looks like
 * blocks. Each line is set by Java2D instead, in a real typeface at a large
 * size with smooth edges and a soft shadow (SkyFont picks the face: on a Mac
 * Apple's own SF Pro), and goes up as one picture. One picture per word of a
 * line, drawn off the render thread; until the next is ready the last one
 * stays up, so nothing blinks.
 */
public final class SkyLyrics {

    /** How far away the line hangs, in blocks; it moves with the camera. */
    private static final double DISTANCE = 16.0;
    /** The letters' height on screen, as an angle, at size 100%. */
    private static final double ANGLE = Math.toRadians(4.2);
    /** Wider than this many degrees and the line is scaled down to fit. */
    private static final double MAX_WIDTH = Math.toRadians(75);
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final long FADE_MS = 350;

    /** Picture pixels per point of type: large, so it stays sharp up close. */
    private static final int SIZE = 112;
    private static final int PAD = SIZE / 3;
    /** Pictures kept: every word state of the last few lines. */
    private static final int KEEP = 40;

    /** A drawn line: its texture, its size, and how tall its letters are in it. */
    private record Plate(Identifier texture, int width, int height, int letters) {}

    private static final class Job {
        volatile byte[] png;
        volatile int width, height, letters;
        volatile boolean failed;
        Plate plate;
        final int number;
        Job(int number) { this.number = number; }
    }

    private static final Map<String, Job> jobs = new LinkedHashMap<>(64, 0.75f, true);
    private static int counter = 0;
    private static Plate shown = null;
    private static String shownFont = "";

    private static double anchorYaw = Double.NaN;
    private static long lastFrame = 0;
    private static String lastText = "";
    private static long lineSince = 0;
    private static boolean failed = false;

    private SkyLyrics() {}

    public static void submit(SubmitNodeCollector collector, CameraRenderState camera) {
        if (failed || camera == null) return;
        try {
            draw(collector);
        } catch (Throwable t) {
            failed = true;
            SpaceClient.LOGGER.error("Sky lyrics failed - stopping them", t);
        }
    }

    private static void draw(SubmitNodeCollector collector) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || SpaceClient.getModuleManager() == null) return;
        if (!(SpaceClient.getModuleManager().get("music") instanceof MusicModule music)) return;
        Lyrics.Moment moment = music.skyMoment();
        long now = System.currentTimeMillis();
        if (moment.isEmpty()) {
            lastText = "";
            shown = null;
            anchorYaw = Double.NaN;
            return;
        }
        if (!moment.text().equals(lastText)) {
            lastText = moment.text();
            lineSince = now;
        }

        String fontChoice = music.skyFont();
        if (!fontChoice.equals(shownFont)) {
            // Another face: what is cached was set in the old one
            shownFont = fontChoice;
            SkyFont.reload();
            clear();
        }
        Plate plate = plate(moment, music.skyAccent(), fontChoice);
        if (plate != null) shown = plate;
        if (shown == null) return;

        // The direction drifts after the view: a time constant of a second
        double yaw = Math.toRadians(mc.player.getYRot());
        double dt = lastFrame == 0 ? 0 : Math.min(0.25, (now - lastFrame) / 1000.0);
        lastFrame = now;
        if (Double.isNaN(anchorYaw)) {
            anchorYaw = yaw;
        } else {
            double diff = Math.IEEEremainder(yaw - anchorYaw, Math.PI * 2);
            anchorYaw += diff * (1 - Math.exp(-dt * 1.2));
        }

        double elevation = Math.toRadians(music.skyHeight());
        double horizontal = Math.cos(elevation) * DISTANCE;
        double x = -Math.sin(anchorYaw) * horizontal;
        double y = Math.sin(elevation) * DISTANCE;
        double z = Math.cos(anchorYaw) * horizontal;

        // World units per picture pixel, from how tall the letters should look
        double letters = 2 * DISTANCE * Math.tan(ANGLE * music.skyScale() / 2);
        double perPixel = letters / shown.letters();
        double maxWidth = 2 * DISTANCE * Math.tan(MAX_WIDTH / 2);
        if (shown.width() * perPixel > maxWidth) perPixel = maxWidth / shown.width();
        float w = (float) (shown.width() * perPixel / 2);
        float h = (float) (shown.height() * perPixel / 2);

        float fade = Math.min(1f, (now - lineSince) / (float) FADE_MS);
        int colour = (Math.max(8, Math.round(fade * 255)) << 24) | 0xFFFFFF;

        PoseStack pose = new PoseStack();
        pose.translate(x, y, z);
        // Facing the player square on, as the camera's own rotation would be
        // when looking straight at it: no roll, so it never tilts sideways
        gg.spaceclient.compat.Pose.rotate(pose, new org.joml.Quaternionf()
                .rotationYXZ((float) (Math.PI - anchorYaw), (float) elevation, 0f));
        Identifier texture = shown.texture();
        collector.submitCustomGeometry(pose, RenderTypes.text(texture), (p, buffer) -> {
            // In the camera's own frame: +x to the viewer's right, +y up;
            // top left, bottom left, bottom right, top right, as SongTag
            buffer.addVertex(p, -w, h, 0f).setColor(colour).setUv(0f, 0f).setLight(FULL_BRIGHT);
            buffer.addVertex(p, -w, -h, 0f).setColor(colour).setUv(0f, 1f).setLight(FULL_BRIGHT);
            buffer.addVertex(p, w, -h, 0f).setColor(colour).setUv(1f, 1f).setLight(FULL_BRIGHT);
            buffer.addVertex(p, w, h, 0f).setColor(colour).setUv(1f, 0f).setLight(FULL_BRIGHT);
        });
    }

    /** The picture for this moment, or null while it is being drawn. */
    private static Plate plate(Lyrics.Moment moment, int accent, String font) {
        String key = moment.text() + '\u0000' + moment.wordStart() + '\u0000' + moment.wordEnd() + '\u0000' + accent;
        Job job = jobs.get(key);
        if (job == null) {
            if (jobs.size() >= KEEP) evictOldest();
            job = new Job(counter++);
            jobs.put(key, job);
            render(moment, accent, font, job);
            return null;
        }
        if (job.failed) return null;
        if (job.plate == null && job.png != null) {
            Identifier id = Identifier.fromNamespaceAndPath(SpaceClient.MOD_ID, "music/sky_" + job.number);
            Identifier registered = TextureLoader.register(job.png, id);
            job.png = null;
            if (registered == null) {
                job.failed = true;
                return null;
            }
            job.plate = new Plate(registered, job.width, job.height, job.letters);
        }
        return job.plate;
    }

    private static void evictOldest() {
        var it = jobs.entrySet().iterator();
        if (!it.hasNext()) return;
        Job oldest = it.next().getValue();
        it.remove();
        release(oldest);
    }

    private static void clear() {
        for (Job job : jobs.values()) release(job);
        jobs.clear();
        shown = null;
    }

    private static void release(Job job) {
        if (job.plate == null || job.plate == shown) return;
        try {
            Minecraft.getInstance().getTextureManager().release(job.plate.texture());
        } catch (Throwable ignored) {
            // A picture that outlives its entry is a small leak, not a fault
        }
    }

    /** Sets the line in its typeface, off the render thread. */
    private static void render(Lyrics.Moment moment, int accent, String choice, Job job) {
        CompletableFuture.runAsync(() -> {
            try {
                String text = moment.text();
                int start = 0, end = 0;
                if (moment.hasWord()) {
                    start = Math.max(0, Math.min(text.length(), moment.wordStart()));
                    end = Math.max(start, Math.min(text.length(), moment.wordEnd()));
                }
                Font face = SkyFont.of(choice).deriveFont(Font.BOLD, SIZE);
                // A face without these letters (Arabic, Chinese...) gives way to
                // the system's own, which finds a font for every script
                if (face.canDisplayUpTo(text) != -1) face = new Font(Font.DIALOG, Font.BOLD, SIZE);

                BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
                Graphics2D probe = scratch.createGraphics();
                hints(probe);
                probe.setFont(face);
                FontMetrics metrics = probe.getFontMetrics();
                String[] parts = { text.substring(0, start), text.substring(start, end), text.substring(end) };
                int[] widths = new int[3];
                int total = 0;
                for (int i = 0; i < 3; i++) {
                    widths[i] = metrics.stringWidth(parts[i]);
                    total += widths[i];
                }
                int ascent = metrics.getAscent();
                int descent = metrics.getDescent();
                probe.dispose();

                int width = Math.max(1, total + 2 * PAD);
                int height = ascent + descent + 2 * PAD;
                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = image.createGraphics();
                try {
                    hints(g);
                    g.setFont(face);
                    int baseline = PAD + ascent;
                    // A soft shadow: the line many times, faint, around a
                    // point a little below it, so it reads on any sky
                    int spread = Math.max(2, SIZE / 28);
                    g.setComposite(AlphaComposite.SrcOver);
                    for (int dx = -spread; dx <= spread; dx += Math.max(1, spread / 2)) {
                        for (int dy = -spread; dy <= spread; dy += Math.max(1, spread / 2)) {
                            g.setColor(new Color(0, 0, 0, 14));
                            g.drawString(text, PAD + dx, baseline + dy + spread);
                        }
                    }
                    int[] colours = { 0xFFFFFFFF, accent | 0xFF000000, 0x8CFFFFFF };
                    if (!moment.hasWord()) colours[2] = 0xFFFFFFFF;
                    int x = PAD;
                    for (int i = 0; i < 3; i++) {
                        if (parts[i].isEmpty()) continue;
                        g.setColor(new Color(colours[i], true));
                        g.drawString(parts[i], x, baseline);
                        x += widths[i];
                    }
                } finally {
                    g.dispose();
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                javax.imageio.ImageIO.write(image, "png", out);
                job.width = width;
                job.height = height;
                job.letters = ascent + descent;
                job.png = out.toByteArray();
            } catch (Throwable t) {
                job.failed = true;
                SpaceClient.LOGGER.warn("Sky lyrics: could not set a line: {}", t.toString());
            }
        });
    }

    private static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }
}
