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
    public record Card(String title, String artist, String source, String lyric) {}

    public static void submit(SubmitNodeCollector collector, PoseStack poseStack, Vec3 position,
                              CameraRenderState camera, Card card) {
        if (card == null || card.title().isEmpty()) return;

        int accent = accentFor(card.source());
        List<TextPlate.Run> topRuns = card.artist().isEmpty()
                ? List.of(new TextPlate.Run("\u266A " + card.title(), accent & 0xFFFFFF))
                : List.of(new TextPlate.Run("\u266A " + card.title(), accent & 0xFFFFFF),
                          new TextPlate.Run("  " + card.artist(), 0xC8C8C8));
        boolean hasLyric = !card.lyric().isEmpty();

        // Pictures are drawn off the render thread; until they are ready the
        // card waits, rather than showing a cover next to a gap
        TextPlate.Plate top = TextPlate.get(topRuns);
        TextPlate.Plate bottom = hasLyric
                ? TextPlate.get(List.of(new TextPlate.Run(card.lyric(), 0xFFFFFF)))
                : null;
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
