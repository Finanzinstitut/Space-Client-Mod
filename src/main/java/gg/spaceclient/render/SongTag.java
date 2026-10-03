package gg.spaceclient.render;

import gg.spaceclient.music.Artwork;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * The song card over a player's name: cover, title and artist, lyric line.
 *
 * Drawn on its own rather than as extra name tags. It used to go through
 * submitNameTag a second and third time, and other mods that hook name tags -
 * Essential puts its icon on every line it sees - then treated the song as a
 * name of their own: the icon landed on the lyric and the song line vanished
 * altogether. Text submitted directly, in the same camera-facing frame vanilla
 * uses for the name, passes through none of that.
 *
 * Laid out in that frame's pixels, where up the screen is simply up: nothing
 * has to be corrected for the viewing angle the way NameLines had to.
 */
public final class SongTag {

    /** Full brightness: a label should not go dark because it is night. */
    private static final int FULL_BRIGHT = 0xF000F0;

    /** Darker than vanilla's quarter-opacity plate, which vanished against sky. */
    private static final int PLATE = 0xA0000000;

    private SongTag() {}

    /** What one card shows. Any part but the title may be empty. */
    public record Card(String title, String artist, String source, String lyric) {}

    public static void submit(SubmitNodeCollector collector, PoseStack poseStack, Vec3 position,
                              CameraRenderState camera, Card card) {
        if (card == null || card.title().isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int accent = accentFor(card.source());
        Component top = Component.literal("♪ " + card.title())
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(accent & 0xFFFFFF)));
        if (!card.artist().isEmpty()) {
            top = top.copy().append(Component.literal("  " + card.artist())
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xC8C8C8))));
        }
        boolean hasLyric = !card.lyric().isEmpty();
        Component bottom = hasLyric ? Component.literal(card.lyric()) : null;

        Identifier cover = Artwork.texture(card.artist(), card.title());
        int coverSize = cover == null ? 0 : (hasLyric ? 20 : 10);
        int coverRoom = coverSize == 0 ? 0 : coverSize + 3;

        int textW = Math.max(font.width(top), bottom == null ? 0 : font.width(bottom));
        int left = -(coverRoom + textW) / 2;
        int textX = left + coverRoom;

        // Rows above the name, which vanilla draws from y = 0 downwards
        int bottomRowY = -12;
        int topRowY = hasLyric ? -23 : -12;

        poseStack.pushPose();
        try {
            // The frame vanilla puts a name tag in: half a block above the
            // anchor, turned to the camera, scaled to text pixels with y down
            poseStack.translate(position.x, position.y + 0.5, position.z);
            poseStack.rotate(camera.orientation);
            poseStack.scale(0.025f, -0.025f, 0.025f);

            line(collector, poseStack, font, top, textX, topRowY);
            if (bottom != null) {
                line(collector, poseStack, font, bottom, textX + (textW - font.width(bottom)) / 2, bottomRowY);
            }

            if (cover != null) {
                int x0 = left;
                int y0 = (hasLyric ? topRowY : bottomRowY) - 1;
                int x1 = x0 + coverSize;
                int y1 = y0 + coverSize;
                collector.submitCustomGeometry(poseStack, RenderTypes.text(cover), (pose, buffer) -> {
                    buffer.addVertex(pose, x0, y0, 0f).setColor(-1).setUv(0f, 0f).setLight(FULL_BRIGHT);
                    buffer.addVertex(pose, x0, y1, 0f).setColor(-1).setUv(0f, 1f).setLight(FULL_BRIGHT);
                    buffer.addVertex(pose, x1, y1, 0f).setColor(-1).setUv(1f, 1f).setLight(FULL_BRIGHT);
                    buffer.addVertex(pose, x1, y0, 0f).setColor(-1).setUv(1f, 0f).setLight(FULL_BRIGHT);
                });
            }
        } finally {
            poseStack.popPose();
        }
    }

    /**
     * One line, the way vanilla draws a name: a faint pass that shows through
     * walls, with the plate, and a full pass on top that the world can hide.
     */
    private static void line(SubmitNodeCollector collector, PoseStack poseStack, Font font,
                             Component text, int x, int y) {
        var sequence = text.getVisualOrderText();
        collector.submitText(poseStack, x, y, sequence, false, Font.DisplayMode.SEE_THROUGH,
                FULL_BRIGHT, 0x80FFFFFF, PLATE, 0);
        collector.submitText(poseStack, x, y, sequence, false, Font.DisplayMode.NORMAL,
                FULL_BRIGHT, 0xFFFFFFFF, 0, 0);
    }

    /** Spotify green, Amazon Music light blue, white when the player is unknown. */
    public static int accentFor(String source) {
        String lower = source == null ? "" : source.toLowerCase();
        if (lower.contains("spotify")) return 0xFF1DB954;
        if (lower.contains("amazon")) return 0xFF4CC8F4;
        return 0xFFFFFFFF;
    }
}
