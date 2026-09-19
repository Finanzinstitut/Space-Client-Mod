package gg.spaceclient.modules;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.module.Module;
import gg.spaceclient.render.HitboxRenderer;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.ColorSetting;
import gg.spaceclient.setting.IntSetting;
import gg.spaceclient.setting.SettingGroup;

import net.minecraft.world.entity.Entity;

/**
 * Hitboxes with per-category control.
 *
 * Four categories, each with its own on/off switch, colour and line width:
 * yourself, other players, mobs and items. On top of that the look-direction
 * arrows can be turned off separately, since they are useful on players and
 * mostly clutter on dropped items.
 *
 * <h2>Through walls, and invisible entities</h2>
 *
 * Both are settings, both start off. They were fixed off for a while and are
 * switches again because that was asked for, and the honest note belongs with
 * them rather than in a refusal: with either of them on this stops being a
 * debug overlay and becomes ESP, which is what most servers ban for. Off, it
 * shows what the game's own F3+B shows - boxes around things already on screen.
 *
 * One line is held either way: a player in spectator mode is never drawn. That
 * is the staff seat, it is hidden from everyone, and revealing it is the one
 * use of this that is aimed at a person rather than at the game.
 *
 * The drawing itself lives in HitboxRenderer; this class is only the settings
 * and the decision of which category an entity falls into.
 */
public class HitboxModule extends Module {

    public enum Category { SELF, PLAYERS, MOBS, ITEMS }

    // --- yourself ---
    private final BooleanSetting selfOn = new BooleanSetting(
            "self_on", "Show", "Draw your own hitbox in third person", false);
    private final ColorSetting selfColor = new ColorSetting(
            "self_color", "Colour", "Colour of your own box", 0xFF38E0FF);
    private final IntSetting selfWidth = new IntSetting(
            "self_width", "Line width", "Thickness in pixels", 2, 1, 8);

    // --- other players ---
    private final BooleanSetting playersOn = new BooleanSetting(
            "players_on", "Show", "Draw hitboxes for other players", true);
    private final ColorSetting playersColor = new ColorSetting(
            "players_color", "Colour", "Colour of player boxes", 0xFFFFFFFF);
    private final IntSetting playersWidth = new IntSetting(
            "players_width", "Line width", "Thickness in pixels", 2, 1, 8);

    // --- mobs ---
    private final BooleanSetting mobsOn = new BooleanSetting(
            "mobs_on", "Show", "Draw hitboxes for mobs", true);
    private final ColorSetting mobsColor = new ColorSetting(
            "mobs_color", "Colour", "Colour of mob boxes", 0xFFFFD9A0);
    private final IntSetting mobsWidth = new IntSetting(
            "mobs_width", "Line width", "Thickness in pixels", 2, 1, 8);

    // --- items and everything else ---
    private final BooleanSetting itemsOn = new BooleanSetting(
            "items_on", "Show", "Draw hitboxes for items, arrows and the rest", false);
    private final ColorSetting itemsColor = new ColorSetting(
            "items_color", "Colour", "Colour of item boxes", 0xFF9A95C9);
    private final IntSetting itemsWidth = new IntSetting(
            "items_width", "Line width", "Thickness in pixels", 1, 1, 8);

    // --- shared ---
    private final BooleanSetting showArrows = new BooleanSetting(
            "arrows", "Look direction arrows", "Draw the arrow showing where an entity looks", true);
    private final BooleanSetting arrowsPlayersOnly = new BooleanSetting(
            "arrows_players_only", "Arrows on players only",
            "Skip the arrows on mobs and items, where they mostly add clutter", true);
    private final IntSetting range = new IntSetting(
            "range", "Range", "Only draw within this many blocks", 48, 8, 128);

    private final BooleanSetting throughWalls = new BooleanSetting(
            "through_walls", "Through walls",
            "Draw boxes for entities behind blocks. Counts as ESP - most servers ban it",
            false);
    private final BooleanSetting invisible = new BooleanSetting(
            "invisible", "Invisible entities",
            "Draw boxes around entities the game is hiding. Counts as ESP - most servers ban it",
            false);

    public HitboxModule() {
        super("hitbox", "Hitbox", "Draws entity hitboxes with per-category control", false);

        addSettings(showArrows, arrowsPlayersOnly, range, throughWalls, invisible);
        addGroups(
                SettingGroup.of("Yourself", "Your own hitbox in third person",
                        selfOn, selfColor, selfWidth),
                SettingGroup.of("Other players", "Hitboxes of other players",
                        playersOn, playersColor, playersWidth),
                SettingGroup.of("Mobs", "Hostile and passive mobs",
                        mobsOn, mobsColor, mobsWidth),
                SettingGroup.of("Items", "Dropped items, arrows, boats and the rest",
                        itemsOn, itemsColor, itemsWidth)
        );
    }

    /** Which bucket an entity falls into. */
    public Category categoryOf(Entity entity) {
        if (mc.player != null && entity == mc.player) return Category.SELF;
        // A living entity that is not the player is either another player or a mob
        if (entity instanceof net.minecraft.world.entity.player.Player) return Category.PLAYERS;
        if (entity instanceof net.minecraft.world.entity.LivingEntity) return Category.MOBS;
        return Category.ITEMS;
    }

    public boolean isEnabledFor(Category category) {
        return switch (category) {
            case SELF -> selfOn.get();
            case PLAYERS -> playersOn.get();
            case MOBS -> mobsOn.get();
            case ITEMS -> itemsOn.get();
        };
    }

    public int colorFor(Category category) {
        return switch (category) {
            case SELF -> selfColor.get();
            case PLAYERS -> playersColor.get();
            case MOBS -> mobsColor.get();
            case ITEMS -> itemsColor.get();
        };
    }

    public int widthFor(Category category) {
        return switch (category) {
            case SELF -> selfWidth.get();
            case PLAYERS -> playersWidth.get();
            case MOBS -> mobsWidth.get();
            case ITEMS -> itemsWidth.get();
        };
    }

    public boolean arrowFor(Category category) {
        if (!showArrows.get()) return false;
        if (!arrowsPlayersOnly.get()) return true;
        return category == Category.SELF || category == Category.PLAYERS;
    }

    public int getRange() { return range.get(); }

    /**
     * Whether a box is skipped when the entity cannot be seen.
     *
     * The render type in use, debugQuads, has no depth test - so with this off
     * "through walls" is literal rather than a matter of draw order.
     */
    public boolean hideBehindWalls() { return !throughWalls.get(); }

    public boolean showInvisible() { return invisible.get(); }

    // --- fallback ---------------------------------------------------------
    // When the world render event is unavailable, custom boxes cannot be drawn.
    // Rather than the module doing nothing at all, the game's own hitbox view is
    // switched on: no per-category colours, but the boxes and arrows are there.

    private java.lang.reflect.Field vanillaFlag;
    private boolean flagLookedUp = false;
    private boolean flagWarned = false;

    private java.lang.reflect.Field vanillaFlag() {
        if (flagLookedUp) return vanillaFlag;
        flagLookedUp = true;

        Object dispatcher = gg.spaceclient.util.Reflect.call(
                mc, "getEntityRenderDispatcher");
        if (dispatcher == null) return null;

        // By name first. "The dispatcher's only plain boolean" was wrong: it
        // carries shouldRenderShadow as well, and that one is declared first -
        // so the old lookup grabbed the shadows and switching the module off
        // turned every entity shadow off with it.
        try {
            java.lang.reflect.Field named = dispatcher.getClass().getDeclaredField("renderHitBoxes");
            if (named.getType() == boolean.class) {
                named.setAccessible(true);
                vanillaFlag = named;
                return named;
            }
        } catch (Throwable ignored) {
            // Renamed or remapped on this version; fall through
        }

        for (java.lang.reflect.Field field : dispatcher.getClass().getDeclaredFields()) {
            if (field.getType() != boolean.class) continue;
            if (field.getName().toLowerCase(java.util.Locale.ROOT).contains("shadow")) continue;
            field.setAccessible(true);
            vanillaFlag = field;
            return field;
        }
        return null;
    }

    private void setVanillaHitboxes(boolean value) {
        try {
            java.lang.reflect.Field field = vanillaFlag();
            Object dispatcher = gg.spaceclient.util.Reflect.call(
                    mc, "getEntityRenderDispatcher");
            if (field == null || dispatcher == null) {
                if (!flagWarned) {
                    flagWarned = true;
                    SpaceClient.LOGGER.warn("Hitbox fallback unavailable on this version");
                }
                return;
            }
            field.set(dispatcher, value);
        } catch (Throwable ignored) {
            // Nothing more to try; the module simply shows nothing
        }
    }

    /** Whether the game's own hitbox view was switched on by this module. */
    private boolean forcedVanilla = false;

    /**
     * The fallback only runs if the renderer never reported itself working -
     * that is, if the mixin did not attach on this version. Otherwise the
     * custom boxes are the real ones and switching the game's own view on as
     * well would just draw everything twice.
     *
     * Asked for once, not every tick, and that is the fix for F3+B. Setting the
     * flag twenty times a second meant the debug key could not turn the boxes
     * off: the key flipped the flag and the next tick flipped it straight back,
     * so a key the game owns looked broken while this module was on.
     */
    @Override
    public void onTick() {
        if (HitboxRenderer.isAvailable()) return;
        if (forcedVanilla) return;

        forcedVanilla = true;
        setVanillaHitboxes(true);
    }

    @Override
    protected void onEnable() {
        forcedVanilla = false;
    }

    @Override
    protected void onDisable() {
        // Only put back what this module turned on. Switching it off blindly
        // would take away a view the player had chosen themselves with F3+B.
        if (forcedVanilla) {
            setVanillaHitboxes(false);
            forcedVanilla = false;
        }
    }

    /** True when at least one category is switched on. */
    public boolean anyCategoryOn() {
        return selfOn.get() || playersOn.get() || mobsOn.get() || itemsOn.get();
    }
}
