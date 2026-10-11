package gg.spaceclient.compat;

import com.mojang.blaze3d.vertex.PoseStack;

import org.joml.Quaternionfc;

/** Pose stack calls whose names changed between Minecraft versions. */
public final class Pose {

    private Pose() {}

    /** Turns the pose by a rotation - mulPose up to 26.2, rotate from 26.3. */
    public static void rotate(PoseStack poseStack, Quaternionfc rotation) {
        //#if MC >= 26.3
        poseStack.rotate(rotation);
        //#else
        //$$ poseStack.mulPose(rotation);
        //#endif
    }
}
