package gg.spaceclient.modules.hud;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.ColorSetting;
import gg.spaceclient.setting.IntSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Counts consecutive hits on the same target.
 *
 * What is new here: the counter also tracks how much health the target actually
 * lost across the combo, so you see "7x (-9.5 HP)". A long combo of weak hits
 * and a short burst of crits look identical on every other client.
 */
public class ComboCounterModule extends HudModule {
    private final IntSetting timeout = new IntSetting(
            "timeout", "Reset after (ticks)", "Combo resets if you do not hit for this long", 60, 20, 200);

    private final BooleanSetting showDamage = new BooleanSetting(
            "show_damage", "Show damage dealt", "Print how much health the target lost", true);

    private final ColorSetting textColor = new ColorSetting(
            "text_color", "Text colour", "Colour of the counter", 0xFF38E0FF);

    private int combo = 0;
    private int ticksSinceHit = 0;
    private float damageDealt = 0;

    private Entity currentTarget;
    private float lastTargetHealth = -1;
    private boolean wasAttacking = false;

    public ComboCounterModule() {
        super("combo", "Combo Counter", "Shows your current hit combo", 0.50f, 0.40f);
        addSettings(timeout, showDamage, textColor);
    }

    @Override
    public void onTick() {
        ticksSinceHit++;
        if (ticksSinceHit > timeout.get()) {
            combo = 0;
            damageDealt = 0;
            currentTarget = null;
        }

        boolean attacking = mc.options != null && mc.options.keyAttack.isDown();
        boolean edge = attacking && !wasAttacking;
        wasAttacking = attacking;

        if (!edge || mc.hitResult == null) return;
        if (mc.hitResult.getType() != HitResult.Type.ENTITY) return;

        Entity target = ((EntityHitResult) mc.hitResult).getEntity();
        if (!(target instanceof LivingEntity living)) return;

        // Switching targets starts a fresh combo
        if (currentTarget != target) {
            currentTarget = target;
            combo = 0;
            damageDealt = 0;
            lastTargetHealth = living.getHealth();
        }

        combo++;
        ticksSinceHit = 0;

        if (lastTargetHealth >= 0) {
            float lost = lastTargetHealth - living.getHealth();
            if (lost > 0) damageDealt += lost;
        }
        lastTargetHealth = living.getHealth();
    }

    private String text() {
        if (combo <= 0) return "";
        String base = combo + "x";
        if (showDamage.get() && damageDealt > 0) {
            base += String.format(" (-%.1f HP)", damageDealt);
        }
        return base;
    }

    @Override
    public int getWidth() { return Math.max(20, mc.font.width(text())); }

    @Override
    public int getHeight() { return mc.font.lineHeight; }

    @Override
    public void render(GuiGraphics context, int x, int y) {
        String text = text();
        if (text.isEmpty()) return;
        context.drawString(mc.font, text, x, y, textColor.get(), true);
    }
}
