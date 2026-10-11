/*
 * Glue for the WaveyCapes port (see CapeHolder for the attribution).
 * Space Client code, written for Minecraft 26.2.
 */
package gg.spaceclient.wavey;

/** The few maths helpers the simulation uses, in plain Java. */
public final class WaveyMth {
    private WaveyMth() {}

    public static float sin(float value) { return (float) Math.sin(value); }
    public static float cos(float value) { return (float) Math.cos(value); }
    public static float sqrt(float value) { return (float) Math.sqrt(value); }
    public static double atan(double value) { return Math.atan(value); }
    public static double atan2(double y, double x) { return Math.atan2(y, x); }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    public static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    public static int clamp(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }

    public static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    public static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    /** Degrees, wrapped to -180..180. */
    public static float wrapDegrees(float value) {
        float wrapped = value % 360.0F;
        if (wrapped >= 180.0F) wrapped -= 360.0F;
        if (wrapped < -180.0F) wrapped += 360.0F;
        return wrapped;
    }

    /** Interpolates between two angles the short way round. */
    public static float rotLerp(float delta, float start, float end) {
        return start + delta * wrapDegrees(end - start);
    }
}
