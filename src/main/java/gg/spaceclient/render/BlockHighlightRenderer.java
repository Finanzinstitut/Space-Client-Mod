package gg.spaceclient.render;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.modules.BlockHighlightModule;
import gg.spaceclient.util.Reflect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;

/**
 * Draws the selection marker for the block under the crosshair.
 *
 * Rides the same route the hitboxes take - submitted at the tail of the world
 * renderer's own features, through the collector, as quads. That path was
 * worked out once for this version and there is no reason to solve it twice.
 *
 * <h2>The block's real shape, not a cube</h2>
 *
 * Half the blocks worth selecting are not cubes. A slab, a fence post, a chest
 * and a carpet all have their own outline, and a marker drawn as a unit cube
 * around any of them is worse than vanilla's thin line because it is confidently
 * wrong. So the shape is asked for, and only when that cannot be reached does
 * this fall back to a cube - which at least matches for the blocks people spend
 * most of their time pointing at.
 */
public final class BlockHighlightRenderer {

    private static boolean failed = false;
    private static String failure = "";
    private static boolean everDrew = false;

    private BlockHighlightRenderer() {}

    public static boolean hasFailed() { return failed; }

    public static String status() {
        if (failed) return failure;
        return everDrew ? "drawing" : "nothing selected yet";
    }

    // --- what is on screen right now, as opposed to what is aimed at ---

    /**
     * The box being drawn, in world coordinates.
     *
     * World coordinates rather than camera relative on purpose. The camera
     * moves every frame, so easing a camera relative box would chase the
     * camera as well as the target and the marker would lag behind the world
     * while standing still.
     */
    private static AABB shown = null;

    /** The face being shaded, carried alongside so it fades with the rest. */
    private static AABB shownFace = null;

    /** How present the marker is, from gone to fully there. */
    private static float presence = 0f;

    private static long lastFrameNanos = 0L;

    /**
     * How far apart two blocks have to be before the marker jumps instead of
     * travelling.
     *
     * Sliding is right for the block next door - it reads as the same marker
     * moving with your crosshair. Across a room it reads as an object flying
     * through the world, which is worse than a cut, so past this distance the
     * old one fades where it stands and the new one fades in where it is.
     */
    private static final double SLIDE_LIMIT = 2.5;

    /** Called once per frame from the world renderer's tail. */
    public static void submit(SubmitNodeCollector collector) {
        if (failed) return;

        try {
            var manager = SpaceClient.getModuleManager();
            if (manager == null) return;

            var module = manager.get("blockhighlight");
            if (!(module instanceof BlockHighlightModule highlight)) return;
            if (!highlight.wantsOutline() && !highlight.wantsOverlay()) {
                shown = null;
                presence = 0f;
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return;

            AABB target = null;
            AABB targetFace = null;

            Object hit = readField(mc, "hitResult");
            Object kind = hit == null ? null : Reflect.call(hit, "getType");

            if (kind != null && "BLOCK".equalsIgnoreCase(String.valueOf(kind))) {
                Object pos = Reflect.call(hit, "getBlockPos");
                if (pos != null && !isAir(mc, pos)) {
                    target = shapeOf(mc, pos);
                    if (target != null && highlight.facesOnly()) {
                        targetFace = faceOf(target.inflate(0.002), hit);
                    }
                }
            }

            float step = frameStep(highlight);
            advance(target, targetFace, step);

            // Gone completely. Dropped rather than drawn at zero, so a marker
            // that is not there costs nothing at all.
            if (presence <= 0.02f || shown == null) {
                shown = null;
                shownFace = null;
                presence = 0f;
                return;
            }

            Vec3 camera = HitboxRenderer.cameraPosition(mc);
            if (camera == null) return;

            RenderType type = HitboxRenderer.lineType();
            if (type == null) return;

            everDrew = true;
            draw(collector, type, highlight, camera);

        } catch (Throwable t) {
            failed = true;
            failure = "stopped after " + t.getClass().getSimpleName();
            SpaceClient.LOGGER.warn("Block highlight disabled: {}", String.valueOf(t));
        }
    }

    /**
     * How much to move this frame.
     *
     * Worked out from real elapsed time rather than assumed. A fixed step per
     * frame makes the marker crawl at 30fps and snap at 240, and the whole
     * point of easing it is that it should feel the same either way.
     */
    private static float frameStep(BlockHighlightModule highlight) {
        long now = System.nanoTime();
        long elapsed = lastFrameNanos == 0L ? 16_000_000L : now - lastFrameNanos;
        lastFrameNanos = now;

        // Clamped, because a frame that took half a second - a chunk load, a
        // window drag - should not teleport the marker to make up for it
        double seconds = Math.min(0.1, elapsed / 1_000_000_000.0);

        if (!highlight.animates()) return 1f;

        // Speed is "how much of the remaining distance per second"; the
        // exponential keeps that true whatever the frame rate happens to be
        double rate = highlight.animationSpeed();
        return (float) (1.0 - Math.exp(-rate * seconds));
    }

    /** Moves what is shown towards what is aimed at. */
    private static void advance(AABB target, AABB targetFace, float step) {
        if (target == null) {
            // Nothing aimed at: fade where it stands. Its own box is left alone
            // so it shrinks from where it was rather than drifting first.
            presence = presence - step * (presence + 0.15f);
            if (presence < 0f) presence = 0f;
            return;
        }

        if (shown == null || centreDistance(shown, target) > SLIDE_LIMIT) {
            shown = target;
            shownFace = targetFace;
            // Started from nothing rather than from full, so a jump to a
            // distant block still arrives rather than appearing
            if (shown == null) presence = 0f;
        } else {
            shown = ease(shown, target, step);
            shownFace = targetFace == null ? null
                    : (shownFace == null ? targetFace : ease(shownFace, targetFace, step));
        }

        presence = presence + (1f - presence) * step;
        if (presence > 1f) presence = 1f;
    }

    private static double centreDistance(AABB a, AABB b) {
        double dx = (a.minX + a.maxX) / 2 - (b.minX + b.maxX) / 2;
        double dy = (a.minY + a.maxY) / 2 - (b.minY + b.maxY) / 2;
        double dz = (a.minZ + a.maxZ) / 2 - (b.minZ + b.maxZ) / 2;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Moves every edge of a box a fraction of the way to another.
     *
     * Edge by edge rather than centre plus size, so a slab becoming a full
     * block grows upward from the floor it is standing on instead of expanding
     * about its middle and sinking into the ground on the way.
     */
    private static AABB ease(AABB from, AABB to, float step) {
        return new AABB(
                from.minX + (to.minX - from.minX) * step,
                from.minY + (to.minY - from.minY) * step,
                from.minZ + (to.minZ - from.minZ) * step,
                from.maxX + (to.maxX - from.maxX) * step,
                from.maxY + (to.maxY - from.maxY) * step,
                from.maxZ + (to.maxZ - from.maxZ) * step
        );
    }

    /**
     * Shrinks a box towards its own centre.
     *
     * What makes the disappearance read as the marker letting go rather than
     * the screen simply dimming. Paired with the fade it is the difference
     * between something leaving and something being switched off.
     */
    private static AABB shrink(AABB box, double factor) {
        double cx = (box.minX + box.maxX) / 2;
        double cy = (box.minY + box.maxY) / 2;
        double cz = (box.minZ + box.maxZ) / 2;

        double hx = (box.maxX - box.minX) / 2 * factor;
        double hy = (box.maxY - box.minY) / 2 * factor;
        double hz = (box.maxZ - box.minZ) / 2 * factor;

        return new AABB(cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz);
    }

    private static void draw(SubmitNodeCollector collector, RenderType type,
                             BlockHighlightModule highlight, Vec3 camera) {
        // Never all the way to nothing: a marker at four fifths reads as
        // receding, one at a tenth reads as broken
        double scale = 0.82 + 0.18 * presence;
        int alpha = Math.round(255 * presence);

        AABB box = shrink(shown, scale).move(-camera.x, -camera.y, -camera.z);

        if (highlight.wantsOverlay()) {
            AABB shaded = highlight.facesOnly() && shownFace != null
                    ? shrink(shownFace, scale).move(-camera.x, -camera.y, -camera.z)
                    : box.inflate(0.002);

            HitboxRenderer.submitFilled(collector, type, shaded,
                    fade(highlight.overlayColor(), alpha));
        }

        if (highlight.wantsOutline()) {
            double thickness = highlight.edgeThickness();
            HitboxRenderer.submitBox(collector, type,
                    box.inflate(thickness / 2 + 0.001),
                    fade(highlight.outlineColor(), alpha), thickness);
        }
    }

    /** Scales a colour's existing transparency by how present the marker is. */
    private static int fade(int colour, int alpha) {
        int existing = (colour >>> 24) & 0xFF;
        return ((existing * alpha / 255) << 24) | (colour & 0xFFFFFF);
    }

    /**
     * The block's outline in world coordinates.
     *
     * Tries the real shape first. A voxel shape can be several boxes - a fence
     * is a post and up to four arms - and the enclosing box of all of them is
     * a better marker than either one alone, so they are merged.
     */
    private static AABB shapeOf(Minecraft mc, Object pos) {
        double x = intOf(Reflect.call(pos, "getX"));
        double y = intOf(Reflect.call(pos, "getY"));
        double z = intOf(Reflect.call(pos, "getZ"));

        try {
            Object state = Reflect.callWith(mc.level, "getBlockState", pos);
            if (state != null) {
                Object shape = Reflect.callWith(state, "getShape", mc.level, pos);
                if (shape == null) shape = Reflect.call(state, "getOutlineShape");

                if (shape != null) {
                    Object boxes = Reflect.call(shape, "toAabbs");
                    if (boxes instanceof java.util.List<?> list && !list.isEmpty()) {
                        AABB merged = null;
                        for (Object entry : list) {
                            if (!(entry instanceof AABB aabb)) continue;
                            merged = merged == null ? aabb : merged.minmax(aabb);
                        }
                        if (merged != null) return merged.move(x, y, z);
                    }
                }
            }
        } catch (Throwable ignored) {
            // Fall through to the cube
        }

        return new AABB(x, y, z, x + 1, y + 1, z + 1);
    }

    /**
     * Flattens the box onto the side being pointed at.
     *
     * The direction comes off the hit result; without it there is no face to
     * pick and the caller falls back to shading nothing rather than guessing.
     */
    private static AABB faceOf(AABB box, Object hit) {
        Object direction = Reflect.call(hit, "getDirection");
        if (direction == null) return null;

        String name = String.valueOf(direction).toUpperCase(java.util.Locale.ROOT);

        return switch (name) {
            case "DOWN" -> new AABB(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ);
            case "UP" -> new AABB(box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ);
            case "NORTH" -> new AABB(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ);
            case "SOUTH" -> new AABB(box.minX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ);
            case "WEST" -> new AABB(box.minX, box.minY, box.minZ, box.minX, box.maxY, box.maxZ);
            case "EAST" -> new AABB(box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
            default -> null;
        };
    }

    /**
     * Whether there is genuinely nothing at that position.
     *
     * Still needed even though the hit type is checked: the ray can report a
     * block on a position that has since been broken, and an outline around
     * air is the one thing this must never draw.
     */
    private static boolean isAir(Minecraft mc, Object pos) {
        try {
            Object state = Reflect.callWith(mc.level, "getBlockState", pos);
            if (state == null) return false;

            Object air = Reflect.call(state, "isAir");
            return air instanceof Boolean flag && flag;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int intOf(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static Object readField(Object target, String name) {
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }
}
