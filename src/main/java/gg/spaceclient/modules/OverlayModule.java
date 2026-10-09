package gg.spaceclient.modules;

import gg.spaceclient.module.Module;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.ColorSetting;
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
 * underwater and in-wall overlays, where the shield sits in your hand, and the
 * hotbar itself: its size, height, frame, slot numbers and the XP bar.
 *
 * The module only holds the numbers. HudOverlayMixin, ScreenEffectMixin and
 * the first-person item mixin read them where the game draws each piece. With
 * the module off, every one of them draws exactly as vanilla does.
 */
public class OverlayModule extends Module {
    private static OverlayModule instance;

    // --- the bars under the hotbar ---
    private final IntSetting hearts = new IntSetting(
            "heart_size", "Hearts (percent)", "Size of the health hearts; for bigger, use Hotbar size", 100, 50, 110);
    private final IntSetting armor = new IntSetting(
            "armor_size", "Armour bar (percent)", "Size of the armour bar", 100, 50, 110);
    private final IntSetting food = new IntSetting(
            "food_size", "Hunger bar (percent)", "Size of the hunger bar; for bigger, use Hotbar size", 100, 50, 110);
    private final IntSetting air = new IntSetting(
            "air_size", "Air bubbles (percent)", "Size of the air bubbles underwater", 100, 50, 110);

    // --- tints over the whole screen ---
    private final IntSetting pumpkin = new IntSetting(
            "pumpkin", "Pumpkin overlay (percent)", "How visible the carved pumpkin view is; 0 hides it", 100, 0, 100);
    private final IntSetting powderSnow = new IntSetting(
            "powder_snow", "Powder snow overlay (percent)", "How visible the frost border is; 0 hides it", 100, 0, 100);
    private final IntSetting portal = new IntSetting(
            "portal", "Portal overlay (percent)", "How strongly a nether portal tints and warps the screen; 0 turns it off", 100, 0, 100);
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

    // --- camera and hands ---
    private final BooleanSetting hurtCam = new BooleanSetting(
            "hurt_cam", "Camera shake on hit", "The view tilting when you take damage", true);
    private final IntSetting handX = new IntSetting(
            "hand_x", "Hand sideways (100 = normal)", "Move the held items left or right", 100, 0, 200);
    private final IntSetting handY = new IntSetting(
            "hand_y", "Hand height (100 = normal)", "Move the held items up or down - lower for a low sword", 100, 0, 200);
    private final IntSetting handZ = new IntSetting(
            "hand_z", "Hand distance (100 = normal)", "Move the held items closer or further away", 100, 0, 200);
    private final BooleanSetting offhand = new BooleanSetting(
            "offhand", "Off-hand item", "Draw whatever is in your off hand; off clears the left of the view", true);

    // --- HUD text and tools ---
    private final IntSetting bossBar = new IntSetting(
            "boss_bar", "Boss bar (percent)", "Size of boss bars at the top; 0 hides them", 100, 0, 200);
    private final IntSetting titles = new IntSetting(
            "title_size", "Title size (percent)", "Size of the big titles servers show in the middle", 100, 25, 200);
    private final BooleanSetting spyglass = new BooleanSetting(
            "spyglass", "Spyglass overlay", "The black ring while looking through a spyglass", true);
    private final IntSetting nausea = new IntSetting(
            "nausea", "Nausea overlay (percent)", "How strongly nausea warps the view; 0 turns it off", 100, 0, 100);

    // --- hotbar and GUI ---
    private final IntSetting hotbarSize = new IntSetting(
            "hotbar_size", "Hotbar size (percent)", "Size of the hotbar together with the hearts, bars and XP above it", 100, 50, 200);
    private final IntSetting hotbarRaise = new IntSetting(
            "hotbar_raise", "Raise hotbar (pixels)", "Move the hotbar and everything above it up from the bottom edge", 0, 0, 120);
    private final IntSetting hotbarOpacity = new IntSetting(
            "hotbar_opacity", "Hotbar background (percent)", "How visible the hotbar's grey frame is; 0 leaves only the items", 100, 0, 100);
    private final ColorSetting selectionColour = new ColorSetting(
            "selection_color", "Selected slot colour", "Tint of the frame around the selected slot", 0xFFFFFFFF);
    private final BooleanSetting slotNumbers = new BooleanSetting(
            "slot_numbers", "Slot numbers", "Show 1 to 9 on the hotbar slots", false);
    private final BooleanSetting xpBar = new BooleanSetting(
            "xp_bar", "Experience bar", "The green bar above the hotbar", true);
    private final BooleanSetting xpLevel = new BooleanSetting(
            "xp_level", "Experience level", "The green level number", true);
    private final BooleanSetting itemName = new BooleanSetting(
            "item_name", "Held item name", "The name shown above the hotbar when you switch items", true);
    private final IntSetting inventoryDim = new IntSetting(
            "inventory_dim", "Inventory background (percent)", "How much the world darkens behind an open inventory or chest", 100, 0, 100);

    public OverlayModule() {
        super("overlay", "Overlay", "Size of hearts and bars, fire, pumpkin, shield and more", false);
        addGroups(
                SettingGroup.of("Hotbar and GUI", "The hotbar, XP bar and inventory screens",
                        hotbarSize, hotbarRaise, hotbarOpacity, selectionColour, slotNumbers,
                        xpBar, xpLevel, itemName, inventoryDim),
                SettingGroup.of("Hearts and bars", "The bars above the hotbar",
                        hearts, armor, food, air),
                SettingGroup.of("Screen overlays", "Tints the game lays over the view",
                        pumpkin, powderSnow, portal, vignette),
                SettingGroup.of("Fire and first person", "What is drawn in front of your eyes",
                        fireLower, fireOpacity, water, inWall),
                SettingGroup.of("Shield", "Where the shield sits in your hand",
                        shieldHeight, shieldTurn),
                SettingGroup.of("Camera and hands", "Hurt shake and where your items are held",
                        hurtCam, handX, handY, handZ, offhand),
                SettingGroup.of("Boss bar, titles and tools", "Other things laid over the view",
                        bossBar, titles, spyglass, nausea)
        );
        instance = this;
    }

    /** How far, in blocks, the hand and shield sliders move at 0 or 200. */
    private static final float HAND_RANGE = 0.25f;

    private static OverlayModule on() {
        OverlayModule module = instance;
        return module != null && module.isEnabled() ? module : null;
    }

    private static float percent(IntSetting setting) {
        return setting.get() / 100f;
    }

    /**
     * The hearts grow from the left and the food from the right, and the two
     * meet in the middle above the hotbar. Past 110 percent they overlap, so
     * that is the limit - also for a value saved before the limit existed.
     * Everything larger goes through the hotbar size, which scales the whole
     * block together.
     */
    private static float barPercent(IntSetting setting) {
        return Math.min(setting.get(), 110) / 100f;
    }

    // ---------------------------------------------------------------- bars

    public static float heartScale() { var m = on(); return m == null ? 1f : barPercent(m.hearts); }
    public static float armorScale() { var m = on(); return m == null ? 1f : barPercent(m.armor); }
    public static float foodScale() { var m = on(); return m == null ? 1f : barPercent(m.food); }
    public static float airScale() { var m = on(); return m == null ? 1f : barPercent(m.air); }

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

    // ---------------------------------------------------------------- camera and hands

    public static boolean hurtCam() { var m = on(); return m == null || m.hurtCam.get(); }

    /** Whether the off-hand item is drawn at all. */
    public static boolean offhand() { var m = on(); return m == null || m.offhand.get(); }

    /** Shifts every held item by the hand position settings; mirrored for the off hand. */
    public static void hand(PoseStack poseStack, InteractionHand hand) {
        var m = on();
        if (m == null) return;
        // A quarter block either way at the ends of the slider. Half a block,
        // as it was, pushed the item out of the view after a few steps.
        float x = (m.handX.get() - 100) / 100f * HAND_RANGE;
        float y = (m.handY.get() - 100) / 100f * HAND_RANGE;
        float z = (m.handZ.get() - 100) / 100f * HAND_RANGE;
        if (x == 0f && y == 0f && z == 0f) return;
        if (hand == InteractionHand.OFF_HAND) x = -x;
        poseStack.translate(x, y, -z);
    }

    public static boolean movesHands() {
        var m = on();
        return m != null && (m.handX.get() != 100 || m.handY.get() != 100 || m.handZ.get() != 100);
    }

    // ---------------------------------------------------------------- hotbar and GUI

    public static float hotbarScale() { var m = on(); return m == null ? 1f : percent(m.hotbarSize); }
    public static int hotbarRaise() { var m = on(); return m == null ? 0 : m.hotbarRaise.get(); }
    public static float hotbarOpacity() { var m = on(); return m == null ? 1f : percent(m.hotbarOpacity); }
    /** ARGB tint for the selected-slot frame; white leaves it as the game draws it. */
    public static int selectionColour() { var m = on(); return m == null ? 0xFFFFFFFF : m.selectionColour.get(); }
    public static boolean slotNumbers() { var m = on(); return m != null && m.slotNumbers.get(); }
    public static boolean xpBar() { var m = on(); return m == null || m.xpBar.get(); }
    public static boolean xpLevel() { var m = on(); return m == null || m.xpLevel.get(); }
    public static boolean itemName() { var m = on(); return m == null || m.itemName.get(); }
    public static float inventoryDim() { var m = on(); return m == null ? 1f : percent(m.inventoryDim); }

    // ---------------------------------------------------------------- HUD text and tools

    public static float bossBarScale() { var m = on(); return m == null ? 1f : percent(m.bossBar); }
    public static float titleScale() { var m = on(); return m == null ? 1f : percent(m.titles); }
    public static boolean spyglass() { var m = on(); return m == null || m.spyglass.get(); }
    public static float nauseaAlpha() { var m = on(); return m == null ? 1f : percent(m.nausea); }

    /**
     * Moves the shield before it is drawn: up or down in the hand, and turned
     * edge-on towards the middle of the screen. Mirrored for the off hand, so
     * both turn the same way relative to the view.
     */
    public static void shield(PoseStack poseStack, InteractionHand hand) {
        var m = on();
        if (m == null) return;
        float height = (m.shieldHeight.get() - 100) / 100f * HAND_RANGE;
        if (height != 0f) poseStack.translate(0f, height, 0f);
        int turn = m.shieldTurn.get();
        if (turn != 0) {
            float degrees = hand == InteractionHand.OFF_HAND ? turn : -turn;
            poseStack.rotate(Axis.YP.rotationDegrees(degrees));
        }
    }
}
