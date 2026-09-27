/*
 * Glue for the WaveyCapes port (see CapeHolder for the attribution).
 * Space Client code, written for Minecraft 26.2.
 */
package gg.spaceclient.wavey;

import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Matrix4f;

/** One full entity vertex, the way the cape renderer writes them. */
public final class VertexConsumerUtil {
    private VertexConsumerUtil() {}

    public static void addVertex(VertexConsumer buffer, Matrix4f matrix, float x, float y, float z,
                                 float u, float v, int overlay, int light,
                                 float nx, float ny, float nz, float alpha) {
        buffer.addVertex(matrix, x, y, z)
                .setColor(1f, 1f, 1f, alpha)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(nx, ny, nz);
    }
}
