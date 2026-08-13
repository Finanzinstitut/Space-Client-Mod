package gg.spaceclient.render;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.modules.HitboxModule;
import gg.spaceclient.util.Reflect;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Method;
import java.util.Collection;

/**
 * Draws the hitboxes.
 *
 * Everything the world renderer exposes changed in this version's render
 * rewrite, and none of it could be verified, so every call in here goes through
 * reflection. That is deliberate: a wrong guess makes the boxes not appear and
 * writes one line to the log, instead of failing the build and costing another
 * upload-and-wait cycle.
 *
 * The two type names in the signature are the only hard dependencies.
 */
public final class HitboxRenderer {
    private static Method renderLineBox;
    private static boolean lookedUp = false;
    private static boolean warned = false;

    public static void render(WorldRenderContext context) {
        HitboxModule module = (HitboxModule) SpaceClient.getModuleManager().get("hitbox");
        if (module == null || !module.isEnabled() || !module.anyCategoryOn()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Method draw = lineBoxMethod();
        if (draw == null) {
            warnOnce("the line renderer was not found");
            return;
        }

        Object camera = Reflect.call(context, "camera");
        Object cameraPos = Reflect.call(camera, "getPosition", "position");
        Object matrices = Reflect.call(context, "matrixStack", "poseStack", "pose");
        Object consumers = Reflect.call(context, "consumers", "bufferSource");
        if (cameraPos == null || matrices == null || consumers == null) {
            warnOnce("the render context did not hand over what is needed");
            return;
        }

        Object buffer = lineBuffer(consumers);
        if (buffer == null) {
            warnOnce("no line buffer available");
            return;
        }

        double camX = coordinate(cameraPos, "x");
        double camY = coordinate(cameraPos, "y");
        double camZ = coordinate(cameraPos, "z");

        Object entities = Reflect.call(mc.level, "entitiesForRendering", "getEntities");
        if (!(entities instanceof Iterable<?> iterable)) {
            warnOnce("the entity list could not be read");
            return;
        }

        for (Object entity : iterable) {
            if (!(entity instanceof net.minecraft.world.entity.Entity typed)) continue;

            HitboxModule.Category category = module.categoryOf(typed);
            if (!module.isEnabledFor(category)) continue;

            Object distance = Reflect.callWith(typed, "distanceTo", mc.player);
            Double blocks = Reflect.asDouble(distance);
            if (blocks != null && blocks > module.getRange()) continue;

            Object box = Reflect.call(typed, "getBoundingBox");
            if (box == null) continue;
            Object moved = Reflect.callWith(box, "move", -camX, -camY, -camZ);
            if (moved == null) continue;

            int argb = module.colorFor(category);
            float a = ((argb >>> 24) & 0xFF) / 255f;
            float r = ((argb >> 16) & 0xFF) / 255f;
            float g = ((argb >> 8) & 0xFF) / 255f;
            float b = (argb & 0xFF) / 255f;

            // Thickness is faked by drawing the box repeatedly, grown a little
            // each time: the pipeline does not expose line width.
            int width = module.widthFor(category);
            for (int pass = 0; pass < width; pass++) {
                Object passBox = pass == 0
                        ? moved
                        : Reflect.callWith(moved, "inflate", pass * 0.004);
                if (passBox == null) passBox = moved;
                try {
                    draw.invoke(null, matrices, buffer, passBox, r, g, b, a);
                } catch (Throwable t) {
                    warnOnce("the line renderer rejected the call");
                    return;
                }
            }

            if (module.arrowFor(category)) {
                drawLookArrow(draw, matrices, buffer, typed, camX, camY, camZ);
            }
        }
    }

    /**
     * The same idea as Mojang's debug arrow: a thin box running two blocks out
     * from the eyes along the view vector, drawn in blue.
     */
    private static void drawLookArrow(Method draw, Object matrices, Object buffer,
                                      Object entity, double camX, double camY, double camZ) {
        try {
            Object eyes = Reflect.call(entity, "getEyePosition");
            Object look = Reflect.callWith(entity, "getViewVector", 1.0f);
            if (eyes == null || look == null) return;

            double ex = coordinate(eyes, "x") - camX;
            double ey = coordinate(eyes, "y") - camY;
            double ez = coordinate(eyes, "z") - camZ;
            double tx = ex + coordinate(look, "x") * 2;
            double ty = ey + coordinate(look, "y") * 2;
            double tz = ez + coordinate(look, "z") * 2;

            Object line = newBox(
                    Math.min(ex, tx) - 0.01, Math.min(ey, ty) - 0.01, Math.min(ez, tz) - 0.01,
                    Math.max(ex, tx) + 0.01, Math.max(ey, ty) + 0.01, Math.max(ez, tz) + 0.01);
            if (line == null) return;

            draw.invoke(null, matrices, buffer, line, 0f, 0f, 1f, 1f);
        } catch (Throwable ignored) {
            // The boxes already drew; a missing arrow is not worth stopping for
        }
    }

    private static Object newBox(double x1, double y1, double z1,
                                 double x2, double y2, double z2) {
        try {
            Class<?> aabb = Class.forName("net.minecraft.world.phys.AABB");
            return aabb.getConstructor(
                            double.class, double.class, double.class,
                            double.class, double.class, double.class)
                    .newInstance(x1, y1, z1, x2, y2, z2);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Vectors expose x, y and z as fields in some versions and methods in others. */
    private static double coordinate(Object vector, String name) {
        try {
            var field = vector.getClass().getField(name);
            return field.getDouble(vector);
        } catch (Throwable ignored) {
            Double value = Reflect.asDouble(Reflect.call(vector, name));
            return value == null ? 0 : value;
        }
    }

    private static Object lineBuffer(Object consumers) {
        try {
            Class<?> renderType = Class.forName("net.minecraft.client.renderer.RenderType");
            Object lines = null;
            for (Method method : renderType.getMethods()) {
                if (method.getParameterCount() == 0 && method.getName().equals("lines")) {
                    lines = method.invoke(null);
                    break;
                }
            }
            if (lines == null) return null;
            return Reflect.callWith(consumers, "getBuffer", lines);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method lineBoxMethod() {
        if (lookedUp) return renderLineBox;
        lookedUp = true;

        try {
            Class<?> levelRenderer = Class.forName("net.minecraft.client.renderer.LevelRenderer");
            for (Method method : levelRenderer.getMethods()) {
                // The overload taking a whole box plus four colour floats
                if (method.getName().equals("renderLineBox") && method.getParameterCount() == 7) {
                    renderLineBox = method;
                    return renderLineBox;
                }
            }
        } catch (Throwable ignored) {
            // Leaves it null, handled by the caller
        }
        return renderLineBox;
    }

    private static void warnOnce(String reason) {
        if (warned) return;
        warned = true;
        SpaceClient.LOGGER.warn("Hitboxes cannot be drawn on this version: {}", reason);
    }

    private HitboxRenderer() {}
}
