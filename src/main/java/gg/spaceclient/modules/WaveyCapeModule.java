package gg.spaceclient.modules;

import gg.spaceclient.module.Module;
import gg.spaceclient.setting.IntSetting;
import gg.spaceclient.setting.ModeSetting;
import gg.spaceclient.wavey.CapeMovement;
import gg.spaceclient.wavey.CapeStyle;
import gg.spaceclient.wavey.WaveyCapes;
import gg.spaceclient.wavey.WindMode;

import java.util.Arrays;

/**
 * Capes that move like cloth: WaveyCapes by tr7zw.
 *
 * The simulation and the cape renderer are WaveyCapes' own (see the
 * gg.spaceclient.wavey package and THIRD-PARTY-WaveyCapes-LICENSE.txt); this
 * module is only its settings screen. The options and their ranges are the
 * ones WaveyCapes offers, with its defaults.
 */
public class WaveyCapeModule extends Module {

    private final ModeSetting movement = new ModeSetting(
            "movement", "Movement",
            "SIMULATION_V2: WaveyCapes' current physics. SIMULATION: the older one. "
                    + "DUNGEONS: the Minecraft Dungeons look. CLASSIC: vanilla's swing on a smooth cape",
            Arrays.asList("SIMULATION_V2", "SIMULATION", "DUNGEONS", "CLASSIC"), "SIMULATION_V2");

    private final ModeSetting style = new ModeSetting(
            "style", "Cape style", "SMOOTH: one bending cloth. BLOCKY: sixteen stacked strips",
            Arrays.asList("SMOOTH", "BLOCKY"), "SMOOTH");

    private final ModeSetting wind = new ModeSetting(
            "wind", "Wind", "WAVES: a gentle wave runs down the cape even standing still",
            Arrays.asList("NONE", "WAVES"), "NONE");

    private final IntSetting gravity = new IntSetting(
            "gravity", "Gravity", "How strongly the cape is pulled down", 25, 5, 32);

    private final IntSetting height = new IntSetting(
            "height_multiplier", "Height multiplier",
            "How much the cape reacts to moving up and down", 6, 4, 16);

    public WaveyCapeModule() {
        super("waveycape", "Wavey Cape",
                "Capes that move like cloth - WaveyCapes by tr7zw", false);
        addSettings(movement, style, wind, gravity, height);
    }

    @Override
    protected void onEnable() {
        apply();
        WaveyCapes.enabled = !WaveyCapes.EXTERNAL;
    }

    @Override
    protected void onDisable() {
        WaveyCapes.enabled = false;
    }

    @Override
    public void onTick() {
        // Every tick is cheap and means a changed setting takes at once
        apply();
        WaveyCapes.enabled = !WaveyCapes.EXTERNAL;
    }

    private void apply() {
        var config = WaveyCapes.config;
        config.capeMovement = switch (movement.get()) {
            case "SIMULATION" -> CapeMovement.BASIC_SIMULATION;
            case "DUNGEONS" -> CapeMovement.DUNGEONS;
            case "CLASSIC" -> CapeMovement.VANILLA;
            default -> CapeMovement.BASIC_SIMULATION_3D;
        };
        config.capeStyle = style.is("BLOCKY") ? CapeStyle.BLOCKY : CapeStyle.SMOOTH;
        config.windMode = wind.is("WAVES") ? WindMode.WAVES : WindMode.NONE;
        config.gravity = gravity.get();
        config.heightMultiplier = height.get();
    }
}
