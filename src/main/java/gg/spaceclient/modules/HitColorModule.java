package gg.spaceclient.modules;

import gg.spaceclient.access.TintHolder;
import gg.spaceclient.module.Module;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.ColorSetting;
import gg.spaceclient.setting.IntSetting;
import gg.spaceclient.setting.SettingGroup;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Colours entities you hit, and the one currently within your reach.
 *
 * The colour sits on the entity itself - its skin, its fur, the crystal - by
 * way of the same overlay the game uses for its red hurt flash (see HitTint).
 * It used to be a translucent box the size of the hitbox, which read as a box
 * rather than as the entity being hit.
 *
 * Which entities are coloured is a setting per kind. Anything left out keeps
 * the game's own red flash, so switching a kind off never loses the cue.
 *
 * For living entities "hit" means the game's own hurt flash, so the colour
 * lasts exactly as long as the red one would have and only appears when the
 * server agreed the hit landed. Crystals have no hurt state - they break - so
 * for them a swing at one in reach counts, for a moment.
 */
public class HitColorModule extends Module {
    /** How long a swing at a crystal keeps it coloured, if it survives at all. */
    private static final long CRYSTAL_FLASH_MS = 300;

    // --- when you land a hit ---
    private final BooleanSetting hitOn = new BooleanSetting(
            "hit_on", "Show", "Colour an entity when it is hit", true);
    private final ColorSetting hitColor = new ColorSetting(
            "hit_color", "Colour", "Colour of the flash - transparency sets how strong", 0x60FF4444);

    // --- while a target is in range ---
    private final BooleanSetting reachOn = new BooleanSetting(
            "reach_on", "Show", "Colour whatever is within reach", false);
    private final ColorSetting reachColor = new ColorSetting(
            "reach_color", "Colour", "Colour of the reach tint - transparency sets how strong", 0x5038E0FF);
    private final IntSetting reachDistance = new IntSetting(
            "reach_distance", "Reach (tenths of a block)",
            "How far a hit is assumed to land", 30, 20, 60);

    // --- what gets coloured ---
    private final BooleanSetting onPlayers = new BooleanSetting(
            "on_players", "Players", "Other players", true);
    private final BooleanSetting onSelf = new BooleanSetting(
            "on_self", "Yourself", "You, in third person", true);
    private final BooleanSetting onHostile = new BooleanSetting(
            "on_hostile", "Hostile mobs", "Zombies, creepers, endermen and the like", true);
    private final BooleanSetting onPassive = new BooleanSetting(
            "on_passive", "Other mobs", "Animals, villagers, golems, armour stands", true);
    private final BooleanSetting onCrystals = new BooleanSetting(
            "on_crystals", "End crystals", "Crystals, for crystal PvP", true);

    /** Crystals swung at recently, and when. */
    private final Map<Integer, Long> crystalHits = new LinkedHashMap<>();

    private boolean wasAttacking = false;
    private Entity inReach = null;

    public HitColorModule() {
        super("hitcolor", "Hit Colour", "Colours entities you hit or can reach", false);
        addGroups(
                SettingGroup.of("On hit", "The flash when a hit lands",
                        hitOn, hitColor),
                SettingGroup.of("In reach", "The tint while a target is close enough",
                        reachOn, reachColor, reachDistance),
                SettingGroup.of("Colour on", "Which entities get the colour - the rest keep the normal red",
                        onPlayers, onSelf, onHostile, onPassive, onCrystals)
        );
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        Entity target = crosshairEntity();

        // Within reach is decided by distance, since the game's own reach is
        // not exposed to a mod in a form worth relying on
        double reach = reachDistance.get() / 10.0;
        inReach = target != null && target.distanceTo(mc.player) <= reach ? target : null;

        boolean attacking = mc.options != null && mc.options.keyAttack.isDown();
        if (attacking && !wasAttacking && inReach instanceof EndCrystal) {
            crystalHits.put(inReach.getId(), System.currentTimeMillis());
        }
        wasAttacking = attacking;

        // Forget old swings so the map cannot grow without bound
        long cutoff = System.currentTimeMillis() - CRYSTAL_FLASH_MS;
        crystalHits.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }

    /** What the crosshair is on, read from the game's own pick result. */
    private Entity crosshairEntity() {
        Object picked = readField(mc, "crosshairPickEntity");
        return picked instanceof Entity entity ? entity : null;
    }

    private static Object readField(Object target, String name) {
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                var field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    /**
     * How to draw an entity this frame: TintHolder.NONE, HIT or REACH.
     * Called from the renderers once per entity per frame.
     *
     * @param hurt whether the game is showing its red hurt flash on it
     */
    public int kindFor(Entity entity, boolean hurt) {
        if (!isEnabled() || !covers(entity)) return TintHolder.NONE;

        boolean hit = hurt || entity instanceof EndCrystal && crystalHits.containsKey(entity.getId());
        if (hit) {
            // With the hit colour off the game's red flash wins, even over
            // the reach tint: a hit is the more important thing to see
            return hitOn.get() ? TintHolder.HIT : TintHolder.NONE;
        }

        if (reachOn.get() && entity == inReach) return TintHolder.REACH;
        return TintHolder.NONE;
    }

    /** Whether this kind of entity is ticked in the settings. */
    private boolean covers(Entity entity) {
        if (entity instanceof EndCrystal) return onCrystals.get();
        if (entity instanceof Player) {
            return entity == mc.player ? onSelf.get() : onPlayers.get();
        }
        if (entity instanceof Enemy) return onHostile.get();
        if (entity instanceof LivingEntity) return onPassive.get();
        return false;
    }

    public int hitColour() { return hitColor.get(); }

    public int reachColour() { return reachColor.get(); }
}
