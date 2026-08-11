package gg.spaceclient.modules.hud;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.IntSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;

import java.util.ArrayList;
import java.util.List;

/**
 * Countdown for primed TNT nearby.
 *
 * What is new: it also works out whether you are inside the blast radius and
 * says so, instead of just counting down. Knowing there are 1.4 seconds left is
 * only useful if you also know whether it matters where you are standing.
 */
public class TntTimerModule extends HudModule {
    private static final double BLAST_RADIUS = 8.0;

    private final IntSetting range = new IntSetting(
            "range", "Range", "Only track TNT within this many blocks", 32, 8, 96);

    private final BooleanSetting warnInRange = new BooleanSetting(
            "warn_in_range", "Warn when in blast range", "Highlight TNT that can actually reach you", true);

    public TntTimerModule() {
        super("tnttimer", "TNT Timer", "Shows when nearby TNT explodes", 0.45f, 0.20f);
        addSettings(range, warnInRange);
    }

    private record Primed(float seconds, double distance, boolean dangerous) {}

    private List<Primed> nearby() {
        List<Primed> out = new ArrayList<>();
        if (mc.level == null || mc.player == null) return out;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof PrimedTnt tnt)) continue;

            double distance = tnt.distanceTo(mc.player);
            if (distance > range.get()) continue;

            out.add(new Primed(tnt.getFuse() / 20.0f, distance, distance <= BLAST_RADIUS));
        }
        out.sort((a, b) -> Float.compare(a.seconds(), b.seconds()));
        return out;
    }

    @Override
    public int getWidth() { return 120; }

    @Override
    public int getHeight() {
        int count = nearby().size();
        return Math.max(mc.font.lineHeight, count * (mc.font.lineHeight + 2));
    }

    @Override
    public void render(GuiGraphics context, int x, int y) {
        int offset = 0;
        for (Primed tnt : nearby()) {
            boolean danger = warnInRange.get() && tnt.dangerous();
            String text = String.format("TNT %.1fs  %.0fm%s",
                    tnt.seconds(), tnt.distance(), danger ? "  IN RANGE" : "");
            int color = danger ? 0xFFFF6B81 : 0xFFFFD9A0;
            context.drawString(mc.font, text, x, y + offset, color, true);
            offset += mc.font.lineHeight + 2;
        }
    }
}
