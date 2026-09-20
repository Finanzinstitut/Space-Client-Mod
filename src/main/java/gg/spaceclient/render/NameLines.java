package gg.spaceclient.render;

import gg.spaceclient.util.Reflect;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;

/**
 * Where an extra line above a name tag belongs.
 *
 * <h2>The bug this exists for</h2>
 *
 * The song line used to sit at position.add(0, 0.28, 0) - a world space offset,
 * added before the tag is turned to face the camera. That is the whole problem:
 * what reaches the screen is only the part of the offset that is across the
 * view, so looking down at someone shrinks it by the cosine of the angle.
 *
 * <pre>
 *   looking level     0.280   one clear line
 *   looking down 30   0.242   still just about clear
 *   looking down 45   0.198   less than a line - the song slides into the name
 *   looking down 60   0.140   half covered
 *   looking down 75   0.072   all but gone
 * </pre>
 *
 * A line of tag text is 9 pixels at a scale of 0.025, which is 0.225 in the
 * same units - so from roughly 36 degrees down the lines start to overlap and
 * the song is partly hidden behind the name below it. That matches the report
 * exactly: from some angles it is not fully there.
 *
 * Offsetting along the camera's own up axis instead keeps the gap the same on
 * screen from every angle, because that axis is across the view by definition.
 */
public final class NameLines {

    /** One line of name tag text, with its padding, in world units. */
    public static final double LINE_HEIGHT = 0.28;

    /**
     * The offset that moves a tag this many lines up the screen.
     *
     * Falls back to straight up in the world when the camera cannot be read,
     * which is what this did before - wrong at steep angles, but never worse
     * than it was.
     */
    public static Vec3 up(double lines) {
        Vec3 axis = cameraUp();
        if (axis == null) return new Vec3(0.0, LINE_HEIGHT * lines, 0.0);
        return offset(axis, lines);
    }

    /** Pure geometry, kept separate so it can be checked without a game. */
    public static Vec3 offset(Vec3 axis, double lines) {
        double length = axis.length();
        if (length < 1.0e-6) return new Vec3(0.0, LINE_HEIGHT * lines, 0.0);

        double scale = LINE_HEIGHT * lines / length;
        return new Vec3(axis.x * scale, axis.y * scale, axis.z * scale);
    }

    /**
     * The camera's up axis, in world space.
     *
     * Asked of the camera rather than built from its rotation quaternion on
     * purpose. A quaternion has to be applied in the right direction to mean
     * anything, and getting that backwards would put the song line below the
     * name instead of above it - a mistake that cannot be seen from here. An
     * up vector has one meaning and no convention to get wrong.
     */
    private static Vec3 cameraUp() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.gameRenderer == null) return null;

            Object camera = Reflect.call(mc.gameRenderer, "getMainCamera", "mainCamera");
            if (camera == null) return null;

            Object vector = Reflect.call(camera, "getUpVector", "upVector", "up");
            return readVector(vector);

        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Three numbers out of whatever kind of vector came back.
     *
     * Deliberately untyped: this has been a JOML Vector3f and a Vec3 in
     * different versions, and both carry x, y and z either as fields or as
     * accessors. Naming one of them here would be a guess that costs a build.
     */
    static Vec3 readVector(Object vector) {
        if (vector == null) return null;
        if (vector instanceof Vec3 vec) return vec;

        Double x = number(vector, "x");
        Double y = number(vector, "y");
        Double z = number(vector, "z");
        if (x == null || y == null || z == null) return null;

        return new Vec3(x, y, z);
    }

    private static Double number(Object target, String name) {
        Object value = Reflect.call(target, name);
        if (value instanceof Number found) return found.doubleValue();

        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                Object held = field.get(target);
                return held instanceof Number found ? found.doubleValue() : null;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private NameLines() {}
}
