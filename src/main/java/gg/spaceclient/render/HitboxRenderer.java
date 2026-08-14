package gg.spaceclient.render;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.modules.HitboxModule;
import gg.spaceclient.util.Reflect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;

/**
 * Draws the hitboxes through 26.2's submit based render pipeline.
 *
 * Everything before this was built on `LevelRenderer.renderLineBox`, which does
 * not exist in this version at all - the render rework replaced it. Geometry is
 * now handed to a SubmitNodeCollector, which is what PolyHitbox does and what
 * this follows: the collector takes a pose, a render type and a callback that
 * writes the vertices.
 */
public final class HitboxRenderer {
    private static Method renderTypeGetter;
    private static boolean lookedUp = false;
    private static boolean warned = false;
    private static boolean available = false;

    public static void setAvailable(boolean value) { available = value; }
    public static boolean isAvailable() { return available; }

    /** Called from the mixin once per frame, after the world's own features. */
    public static void submit(SubmitNodeCollector collector) {
        HitboxModule module = (HitboxModule) SpaceClient.getModuleManager().get("hitbox");
        if (module == null || !module.isEnabled() || !module.anyCategoryOn()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        RenderType lines = lineType();
        if (lines == null) {
            warnOnce("no line render type found");
            return;
        }

        // The camera sits at the origin of the render space, so every box has
        // to be moved by its position.
        Vec3 camera = cameraPosition(mc);
        if (camera == null) {
            warnOnce("camera position unavailable");
            return;
        }

        available = true;

        for (Entity entity : mc.level.entitiesForRendering()) {
            HitboxModule.Category category = module.categoryOf(entity);
            if (!module.isEnabledFor(category)) continue;
            if (entity.distanceTo(mc.player) > module.getRange()) continue;

            AABB box = entity.getBoundingBox().move(-camera.x, -camera.y, -camera.z);
            int argb = module.colorFor(category);
            int width = module.widthFor(category);
            boolean arrow = module.arrowFor(category);

            // Thickness is faked by drawing the outline several times, each a
            // little larger: the pipeline exposes no line width.
            for (int pass = 0; pass < width; pass++) {
                AABB grown = pass == 0 ? box : box.inflate(pass * 0.004);
                submitBox(collector, lines, grown, argb);
            }

            if (arrow) {
                Vec3 eyes = entity.getEyePosition().subtract(camera);
                Vec3 tip = eyes.add(entity.getViewVector(1.0f).scale(2.0));
                submitLine(collector, lines, eyes, tip, 0xFF0000FF);
            }
        }
    }

    /** Twelve edges of a box, written as line pairs. */
    private static void submitBox(SubmitNodeCollector collector, RenderType type,
                                  AABB box, int argb) {
        double x1 = box.minX, y1 = box.minY, z1 = box.minZ;
        double x2 = box.maxX, y2 = box.maxY, z2 = box.maxZ;

        double[][] edges = {
                {x1, y1, z1, x2, y1, z1}, {x2, y1, z1, x2, y1, z2},
                {x2, y1, z2, x1, y1, z2}, {x1, y1, z2, x1, y1, z1},
                {x1, y2, z1, x2, y2, z1}, {x2, y2, z1, x2, y2, z2},
                {x2, y2, z2, x1, y2, z2}, {x1, y2, z2, x1, y2, z1},
                {x1, y1, z1, x1, y2, z1}, {x2, y1, z1, x2, y2, z1},
                {x2, y1, z2, x2, y2, z2}, {x1, y1, z2, x1, y2, z2},
        };

        collector.submitCustomGeometry(new PoseStack(), type, (pose, buffer) -> {
            for (double[] edge : edges) {
                writeLine(buffer, pose, edge[0], edge[1], edge[2], edge[3], edge[4], edge[5], argb);
            }
        });
    }

    private static void submitLine(SubmitNodeCollector collector, RenderType type,
                                   Vec3 from, Vec3 to, int argb) {
        collector.submitCustomGeometry(new PoseStack(), type, (pose, buffer) ->
                writeLine(buffer, pose, from.x, from.y, from.z, to.x, to.y, to.z, argb));
    }

    /**
     * Line render types want a normal per vertex; without one the line is
     * dropped silently, which is a long afternoon if you do not know it.
     */
    private static void writeLine(VertexConsumer buffer, PoseStack.Pose pose,
                                  double x1, double y1, double z1,
                                  double x2, double y2, double z2, int argb) {
        float dx = (float) (x2 - x1);
        float dy = (float) (y2 - y1);
        float dz = (float) (z2 - z1);
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0e-5f) return;

        dx /= length;
        dy /= length;
        dz /= length;

        buffer.addVertex(pose, (float) x1, (float) y1, (float) z1)
                .setColor(argb)
                .setNormal(pose, dx, dy, dz);
        buffer.addVertex(pose, (float) x2, (float) y2, (float) z2)
                .setColor(argb)
                .setNormal(pose, dx, dy, dz);
    }

    /** The render type used for debug style lines, whatever it is called here. */
    private static RenderType lineType() {
        if (lookedUp && renderTypeGetter == null) return null;

        if (!lookedUp) {
            lookedUp = true;
            try {
                Class<?> types = Class.forName(
                        "net.minecraft.client.renderer.rendertype.RenderTypes");
                for (String name : new String[]{"lines", "debugLine", "debugLineStrip", "debugQuads"}) {
                    for (Method method : types.getMethods()) {
                        if (!method.getName().equals(name)) continue;
                        if (method.getParameterCount() != 0) continue;
                        if (!RenderType.class.isAssignableFrom(method.getReturnType())) continue;
                        renderTypeGetter = method;
                        break;
                    }
                    if (renderTypeGetter != null) break;
                }
            } catch (Throwable t) {
                SpaceClient.LOGGER.warn("Could not reach the render types: {}", t.getMessage());
            }
        }

        try {
            return renderTypeGetter == null ? null : (RenderType) renderTypeGetter.invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Vec3 cameraPosition(Minecraft mc) {
        Object camera = Reflect.call(mc.gameRenderer, "getMainCamera", "mainCamera");
        Object position = Reflect.call(camera, "getPosition", "position");
        return position instanceof Vec3 vec ? vec : null;
    }

    private static void warnOnce(String reason) {
        if (warned) return;
        warned = true;
        SpaceClient.LOGGER.warn("Hitboxes cannot be drawn: {}", reason);
    }

    private HitboxRenderer() {}
}
