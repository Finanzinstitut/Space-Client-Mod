package gg.spaceclient.modules;

import gg.spaceclient.module.Module;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.IntSetting;
import gg.spaceclient.setting.SettingGroup;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;

/**
 * Everything the game lays over the view, adjustable in one place: the size of
 * the hearts and bars, how strongly the pumpkin, powder snow and portal tint
 * the screen, the vignette, how high the fire burns in first person, the
 * underwater and in-wall overlays, and where the shield sits in your hand.
 *
 * The module only holds the numbers. HudOverlayMixin, ScreenEffectMixin and
 * the first-person item mixin read them where the game draws each piece. With
 * the module off, every one of them draws exactly as vanilla does.
 */
public class OverlayModule extends Module {
    private static OverlayModule instance;

    // --- the bars under the hotbar ---
    private final IntSetting hearts = new IntSetting(
            "heart_size", "Hearts (percent)", "Size of the health hearts", 100, 50, 200);
    private final IntSetting armor = new IntSetting(
            "armor_size", "Armour bar (percent)", "Size of the armour bar", 100, 50, 200);
    private final IntSetting food = new IntSetting(
            "food_size", "Hunger bar (percent)", "Size of the hunger bar", 100, 50, 200);
    private final IntSetting air = new IntSetting(
            "air_size", "Air bubbles (percent)", "Size of the air bubbles underwater", 100, 50, 200);

    // --- tints over the whole screen ---
    private final IntSetting pumpkin = new IntSetting(
            "pumpkin", "Pumpkin overlay (percent)", "How visible the carved pumpkin view is; 0 hides it", 100, 0, 100);
    private final IntSetting powderSnow = new IntSetting(
            "powder_snow", "Powder snow overlay (percent)", "How visible the frost border is; 0 hides it", 100, 0, 100);
    private final IntSetting portal = new IntSetting(
            "portal", "Portal overlay (percent)", "How strongly a nether portal tints the screen; 0 hides it", 100, 0, 100);
    private final BooleanSetting vignette = new BooleanSetting(
            "vignette", "Vignette", "The darkened screen edges", true);

    // --- first person ---
    private final IntSetting fireLower = new IntSetting(
            "fire_lower", "Lower the fire (percent)", "How far the burning overlay is pushed down; 100 keeps only its tips", 0, 0, 100);
    private final IntSetting fireOpacity = new IntSetting(
            "fire_opacity", "Fire opacity (percent)", "How see-through the burning overlay is", 90, 10, 100);
    private final BooleanSetting water = new BooleanSetting(
            "underwater", "Underwater overlay", "The darkened view under water", true);
    private final BooleanSetting inWall = new BooleanSetting(
            "in_wall", "In-wall overlay", "The block texture shown when your head is inside a block", true);

    // --- the shield ---
    private final IntSetting shieldHeight = new IntSetting(
            "shield_height", "Shield height (100 = normal)", "Lower or raise the shield in your hand", 100, 0, 200);
    private final IntSetting shieldTurn = new IntSetting(
            "shield_turn", "Shield sideways (degrees)", "Turn the shield to the side so it covers less of the view", 0, 0, 90);

    public OverlayModule() {
        super("overlay", "Overlay", "Size of hearts and bars, fire, pumpkin, shield and more", false);
        addGroups(
                SettingGroup.of("Hearts and bars", "The bars above the hotbar",
                        hearts, armor, food, air),
                SettingGroup.of("Screen overlays", "Tints the game lays over the view",
                        pumpkin, powderSnow, portal, vignette),
                SettingGroup.of("Fire and first person", "What is drawn in front of your eyes",
                        fireLower, fireOpacity, water, inWall),
                SettingGroup.of("Shield", "Where the shield sits in your hand",
                        shieldHeight, shieldTurn)
        );
        instance = this;
    }

    private static OverlayModule on() {
        OverlayModule module = instance;
        return module != null && module.isEnabled() ? module : null;
    }

    private static float percent(IntSetting setting) {
        return setting.get() / 100f;
    }

    // ---------------------------------------------------------------- bars

    public static float heartScale() { var m = on(); return m == null ? 1f : percent(m.hearts); }
    public static float armorScale() { var m = on(); return m == null ? 1f : percent(m.armor); }
    public static float foodScale() { var m = on(); return m == null ? 1f : percent(m.food); }
    public static float airScale() { var m = on(); return m == null ? 1f : percent(m.air); }

    // ---------------------------------------------------------------- screen tints

    /**
     * How strongly a full-screen texture overlay is drawn, as a factor on the
     * game's own alpha; 1 when the overlay is not one this module handles.
     */
    public static float textureOverlay(Identifier texture) {
        var m = on();
        if (m == null || texture == null) return 1f;
        String path = texture.getPath();
        if (path.contains("pumpkinblur")) return percent(m.pumpkin);
        if (path.contains("powder_snow")) return percent(m.powderSnow);
        return 1f;
    }

    public static float portalAlpha() { var m = on(); return m == null ? 1f : percent(m.portal); }

    public static boolean vignette() { var m = on(); return m == null || m.vignette.get(); }

    // ---------------------------------------------------------------- first person

    /** How far down the fire is moved, in the overlay's own units (it is one tall). */
    public static float fireLower() { var m = on(); return m == null ? 0f : percent(m.fireLower) * 0.3f; }

    /** The fire's colour with this module's opacity; the game's 0.9 otherwise. */
    public static int fireColour(int vanilla) {
        var m = on();
        if (m == null) return vanilla;
        int alpha = Math.round(percent(m.fireOpacity) * 255);
        return (alpha << 24) | (vanilla & 0xFFFFFF);
    }

    public static boolean underwater() { var m = on(); return m == null || m.water.get(); }

    public static boolean inWall() { var m = on(); return m == null || m.inWall.get(); }

    /**
     * Moves the shield before it is drawn: up or down in the hand, and turned
     * edge-on towards the middle of the screen. Mirrored for the off hand, so
     * both turn the same way relative to the view.
     */
    public static void shield(PoseStack poseStack, InteractionHand hand) {
        var m = on();
        if (m == null) return;
        float height = (m.shieldHeight.get() - 100) / 100f * 0.5f;
        if (height != 0f) poseStack.translate(0f, height, 0f);
        int turn = m.shieldTurn.get();
        if (turn != 0) {
            float degrees = hand == InteractionHand.OFF_HAND ? turn : -turn;
            poseStack.rotate(Axis.YP.rotationDegrees(degrees));
        }
    }
}
