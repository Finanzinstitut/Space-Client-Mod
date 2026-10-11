package gg.spaceclient.compat;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
//#if MC >= 26.1
import net.minecraft.client.renderer.state.level.CameraRenderState;
//#else
//$$ import net.minecraft.client.renderer.state.CameraRenderState;
//#endif
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * The game's name tag call. Up to 26.1 it also takes the entity's squared
 * distance to the camera, which the game uses to order overlapping tags;
 * from 26.2 the camera state is enough.
 */
public final class NameTags {

    private NameTags() {}

    public static void submit(SubmitNodeCollector collector, PoseStack poseStack, Vec3 position, int y,
                              Component text, boolean seeThrough, int light, double distanceSq,
                              CameraRenderState camera) {
        //#if MC >= 26.2
        collector.submitNameTag(poseStack, position, y, text, seeThrough, light, camera);
        //#else
        //$$ collector.submitNameTag(poseStack, position, y, text, seeThrough, light, distanceSq, camera);
        //#endif
    }
}
