package gg.spaceclient.render;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.modules.LightLevelModule;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the Light Level module's crosses: two thin flat bars in an X on the
 * floor of each marked block.
 *
 * All of them in one submit per frame, from the array the module's search
 * already finished - drawing does no world lookups at all.
 */
public final class LightLevelRenderer {

    private static final PoseStack IDENTITY = new PoseStack();
    private static boolean failed = false;

    /** Half the width of a bar, and how far in from the block's edge the X stops. */
    private static final float HALF_WIDTH = 0.035f;
    private static final float INSET = 0.18f;
    /** Just above the floor, so the cross does not flicker with the block's own face. */
    private static final float LIFT = 0.012f;

    private LightLevelRenderer() {}

    public static void submit(SubmitNodeCollector collector) {
        if (failed) return;
        LightLevelModule module = LightLevelModule.drawing();
        if (module == null) return;
        int count = module.markCount();
        if (count == 0) return;

        Minecraft mc = Minecraft.getInstance();
        Vec3 camera = HitboxRenderer.cameraPosition(mc);
        RenderType type = HitboxRenderer.lineType();
        if (camera == null || type == null) return;

        long[] marks = module.marks();
        byte[] kinds = module.kinds();
        int[] colours = {
                module.colourFor(LightLevelModule.SAFE),
                module.colourFor(LightLevelModule.DANGER),
                module.colourFor(LightLevelModule.NIGHT),
        };
        double cx = camera.x, cy = camera.y, cz = camera.z;

        collector.submitCustomGeometry(IDENTITY, type, (pose, buffer) -> {
            try {
                int n = Math.min(count, Math.min(marks.length, kinds.length));
                for (int i = 0; i < n; i++) {
                    long packed = marks[i];
                    float x = (float) (BlockPos.getX(packed) - cx);
                    float y = (float) (BlockPos.getY(packed) - cy) + LIFT;
                    float z = (float) (BlockPos.getZ(packed) - cz);
                    cross(buffer, x, y, z, colours[kinds[i]]);
                }
            } catch (Throwable t) {
                failed = true;
                SpaceClient.LOGGER.error("Light level crosses failed - stopping them", t);
            }
        });
    }

    private static void cross(VertexConsumer buffer, float x, float y, float z, int argb) {
        float a = INSET, b = 1f - INSET, w = HALF_WIDTH;
        // From one corner to the opposite; the width runs across the diagonal
        bar(buffer, x + a, z + a, x + b, z + b, y, w, -w, argb);
        bar(buffer, x + a, z + b, x + b, z + a, y, w, w, argb);
    }

    private static void bar(VertexConsumer buffer, float x1, float z1, float x2, float z2,
                            float y, float ox, float oz, int argb) {
        // Wound both ways, so it shows whichever side it is seen from
        buffer.addVertex(x1 - ox, y, z1 - oz).setColor(argb);
        buffer.addVertex(x1 + ox, y, z1 + oz).setColor(argb);
        buffer.addVertex(x2 + ox, y, z2 + oz).setColor(argb);
        buffer.addVertex(x2 - ox, y, z2 - oz).setColor(argb);
        buffer.addVertex(x2 - ox, y, z2 - oz).setColor(argb);
        buffer.addVertex(x2 + ox, y, z2 + oz).setColor(argb);
        buffer.addVertex(x1 + ox, y, z1 + oz).setColor(argb);
        buffer.addVertex(x1 - ox, y, z1 - oz).setColor(argb);
    }
}
