package gg.spaceclient.modules;

import gg.spaceclient.module.Module;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.IntSetting;

import net.minecraft.client.renderer.state.LightmapRenderState;

import org.joml.Vector3f;

/**
 * Lights the world without night vision.
 *
 * Night vision tints everything blue-white and flickers out at the end of the
 * effect; this does neither. It works on the light map itself - the small
 * table the game looks every light level up in - by raising its floor: every
 * block and sky level comes out at least as bright as the strength set here,
 * so caves and nights read like day while light sources still add on top.
 *
 * LightmapMixin calls apply() right after the game has filled in the light
 * map's inputs for the frame.
 */
public class FullbrightModule extends Module {
    private static FullbrightModule instance;

    private final IntSetting strength = new IntSetting(
            "strength", "Strength (percent)",
            "How bright the darkest places get. 100 is full daylight everywhere", 100, 10, 100);

    private final BooleanSetting gamma = new BooleanSetting(
            "gamma", "Brightest gamma",
            "Also use the game's highest brightness curve, whatever the video setting says", true);

    private final BooleanSetting noDarkness = new BooleanSetting(
            "no_darkness", "Ignore the darkness effect",
            "Keep the world lit when a warden's darkness pulses", true);

    private final BooleanSetting noBossFog = new BooleanSetting(
            "no_boss_darkening", "Ignore boss darkening",
            "Keep the world lit during boss fights that darken the sky", false);

    /** Set for one more frame after switching off, so the light map is rebuilt without us. */
    private volatile boolean refresh = false;

    public FullbrightModule() {
        super("fullbright", "Fullbright", "Lights up caves and nights - not night vision", false);
        addSettings(strength, gamma, noDarkness, noBossFog);
        instance = this;
    }

    @Override
    protected void onEnable() { refresh = true; }

    @Override
    protected void onDisable() { refresh = true; }

    public static void apply(LightmapRenderState state) {
        FullbrightModule module = instance;
        if (module == null) return;
        if (!module.isEnabled()) {
            if (module.refresh) {
                module.refresh = false;
                state.needsUpdate = true;
            }
            return;
        }
        float floor = module.strength.get() / 100f;
        var ambient = state.ambientColor;
        float r = ambient == null ? 0 : ambient.x();
        float g = ambient == null ? 0 : ambient.y();
        float b = ambient == null ? 0 : ambient.z();
        state.ambientColor = new Vector3f(Math.max(r, floor), Math.max(g, floor), Math.max(b, floor));
        if (module.gamma.get()) state.brightness = 1f;
        if (module.noDarkness.get()) state.darknessEffectScale = 0f;
        if (module.noBossFog.get()) state.bossOverlayWorldDarkening = 0f;
        // Rebuilt every frame while on: the inputs above change as the sun
        // moves anyway, and the light map is a 16 by 16 texture
        state.needsUpdate = true;
    }
}
