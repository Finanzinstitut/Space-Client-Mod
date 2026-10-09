package gg.spaceclient.render;

import com.mojang.blaze3d.vertex.PoseStack;

import org.joml.Quaternionf;

/**
 * Rotating a pose stack by a yaw and a pitch.
 *
 * This used to look up {@code mulPose(Quaternionf)} by reflection, so that a
 * renamed method could not fail the build. On 26.3 it was renamed - to
 * {@code rotate(Quaternionfc)} - and the lookup quietly found nothing, which
 * meant Item Physics never turned a single item. A compile error would have
 * said so at once; the silent fallback hid it. So it is called directly now,
 * through the version switch in compat.Pose.
 */
public final class PoseOps {

    private PoseOps() {}

    /** Whether rotation works, for the diagnostics screen. Always, now. */
    public static boolean canRotate() {
        return true;
    }

    /**
     * Turns the pose by the given angles, in degrees.
     *
     * Yaw first, then pitch, which is the order that reads as "lay it down
     * facing that way" rather than "tip it over and then spin the floor".
     */
    public static void rotate(PoseStack poseStack, float yawDegrees, float pitchDegrees) {
        if (poseStack == null) return;
        gg.spaceclient.compat.Pose.rotate(poseStack, new Quaternionf()
                .rotateY((float) Math.toRadians(yawDegrees))
                .rotateX((float) Math.toRadians(pitchDegrees)));
    }
}
