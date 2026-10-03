package gg.spaceclient.music;

import gg.spaceclient.ui.TextureLoader;

import net.minecraft.resources.Identifier;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The cover of the track that is playing, as a texture the HUD can draw.
 *
 * Windows keeps a thumbnail with every media session - the same picture the
 * volume overlay shows - so no web API and no account are needed. It is asked
 * for once per song, not on every poll: starting PowerShell is the expensive
 * part, and a cover does not change while the song plays.
 *
 * Two threads are involved and they meet here. The picture is fetched and
 * decoded next to the poll, off the render thread; turning it into a texture
 * has to happen on the render thread, so the finished bytes wait in a field
 * until the next frame picks them up.
 */
public final class Cover {
    private static final Identifier ID =
            Identifier.fromNamespaceAndPath("spaceclient", "music/cover");

    /** Covers are drawn small; storing them larger only costs video memory. */
    private static final int SIZE = 96;

    /** The track the cover was fetched for, as source|artist|title. */
    private static volatile String fetchedFor = "";

    /** A decoded picture waiting for the render thread, and whose it is. */
    private static volatile byte[] pending = null;
    private static volatile String pendingFor = "";
    private static volatile boolean pendingEmpty = false;

    /** What is on the texture right now. */
    private static String shownFor = "";
    private static boolean hasTexture = false;

    private Cover() {}

    private static String key(NowPlaying track) {
        return track.source() + "|" + track.artist() + "|" + track.title();
    }

    /**
     * Fetches the cover if the song changed. Called on the poll thread, right
     * after a reading from the media session.
     */
    static void update(NowPlaying track) {
        if (track == null || track.isEmpty()) return;
        String key = key(track);
        if (key.equals(fetchedFor)) return;
        fetchedFor = key;

        byte[] png = null;
        try {
            Path file = Path.of(System.getProperty("java.io.tmpdir"), "spaceclient-cover.img");
            if (MediaSession.thumbnail(file)) {
                png = toPng(Files.readAllBytes(file));
            }
        } catch (Throwable ignored) {
            // No cover is a normal outcome; the record stands in for it
        }

        pendingFor = key;
        pendingEmpty = png == null;
        pending = png;
    }

    /**
     * The cover to draw for this track, or null when there is none (yet).
     * Called on the render thread.
     */
    public static Identifier texture(NowPlaying track) {
        if (track == null || track.isEmpty()) return null;

        // Nothing new goes to the graphics card while the window is minimised:
        // the picture waits for the window to come back
        String waitingFor = pendingFor;
        if (!waitingFor.isEmpty() && !net.minecraft.client.Minecraft.getInstance().getWindow().isIconified()) {
            byte[] bytes = pending;
            boolean empty = pendingEmpty;
            pendingFor = "";
            pending = null;

            if (empty || bytes == null) {
                hasTexture = false;
            } else {
                // Registering under the same id replaces the old picture
                hasTexture = TextureLoader.register(bytes, ID) != null;
            }
            shownFor = waitingFor;
        }

        // A cover from the previous song is worse than no cover
        if (hasTexture && shownFor.equals(key(track))) return ID;

        // Windows had none for this song, or has not answered yet: the
        // catalogue lookup the song cards over other players use
        return Artwork.texture(track.artist(), track.title());
    }

    /**
     * Any picture the players hand out, as a small PNG.
     *
     * The game only reads PNG, and Spotify's covers are JPEG. Scaled down on
     * the way, since a 640 pixel cover drawn at forty is a waste of memory.
     */
    static byte[] toPng(byte[] raw) throws Exception {
        BufferedImage source = javax.imageio.ImageIO.read(new ByteArrayInputStream(raw));
        if (source == null) return null;

        BufferedImage small = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        var g = small.createGraphics();
        try {
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            // Square crop from the middle: some players send wide banners
            int side = Math.min(source.getWidth(), source.getHeight());
            int sx = (source.getWidth() - side) / 2;
            int sy = (source.getHeight() - side) / 2;
            g.drawImage(source, 0, 0, SIZE, SIZE, sx, sy, sx + side, sy + side, null);
        } finally {
            g.dispose();
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(small, "png", out);
        return out.toByteArray();
    }
}
