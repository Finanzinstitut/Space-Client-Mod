package gg.spaceclient.render;

import gg.spaceclient.music.Artwork;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
//#if MC >= 26.1
import net.minecraft.client.renderer.state.level.CameraRenderState;
//#else
//$$ import net.minecraft.client.renderer.state.CameraRenderState;
//#endif
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The song card over a player's name: cover, title and artist, lyric line.
 *
 * Every part is a textured quad - the cover, and each line of text as a
 * picture (see TextPlate) - in the camera-facing frame vanilla uses for the
 * name. Nothing goes through the game's text or name tag rendering:
 *
 * - as name tags, other mods that hook them (Essential, Cosmetica) treat the
 *   song as a name of their own and decorate, move or drop it;
 * - as directly submitted text, the game sorts it into phases that differ
 *   between versions, and on 26.3 it did not show at all while the cover did.
 *
 * Laid out in that frame's pixels, where up the screen is simply up.
 */
public final class SongTag {

    /** Full brightness: a label should not go dark because it is night. */
    private static final int FULL_BRIGHT = 0xF000F0;

    private SongTag() {}

    /** What one card shows. Any part but the title may be empty. */
    public record Card(String title, String artist, String source, gg.spaceclient.music.Lyrics.Moment lyric) {}

    /** Words already sung, the word being sung, and the ones still to come. */
    private static final int SUNG = 0xFFFFFF;
    private static final int TO_COME = 0x9A9AA2;

    /**
     * The lyric line as coloured runs, with the word being sung in the
     * player's colour.
     */
    private static List<TextPlate.Run> lyricRuns(gg.spaceclient.music.Lyrics.Moment moment, int accent) {
        String text = moment.text();
        if (!moment.hasWord()) return List.of(new TextPlate.Run(text, SUNG));
        int a = Math.min(text.length(), moment.wordStart());
        int b = Math.min(text.length(), moment.wordEnd());
        List<TextPlate.Run> runs = new java.util.ArrayList<>(3);
        if (a > 0) runs.add(new TextPlate.Run(text.substring(0, a), SUNG));
        // A player without a colour of its own is white, which is what the
        // sung words already are; the word then goes gold instead
        int word = (accent & 0xFFFFFF) == 0xFFFFFF ? 0xFFD54A : accent & 0xFFFFFF;
        runs.add(new TextPlate.Run(text.substring(a, b), word));
        if (b < text.length()) runs.add(new TextPlate.Run(text.substring(b), TO_COME));
        return runs;
    }

    /**
     * The last lyric picture shown for each song, for while the next one is
     * still being drawn. Every word is a picture of its own, made off the
     * render thread; without this the line blinked out for a frame or two at
     * each word.
     */
    private static final java.util.Map<String, TextPlate.Plate> lastLyric =
            new java.util.LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, TextPlate.Plate> eldest) {
                    return size() > 32;
                }
            };

    public static void submit(SubmitNodeCollector collector, PoseStack poseStack, Vec3 position,
                              CameraRenderState camera, Card card) {
        if (card == null || card.title().isEmpty()) return;

        int accent = accentFor(card.source());
        List<TextPlate.Run> topRuns = card.artist().isEmpty()
                ? List.of(new TextPlate.Run("\u266A " + card.title(), accent & 0xFFFFFF))
                : List.of(new TextPlate.Run("\u266A " + card.title(), accent & 0xFFFFFF),
                          new TextPlate.Run("  " + card.artist(), 0xC8C8C8));
        boolean hasLyric = card.lyric() != null && !card.lyric().isEmpty();

        // Pictures are drawn off the render thread; until they are ready the
        // card waits, rather than showing a cover next to a gap
        TextPlate.Plate top = TextPlate.get(topRuns);
        TextPlate.Plate bottom = null;
        if (hasLyric) {
            // Per song: until the next picture is ready the last one stays up
            String lineKey = card.artist() + "\u0000" + card.title();
            bottom = TextPlate.get(lyricRuns(card.lyric(), accent));
            if (bottom != null) lastLyric.put(lineKey, bottom);
            else bottom = lastLyric.get(lineKey);
            // The plain line too, ready for the first word of the next line
            if (bottom == null) bottom = TextPlate.get(List.of(new TextPlate.Run(card.lyric().text(), SUNG)));
        }
        hasLyric = bottom != null;
        if (top == null) return;

        Identifier cover = Artwork.texture(card.artist(), card.title());

        // Rows above the name, which vanilla draws from y = 0 downwards
        int rowHeight = TextPlate.HEIGHT;
        int bottomRowY = -rowHeight - 1;
        int topRowY = hasLyric ? bottomRowY - rowHeight : bottomRowY;

        poseStack.pushPose();
        try {
            // The frame vanilla puts a name tag in: half a block above the
            // anchor, turned to the camera, scaled to text pixels with y down
            poseStack.translate(position.x, position.y + 0.5, position.z);
            gg.spaceclient.compat.Pose.rotate(poseStack, camera.orientation);
            poseStack.scale(0.025f, -0.025f, 0.025f);

            quad(collector, poseStack, top.texture(),
                    -top.width() / 2f, topRowY, top.width() / 2f, topRowY + top.height());

            if (bottom != null) {
                quad(collector, poseStack, bottom.texture(),
                        -bottom.width() / 2f, bottomRowY, bottom.width() / 2f, bottomRowY + bottom.height());
            }

            if (cover != null) {
                // Square, as tall as the rows it sits beside, left of the title
                float size = hasLyric ? 2 * rowHeight + 1 : rowHeight;
                float x1 = -top.width() / 2f - 1;
                quad(collector, poseStack, cover, x1 - size, topRowY, x1, topRowY + size);
            }
        } finally {
            poseStack.popPose();
        }
    }

    /** One textured rectangle in the card's frame. */
    private static void quad(SubmitNodeCollector collector, PoseStack poseStack, Identifier texture,
                             float x0, float y0, float x1, float y1) {
        collector.submitCustomGeometry(poseStack, RenderTypes.text(texture), (pose, buffer) -> {
            buffer.addVertex(pose, x0, y0, 0f).setColor(-1).setUv(0f, 0f).setLight(FULL_BRIGHT);
            buffer.addVertex(pose, x0, y1, 0f).setColor(-1).setUv(0f, 1f).setLight(FULL_BRIGHT);
            buffer.addVertex(pose, x1, y1, 0f).setColor(-1).setUv(1f, 1f).setLight(FULL_BRIGHT);
            buffer.addVertex(pose, x1, y0, 0f).setColor(-1).setUv(1f, 0f).setLight(FULL_BRIGHT);
        });
    }

    /** Spotify green, Amazon Music light blue, white when the player is unknown. */
    public static int accentFor(String source) {
        String lower = source == null ? "" : source.toLowerCase();
        if (lower.contains("spotify")) return 0xFF1DB954;
        if (lower.contains("amazon")) return 0xFF4CC8F4;
        return 0xFFFFFFFF;
    }
}
