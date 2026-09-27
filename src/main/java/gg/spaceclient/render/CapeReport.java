package gg.spaceclient.render;

/**
 * What the wavey cape has been doing, for the diagnostics page.
 *
 * Three separate proofs, because each fails in its own way: the layer being
 * added (the renderer mixin applied), the simulation stepping (the tick mixin
 * applied and the module is on), and the layer drawing (a cape reached it).
 */
public final class CapeReport {
    private static volatile boolean layerAdded = false;
    private static volatile long simulated = 0;
    private static volatile long submitted = 0;
    private static volatile String failure = "";

    private CapeReport() {}

    public static void layerAdded() { layerAdded = true; }
    public static void simulated() { simulated++; }
    public static void submitted() { submitted++; }

    public static void failed(Throwable t) {
        failure = t.getClass().getSimpleName() + ": " + t.getMessage();
    }

    public static boolean hooked() { return layerAdded; }

    public static String status() {
        if (!layerAdded) return "cape layer was never added - the renderer mixin did not apply";
        if (!failure.isEmpty()) return "simulation failed: " + failure;
        return "layer added, simulated " + simulated + " ticks, drawn " + submitted + " times";
    }

    public static boolean sawSelfYet() { return submitted > 0; }

    public static String stateReport() {
        return submitted > 0 ? "a cape has been drawn by the wavey layer"
                : "no cape drawn yet - wear a cape with the module on";
    }
}
