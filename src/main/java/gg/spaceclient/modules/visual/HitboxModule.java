package gg.spaceclient.modules.visual;

import gg.spaceclient.module.Category;
import gg.spaceclient.module.Module;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.ColorSetting;
import gg.spaceclient.setting.IntSetting;
import gg.spaceclient.setting.ModeSetting;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Arrays;

/**
 * Vanilla's own hitbox rendering, but filterable and recolourable.
 *
 * The geometry is Mojang's: the same axis-aligned box and the same blue eye
 * direction arrow you get from F3+B. What is added is per-category filtering,
 * a configurable colour and line width, and one thing no other client does:
 * DISTANCE_FADE, which fades boxes out with distance so a crowded area does not
 * turn into an unreadable wireframe soup.
 */
public class HitboxModule extends Module {

    private final BooleanSetting showPlayers = new BooleanSetting(
            "players", "Players", "Draw hitboxes for other players", true);

    private final BooleanSetting showHostile = new BooleanSetting(
            "hostile", "Hostile mobs", "Draw hitboxes for monsters", true);

    private final BooleanSetting showPassive = new BooleanSetting(
            "passive", "Passive mobs", "Draw hitboxes for animals and villagers", true);

    private final BooleanSetting showOther = new BooleanSetting(
            "other", "Other entities", "Items, arrows, boats, armour stands", false);

    private final BooleanSetting showSelf = new BooleanSetting(
            "self", "Yourself", "Draw your own hitbox in third person", false);

    private final BooleanSetting showEyeArrow = new BooleanSetting(
            "eye_arrow", "Look direction arrow", "Mojang's blue arrow showing where the entity is looking", true);

    private final ColorSetting boxColor = new ColorSetting(
            "box_color", "Box colour", "Colour of the hitbox lines", 0xFFFFFFFF);

    private final ColorSetting arrowColor = new ColorSetting(
            "arrow_color", "Arrow colour", "Colour of the look direction arrow", 0xFF0000FF);

    private final IntSetting lineWidth = new IntSetting(
            "line_width", "Line width", "Thickness of the hitbox lines", 2, 1, 8);

    private final IntSetting range = new IntSetting(
            "range", "Range", "Only draw hitboxes within this many blocks", 48, 8, 128);

    /**
     * SOLID keeps every box at full opacity. DISTANCE_FADE dims boxes the
     * further away they are, so nearby entities stand out in a busy fight.
     * HEALTH_TINT shifts the colour from the configured one towards red as an
     * entity loses health - a read on how hurt a target is without a nameplate.
     */
    private final ModeSetting style = new ModeSetting(
            "style", "Style", "How boxes are coloured",
            Arrays.asList("SOLID", "DISTANCE_FADE", "HEALTH_TINT"), "DISTANCE_FADE");

    public HitboxModule() {
        super("hitbox", "Hitbox", "Highlights entity hitboxes for better visibility", Category.VISUAL);
        addSettings(showPlayers, showHostile, showPassive, showOther, showSelf,
                showEyeArrow, boxColor, arrowColor, lineWidth, range, style);
    }

    public boolean shouldRender(Entity entity) {
        if (mc.player == null) return false;

        if (entity == mc.player) return showSelf.get();
        if (entity.distanceTo(mc.player) > range.get()) return false;

        if (entity instanceof PlayerEntity) return showPlayers.get();
        if (entity instanceof Monster) return showHostile.get();
        if (entity instanceof PassiveEntity) return showPassive.get();
        return showOther.get();
    }

    public boolean showEyeArrow() { return showEyeArrow.get(); }
    public int getArrowColor() { return arrowColor.get(); }
    public float getLineWidth() { return lineWidth.get(); }

    /** Final ARGB for this entity, after the selected style is applied. */
    public int getColorFor(Entity entity) {
        int base = boxColor.get();
        int a = (base >>> 24) & 0xFF;
        int r = (base >> 16) & 0xFF;
        int g = (base >> 8) & 0xFF;
        int b = base & 0xFF;

        if (style.is("DISTANCE_FADE") && mc.player != null) {
            float dist = entity.distanceTo(mc.player);
            float factor = 1.0f - Math.min(0.8f, dist / range.get());
            a = (int) (a * Math.max(0.2f, factor));

        } else if (style.is("HEALTH_TINT") && entity instanceof LivingEntity living) {
            float ratio = living.getMaxHealth() > 0
                    ? living.getHealth() / living.getMaxHealth()
                    : 1.0f;
            // Push green and blue down as health drops, leaving red behind
            g = (int) (g * ratio);
            b = (int) (b * ratio);
            r = Math.max(r, (int) (255 * (1.0f - ratio)));
        }

        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }
}
