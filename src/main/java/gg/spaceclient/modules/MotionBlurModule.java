package gg.spaceclient.modules;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.module.Module;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.IntSetting;
import gg.spaceclient.setting.SettingGroup;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Motion blur: every frame is mixed with the one shown before it, so turning
 * the camera and things moving across the screen leave a short fading trail.
 *
 * <h2>Made for PvP</h2>
 *
 * The trail is measured in time, not in frames. Mixing a fixed share of the
 * last frame would give a long smear at 60 FPS and almost nothing at 240, so
 * the share is worked out from the frame rate: the strength is what one frame
 * keeps at 60 FPS, and at a higher rate each frame keeps more, so the trail
 * fades out after the same fraction of a second either way. It is also capped
 * well short of a full smear - at any setting the newest frame stays in charge
 * of the picture, so a target never turns into a ghost you aim at by mistake.
 * And it only touches the world: the crosshair and the HUD stay sharp.
 *
 * <h2>How</h2>
 *
 * A post effect with a persistent target holding the last shown frame. The
 * mix amount is baked into the effect file, one file per five percent, and the
 * frame-rate step is chosen from the once-a-second FPS reading, so it changes
 * at most once a second; when it does, the new effect's history is cleared
 * first, so it never shows a stale frame from the last time it was used.
 */
public class MotionBlurModule extends Module {
    private static MotionBlurModule instance;

    private final IntSetting strength = new IntSetting(
            "strength", "Strength (percent)", "How long the trail is; 30 to 50 is subtle enough for PvP", 40, 5, 80);
    private final BooleanSetting frameRateAware = new BooleanSetting(
            "fps_aware", "Same at any FPS", "Keep the trail the same length whatever your frame rate", true);
    private final BooleanSetting skipGui = new BooleanSetting(
            "pause_in_menus", "Off in menus", "No blur while an inventory or menu is open", true);

    /** Above this a frame is mostly the past - unreadable in a fight. */
    private static final int MAX_STEP = 90;

    private int activeStep = -1;
    private static String status = "off";

    public MotionBlurModule() {
        super("motionblur", "Motion Blur", "Smooth trail when turning and moving, tuned for PvP", false);
        addGroups(SettingGroup.of("Blur", "How strong and when",
                strength, frameRateAware, skipGui));
        instance = this;
    }

    public static String status() { return status; }

    @Override
    protected void onDisable() {
        activeStep = -1;
        status = "off";
    }

    /** Called every frame from the game renderer, after its own effects are listed. */
    public static void contribute(List<Identifier> requested) {
        MotionBlurModule module = instance;
        if (module == null || !module.isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (module.skipGui.get() && mc.gui.screen() != null) {
            // Leaving a menu should not show the frame from before it opened
            module.activeStep = -1;
            status = "paused in a menu";
            return;
        }

        int step = module.stepFor(mc.getFps());
        if (step != module.activeStep) {
            freshHistory(mc, idFor(step));
            module.activeStep = step;
        }
        requested.add(idFor(step));
        status = "active, " + step + "% per frame";
    }

    /** The share of the last frame to keep, snapped to the effect files' five percent steps. */
    private int stepFor(int fps) {
        double atSixty = strength.get() / 100.0;
        double keep = atSixty;
        if (frameRateAware.get() && fps > 0) {
            // Same fade per second: keep^fps == atSixty^60
            keep = Math.pow(atSixty, 60.0 / Math.max(20, fps));
        }
        int step = (int) Math.round(keep * 20) * 5;
        return Math.max(5, Math.min(MAX_STEP, step));
    }

    private static Identifier idFor(int step) {
        return Identifier.fromNamespaceAndPath(SpaceClient.MOD_ID, "motion_blur_" + step);
    }

    private static void freshHistory(Minecraft mc, Identifier id) {
        try {
            PostChain chain = mc.getShaderManager().getPostChain(id, LevelTargetBundle.MAIN_TARGETS);
            if (chain != null) chain.closePersistentTargets();
        } catch (Throwable ignored) {
            // At worst one stale frame shows through for a moment
        }
    }
}
