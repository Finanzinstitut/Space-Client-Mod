package gg.spaceclient.render;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.modules.MusicModule;
import gg.spaceclient.music.Lyrics;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * The song's lyrics in the sky: one line, large, above the horizon ahead of
 * the player, with the word being sung lit.
 *
 * It sits in the world rather than on the screen. Look up and it is there,
 * look down and it is gone, like the sun. Its direction follows where the
 * player faces, but slowly, so turning the head moves the view across it the
 * way it would across anything far away, and it drifts back in front after.
 *
 * Drawn in Inter Display (assets/spaceclient/font/sky.json), rasterised large
 * and scaled into the world, full bright with a shadow so it reads on a blue
 * sky and a night one alike. A new line fades in.
 */
public final class SkyLyrics {

    private static final Style FONT = Style.EMPTY.withFont(new FontDescription.Resource(
            Identifier.fromNamespaceAndPath(SpaceClient.MOD_ID, "sky")));
    /**
     * How far away the line hangs, in blocks. It moves with the camera, so
     * the distance does not make it swim; it only decides what can stand in
     * front of it. Near enough that fog at a short render distance leaves it
     * alone, far enough that only a roof or a tree overhead covers it.
     */
    private static final double DISTANCE = 16.0;
    /** The line's height on screen, as an angle, at size 100%. */
    private static final double ANGLE = Math.toRadians(4.2);
    /** Wider than this many degrees and the line is scaled down to fit. */
    private static final double MAX_WIDTH = Math.toRadians(70);
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int SUNG = 0xFFFFFFFF;
    /** Not sung yet: a light grey that still reads against a bright sky. */
    private static final int AHEAD = 0xFFB9C2CE;
    private static final long FADE_MS = 350;

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
            anchorYaw = Double.NaN;
            return;
        }
        if (!moment.text().equals(lastText)) {
            lastText = moment.text();
            lineSince = now;
        }

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

        Component line = styled(moment, music.skyAccent());
        Font font = mc.font;
        float width = font.width(line);
        float height = font.lineHeight;
        double worldHeight = 2 * DISTANCE * Math.tan(ANGLE * music.skyScale() / 2);
        double scale = worldHeight / height;
        double maxWorldWidth = 2 * DISTANCE * Math.tan(MAX_WIDTH / 2);
        if (width * scale > maxWorldWidth) scale = maxWorldWidth / width;

        float fade = Math.min(1f, (now - lineSince) / (float) FADE_MS);
        int alpha = Math.max(8, Math.round(fade * 255));

        PoseStack pose = new PoseStack();
        pose.translate(x, y, z);
        // Facing the player square on, as the camera's own rotation would be
        // when looking straight at it: no roll, so it never tilts sideways
        gg.spaceclient.compat.Pose.rotate(pose, new org.joml.Quaternionf()
                .rotationYXZ((float) (Math.PI - anchorYaw), (float) elevation, 0f));
        pose.scale((float) scale, (float) -scale, (float) scale);
        collector.submitText(pose, -width / 2f, -height / 2f, line.getVisualOrderText(), true,
                Font.DisplayMode.NORMAL, FULL_BRIGHT, (alpha << 24) | 0xFFFFFF, 0, 0);
    }

    /** Sung words white, the word being sung in the accent, what is ahead dimmed. */
    private static Component styled(Lyrics.Moment moment, int accent) {
        String text = moment.text();
        if (!moment.hasWord()) return Component.literal(text).withStyle(FONT.withColor(SUNG & 0xFFFFFF));
        int start = Math.max(0, Math.min(text.length(), moment.wordStart()));
        int end = Math.max(start, Math.min(text.length(), moment.wordEnd()));
        MutableComponent out = Component.empty();
        out.append(Component.literal(text.substring(0, start)).withStyle(FONT.withColor(SUNG & 0xFFFFFF)));
        out.append(Component.literal(text.substring(start, end)).withStyle(FONT.withColor(accent & 0xFFFFFF)));
        out.append(Component.literal(text.substring(end)).withStyle(FONT.withColor(AHEAD & 0xFFFFFF)));
        return out;
    }
}
