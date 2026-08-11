package gg.spaceclient.modules.hud;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.ColorSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Distance to whatever you are aiming at.
 *
 * The extra bit: it keeps a rolling record of the longest reach you actually
 * landed a hit at this session, which is the number that matters on servers
 * with reach limits - a live crosshair readout tells you nothing about what you
 * connected with a second ago.
 */
public class ReachModule extends HudModule {
    private final BooleanSetting showMax = new BooleanSetting(
            "show_max", "Show session best", "Also print the longest hit you landed", true);

    private final ColorSetting textColor = new ColorSetting(
            "text_color", "Text colour", "Colour of the readout", 0xFFFFFFFF);

    private double current = 0;
    private double sessionMax = 0;
    private boolean wasAttacking = false;

    public ReachModule() {
        super("reach", "Reach Display", "Shows your reach distance in combat", 0.02f, 0.25f);
        addSettings(showMax, textColor);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.crosshairTarget == null) return;

        if (mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {
            Entity target = ((EntityHitResult) mc.crosshairTarget).getEntity();
            current = mc.player.getEyePos().distanceTo(target.getPos().add(0, target.getHeight() / 2, 0));
        } else {
            current = 0;
        }

        boolean attacking = mc.options != null && mc.options.attackKey.isPressed();
        // Record on the click edge, while the target is still under the cursor
        if (attacking && !wasAttacking && current > 0) {
            sessionMax = Math.max(sessionMax, current);
        }
        wasAttacking = attacking;
    }

    private String text() {
        String base = current > 0 ? String.format("%.2f m", current) : "-.-- m";
        if (showMax.get() && sessionMax > 0) {
            base += String.format("  (best %.2f)", sessionMax);
        }
        return base;
    }

    @Override
    public int getWidth() { return mc.textRenderer.getWidth(text()); }

    @Override
    public int getHeight() { return mc.textRenderer.fontHeight; }

    @Override
    public void render(DrawContext context, int x, int y) {
        context.drawText(mc.textRenderer, text(), x, y, textColor.get(), true);
    }
}
