package gg.spaceclient.modules;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.module.Module;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.IntSetting;
import gg.spaceclient.setting.ModeSetting;
import gg.spaceclient.setting.SettingGroup;

import net.minecraft.resources.Identifier;

import java.util.Arrays;
import java.util.List;

/**
 * Shifts how strong the world's colours are, from grey to oversaturated.
 *
 * This is not a HUD element and draws nothing of its own. It is a post
 * processing pass: after the world has been rendered, every pixel is pulled
 * towards or away from its own brightness. The weights are Mojang's own
 * (0.3, 0.59, 0.11), so a fully desaturated world looks the same as the game's
 * built-in greyscale effects.
 *
 * <h2>How it reaches the game</h2>
 *
 * Since 26.3 the game no longer holds one post effect that is set and cleared.
 * Every frame GameRenderer.update builds a fresh list of the effects it wants -
 * the end-of-frame pass, the player's, a spectated entity's - and applies them
 * in order. GameRendererPostMixin adds ours to that list right after it is
 * built, so there is nothing to keep reapplying and nothing for the game to
 * clear: switch the module off and the next frame simply does not ask for it.
 *
 * <h2>Why fixed steps rather than a free slider</h2>
 *
 * The strength lives in the effect's JSON as a uniform, read once when the
 * chain is loaded. So the mod ships one small JSON per step and picks between
 * them. At five percent apart the slider reads as continuous.
 */
public class SaturationModule extends Module {
    private static final int STEP = 5;
    private static final int MAX = 200;

    private static SaturationModule instance;

    private final IntSetting level = new IntSetting(
            "level", "Saturation (percent)",
            "100 leaves colours alone, 0 is greyscale, above 100 pushes them", 100, 0, MAX);

    private final ModeSetting preset = new ModeSetting(
            "preset", "Preset", "Jump to a strength, or leave on Custom to use the slider",
            Arrays.asList("CUSTOM", "GREYSCALE", "FADED", "MUTED", "NEUTRAL", "RICH", "VIVID", "INTENSE"),
            "CUSTOM");

    private final BooleanSetting respectVanilla = new BooleanSetting(
            "respect_vanilla", "Yield to game effects",
            "Leave the colours alone while the game shows an effect of its own, like spectating a creeper", true);

    private final BooleanSetting skipSpectator = new BooleanSetting(
            "skip_spectator", "Off while spectating",
            "Leave colours alone in spectator mode", false);

    private String lastPreset = "CUSTOM";
    private static volatile String status = "not applied";

    public static String lastResult() { return status; }

    public SaturationModule() {
        super("saturation", "Saturation", "Shifts how strong the world's colours are", false);
        addGroups(
                SettingGroup.of("Strength", "How far the colours are pushed",
                        preset, level),
                SettingGroup.of("Behaviour", "How the effect shares the screen with the game",
                        respectVanilla, skipSpectator)
        );
        instance = this;
    }

    private static int presetLevel(String name) {
        return switch (name) {
            case "GREYSCALE" -> 0;
            case "FADED" -> 35;
            case "MUTED" -> 65;
            case "NEUTRAL" -> 100;
            case "RICH" -> 130;
            case "VIVID" -> 160;
            case "INTENSE" -> 200;
            default -> -1;
        };
    }

    private static int snap(int value) {
        int clamped = Math.max(0, Math.min(MAX, value));
        return Math.round(clamped / (float) STEP) * STEP;
    }

    private static Identifier idFor(int step) {
        return Identifier.fromNamespaceAndPath(SpaceClient.MOD_ID, "saturation_" + step);
    }

    @Override
    public void onTick() {
        // A preset is a jump: it sets the slider and goes back to Custom, so the
        // slider stays the one place the strength is read from
        String chosen = preset.get();
        if (!chosen.equals(lastPreset)) {
            lastPreset = chosen;
            int wanted = presetLevel(chosen);
            if (wanted >= 0) {
                level.set(wanted);
                preset.set("CUSTOM");
                lastPreset = "CUSTOM";
            }
        }
    }

    @Override
    protected void onDisable() {
        status = "off";
    }

    /**
     * Called by GameRendererPostMixin once a frame with the list the game has
     * just built; adds the saturation pass when it is wanted.
     *
     * @param vanillaEffects how many entries the game itself put there beyond
     *                       the end-of-frame pass - more than none means a
     *                       creeper view or similar is showing
     */
    public static void contribute(List<Identifier> requested, int vanillaEffects) {
        SaturationModule module = instance;
        if (module == null || !module.isEnabled()) return;

        int step = snap(module.level.get());
        if (step == 100) {
            status = "neutral";
            return;
        }
        if (module.skipSpectator.get() && mc.player != null && mc.player.isSpectator()) {
            status = "paused while spectating";
            return;
        }
        if (module.respectVanilla.get() && vanillaEffects > 0) {
            status = "yielded to a game effect";
            return;
        }
        requested.add(idFor(step));
        status = "active at " + step + "%";
    }
}
