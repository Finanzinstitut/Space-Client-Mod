package gg.spaceclient.modules.hud;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.IntSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.List;

/**
 * Active potion effects and their remaining time.
 *
 * The useful addition: effects about to expire start blinking at a configurable
 * threshold, and they are sorted by remaining time rather than by registry
 * order - so the one you need to re-drink is always at the top.
 */
public class PotionStatusModule extends HudModule {
    private final BooleanSetting sortByTime = new BooleanSetting(
            "sort_by_time", "Sort by remaining time", "Shortest remaining effect first", true);

    private final BooleanSetting blinkWhenLow = new BooleanSetting(
            "blink", "Blink when expiring", "Flash effects that are about to run out", true);

    private final IntSetting blinkThreshold = new IntSetting(
            "blink_threshold", "Blink below (seconds)", "When to start flashing", 10, 3, 60);

    private final BooleanSetting hideAmbient = new BooleanSetting(
            "hide_ambient", "Hide beacon effects", "Skip ambient effects from beacons", false);

    public PotionStatusModule() {
        super("potionstatus", "Potion Status", "Shows active effects and their remaining time", 0.85f, 0.15f);
        addSettings(sortByTime, blinkWhenLow, blinkThreshold, hideAmbient);
    }

    private List<StatusEffectInstance> effects() {
        List<StatusEffectInstance> list = new ArrayList<>();
        if (mc.player == null) return list;

        for (StatusEffectInstance effect : mc.player.getStatusEffects()) {
            if (hideAmbient.get() && effect.isAmbient()) continue;
            list.add(effect);
        }
        if (sortByTime.get()) {
            list.sort((a, b) -> Integer.compare(a.getDuration(), b.getDuration()));
        }
        return list;
    }

    private String format(StatusEffectInstance effect) {
        String name = Registries.STATUS_EFFECT.getId(effect.getEffectType().value()) != null
                ? Registries.STATUS_EFFECT.getId(effect.getEffectType().value()).getPath().replace('_', ' ')
                : "effect";

        int seconds = effect.getDuration() / 20;
        String time = effect.isInfinite()
                ? "**"
                : String.format("%d:%02d", seconds / 60, seconds % 60);

        String level = effect.getAmplifier() > 0 ? " " + (effect.getAmplifier() + 1) : "";
        return name + level + "  " + time;
    }

    @Override
    public int getWidth() {
        int max = 60;
        for (StatusEffectInstance e : effects()) {
            max = Math.max(max, mc.textRenderer.getWidth(format(e)));
        }
        return max;
    }

    @Override
    public int getHeight() {
        return Math.max(mc.textRenderer.fontHeight, effects().size() * (mc.textRenderer.fontHeight + 2));
    }

    @Override
    public void render(DrawContext context, int x, int y) {
        int offset = 0;
        long now = System.currentTimeMillis();

        for (StatusEffectInstance effect : effects()) {
            int color = 0xFFFFFFFF;

            if (blinkWhenLow.get() && !effect.isInfinite()) {
                int seconds = effect.getDuration() / 20;
                if (seconds <= blinkThreshold.get()) {
                    // Fast pulse so it catches the eye without being unreadable
                    boolean on = (now / 300) % 2 == 0;
                    color = on ? 0xFFFF6B81 : 0xFFFFD9A0;
                }
            }

            context.drawText(mc.textRenderer, format(effect), x, y + offset, color, true);
            offset += mc.textRenderer.fontHeight + 2;
        }
    }
}
