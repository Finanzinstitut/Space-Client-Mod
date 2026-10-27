package gg.spaceclient.render;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.modules.LitematicaModule;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the Litematica helper's labels: a small name tag just above each
 * schematic block with a value worth knowing.
 *
 * Name tags because they already face the camera and sort themselves into
 * the frame; the labels are smaller than a player's tag and always lit.
 */
public final class SchematicLabels {

    private static final float SCALE = 0.6f;
    /** Name tags sit half a block over their point; this takes that back out. */
    private static final Vec3 ATTACH = new Vec3(0, -0.5, 0);
    /** The text hangs down from its point: this much room keeps it off the block. */
    private static final double CLEARANCE = 0.2;
    private static final int FULL_BRIGHT = 0xF000F0;
    private static boolean failed = false;

    private SchematicLabels() {}

    public static void submit(SubmitNodeCollector collector, CameraRenderState cameraState) {
        if (failed || cameraState == null) return;
        LitematicaModule module = LitematicaModule.drawing();
        if (module == null) return;
        Vec3 camera = HitboxRenderer.cameraPosition(Minecraft.getInstance());
        if (camera == null) return;

        long[] positions = module.labelPositions();
        String[] texts = module.labelTexts();
        int[] colours = module.labelColours();
        float[] lifts = module.labelLifts();
        int n = Math.min(Math.min(positions.length, lifts.length), Math.min(texts.length, colours.length));
        try {
            for (int i = 0; i < n; i++) {
                long packed = positions[i];
                double x = BlockPos.getX(packed) + 0.5 - camera.x;
                double y = BlockPos.getY(packed) + lifts[i] + CLEARANCE - camera.y;
                double z = BlockPos.getZ(packed) + 0.5 - camera.z;
                PoseStack pose = new PoseStack();
                pose.translate(x, y, z);
                pose.scale(SCALE, SCALE, SCALE);
                Component text = Component.literal(texts[i]).withColor(colours[i]);
                gg.spaceclient.compat.NameTags.submit(collector, pose, ATTACH, 0, text, false,
                        FULL_BRIGHT, x * x + y * y + z * z, cameraState);
            }
        } catch (Throwable t) {
            failed = true;
            SpaceClient.LOGGER.error("Schematic labels failed - stopping them", t);
        }
    }
}
