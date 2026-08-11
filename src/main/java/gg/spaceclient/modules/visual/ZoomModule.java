package gg.spaceclient.modules.visual;

import gg.spaceclient.module.Category;
import gg.spaceclient.module.Module;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.IntSetting;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/**
 * Hold C to zoom.
 *
 * The unusual part: SCROLL_STEP lets the zoom factor be adjusted with the
 * scroll wheel *while zoomed*, and the level is remembered per session, so you
 * settle on the magnification you want instead of picking one in a menu.
 * Smoothing eases between levels rather than snapping.
 */
public class ZoomModule extends Module {
    private final IntSetting baseFactor = new IntSetting(
            "factor", "Zoom factor", "How far the default zoom goes", 4, 2, 20);

    private final BooleanSetting smooth = new BooleanSetting(
            "smooth", "Smooth zoom", "Ease in and out instead of snapping", true);

    private final BooleanSetting scrollAdjust = new BooleanSetting(
            "scroll_adjust", "Scroll to adjust", "Change magnification with the wheel while zoomed", true);

    private final BooleanSetting slowSensitivity = new BooleanSetting(
            "slow_sensitivity", "Reduce sensitivity", "Lower mouse sensitivity proportionally while zoomed", true);

    private float sessionFactor = -1;
    private float currentFactor = 1.0f;
    private double savedSensitivity = -1;

    public ZoomModule() {
        super("zoom", "Zoom", "Hold a key to zoom in", Category.VISUAL);
        addSettings(baseFactor, smooth, scrollAdjust, slowSensitivity);
    }

    public boolean isZooming() {
        if (mc.getWindow() == null) return false;
        return InputConstants.isKeyDown(mc.getWindow().getWindow(), GLFW.GLFW_KEY_C);
    }

    public float getCurrentFactor() {
        return Math.max(1.0f, currentFactor);
    }

    public boolean isScrollAdjustEnabled() {
        return scrollAdjust.get();
    }

    /** Called from the scroll hook while zoomed. */
    public void adjustFactor(double amount) {
        if (sessionFactor < 0) sessionFactor = baseFactor.get();
        sessionFactor = (float) Math.max(1.5, Math.min(40, sessionFactor + amount));
    }

    @Override
    public void onTick() {
        float target = 1.0f;
        if (isZooming()) {
            target = sessionFactor > 0 ? sessionFactor : baseFactor.get();
        }

        if (smooth.get()) {
            currentFactor += (target - currentFactor) * 0.35f;
            if (Math.abs(currentFactor - target) < 0.01f) currentFactor = target;
        } else {
            currentFactor = target;
        }

        if (!slowSensitivity.get() || mc.options == null) return;

        if (currentFactor > 1.05f) {
            if (savedSensitivity < 0) {
                savedSensitivity = mc.options.sensitivity().getValue();
            }
            // Scale sensitivity with the magnification so aiming stays usable
            mc.options.sensitivity().setValue(savedSensitivity / currentFactor);
        } else if (savedSensitivity >= 0) {
            mc.options.sensitivity().setValue(savedSensitivity);
            savedSensitivity = -1;
        }
    }

    @Override
    protected void onDisable() {
        currentFactor = 1.0f;
        if (savedSensitivity >= 0 && mc.options != null) {
            mc.options.sensitivity().setValue(savedSensitivity);
            savedSensitivity = -1;
        }
    }
}
