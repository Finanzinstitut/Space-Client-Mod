package gg.spaceclient.modules;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.ColorSetting;
import gg.spaceclient.setting.IntSetting;
import gg.spaceclient.setting.ModeSetting;
import gg.spaceclient.setting.SettingGroup;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The status effects, the way Lunar shows them: the effect's own icon with the
 * time beside it, wherever you put it, instead of the game's icons in the top
 * right corner.
 *
 * With the module on and "Replace vanilla" set, the game's own row of effect
 * icons is not drawn (HudOverlayMixin cancels it), so the effects are shown
 * once, here. Switch the module off and the game's row is back.
 */
public class EffectsModule extends HudModule {
    private static EffectsModule instance;

    private static final int ICON = 18;
    private static final int ROW_GAP = 3;

    private final ModeSetting layout = new ModeSetting(
            "layout", "Layout",
            "ICON_TIME: icon and time. ICON_NAME: icon, name and time below it. TEXT: name and time only",
            Arrays.asList("ICON_TIME", "ICON_NAME", "TEXT"), "ICON_TIME");
    private final BooleanSetting replaceVanilla = new BooleanSetting(
            "replace_vanilla", "Replace vanilla",
            "Hide the game's own effect icons in the top right while this is on", true);
    private final BooleanSetting showLevel = new BooleanSetting(
            "show_level", "Level", "The effect's strength as a numeral", true);
    private final BooleanSetting showInfinite = new BooleanSetting(
            "show_infinite", "Endless effects", "Include effects with no timer", true);
    private final BooleanSetting blink = new BooleanSetting(
            "blink", "Blink when ending", "Flash the icon in the last seconds, like the game does", true);
    private final IntSetting warnAt = new IntSetting(
            "warn_at", "Warn under", "Seconds remaining before the time turns amber",
            10, 0, 60);
    private final IntSetting limit = new IntSetting(
            "limit", "Show at most", "How many effects to list", 8, 1, 16);
    private final ColorSetting textColor = new ColorSetting(
            "text_color", "Text colour", "Colour of names and times", 0xFFFFFFFF);
    private final ColorSetting nameColor = new ColorSetting(
            "name_color", "Name colour", "Colour of the effect name in the icon and name layout", 0xFFFFFFFF);

    public EffectsModule() {
        super("effects", "Effects", "Your status effects with icon and time - replaces the top right",
                0.86f, 0.06f, false);
        addGroups(
                SettingGroup.of("Readout", "What each line says",
                        layout, replaceVanilla, showLevel, showInfinite, limit),
                SettingGroup.of("Appearance", "How it looks",
                        blink, warnAt, textColor, nameColor)
        );
        instance = this;
    }

    /** Whether the game's own effect icons should stay hidden. */
    public static boolean replacesVanilla() {
        EffectsModule module = instance;
        return module != null && module.isEnabled() && module.replaceVanilla.get();
    }

    private List<MobEffectInstance> active() {
        List<MobEffectInstance> out = new ArrayList<>();
        if (mc.player == null) return out;
        for (MobEffectInstance effect : mc.player.getActiveEffects()) {
            // Effects the game itself keeps out of sight stay out of sight
            if (!effect.showIcon()) continue;
            if (effect.isInfiniteDuration() && !showInfinite.get()) continue;
            out.add(effect);
        }
        // Soonest to run out on top, endless ones last
        out.sort(Comparator.comparingInt(e -> e.isInfiniteDuration() ? Integer.MAX_VALUE : e.getDuration()));
        if (out.size() > limit.get()) return out.subList(0, limit.get());
        return out;
    }

    private static final String[] NUMERALS = {"", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private String name(MobEffectInstance effect) {
        String name = effect.getEffect().value().getDisplayName().getString();
        int level = effect.getAmplifier() + 1;
        if (showLevel.get() && level > 1) {
            name += " " + (level - 1 < NUMERALS.length ? NUMERALS[level - 1] : Integer.toString(level));
        }
        return name;
    }

    private static String clock(MobEffectInstance effect) {
        if (effect.isInfiniteDuration()) return "**:**";
        int seconds = Math.max(0, effect.getDuration() / 20);
        if (seconds >= 3600) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
        }
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private boolean ending(MobEffectInstance effect) {
        return !effect.isInfiniteDuration() && effect.getDuration() / 20 <= warnAt.get();
    }

    private int rowHeight() {
        return layout.is("TEXT") ? mc.font.lineHeight + 1 : ICON + ROW_GAP;
    }

    private String line(MobEffectInstance effect) {
        return layout.is("ICON_TIME") ? clock(effect) : name(effect) + (layout.is("TEXT") ? "  " + clock(effect) : "");
    }

    /** Nothing to show means no plate either - except in the editor, where it has to be placeable. */
    @Override
    public void draw(GuiGraphicsExtractor graphics, int x, int y) {
        boolean editing = mc.gui != null && mc.gui.screen() instanceof gg.spaceclient.ui.HudEditorScreen;
        if (active().isEmpty() && !editing) return;
        super.draw(graphics, x, y);
    }

    @Override
    public int getWidth() {
        List<MobEffectInstance> effects = active();
        int text = 0;
        for (MobEffectInstance effect : effects) {
            text = Math.max(text, mc.font.width(line(effect)));
            if (layout.is("ICON_NAME")) text = Math.max(text, mc.font.width(clock(effect)));
        }
        if (effects.isEmpty()) text = mc.font.width(layout.is("TEXT") ? "Speed II  1:30" : "1:30");
        return layout.is("TEXT") ? text : ICON + 4 + text;
    }

    @Override
    public int getHeight() {
        int rows = Math.max(1, active().size());
        return rows * rowHeight() - (layout.is("TEXT") ? 1 : ROW_GAP);
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int x, int y) {
        List<MobEffectInstance> effects = active();
        if (effects.isEmpty()) {
            // Shown only in the HUD editor, so there is something to place
            graphics.text(mc.font, layout.is("TEXT") ? "Speed II  1:30" : "1:30",
                    layout.is("TEXT") ? x : x + ICON + 4, y + (layout.is("TEXT") ? 0 : 5), 0x80FFFFFF, true);
            return;
        }
        int row = y;
        for (MobEffectInstance effect : effects) {
            boolean ending = ending(effect);
            int timeColour = ending ? 0xFFE8C46A : textColor.get();

            if (layout.is("TEXT")) {
                graphics.text(mc.font, line(effect), x, row, timeColour, true);
                row += rowHeight();
                continue;
            }

            // The game's blink near the end: the icon fades in and out
            float alpha = 1f;
            if (blink.get() && ending && !effect.isInfiniteDuration()) {
                int ticks = effect.getDuration();
                alpha = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.cos(ticks * Math.PI / 5));
            }
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(effect.getEffect()),
                    x, row, ICON, ICON, alpha);

            int textX = x + ICON + 4;
            if (layout.is("ICON_NAME")) {
                graphics.text(mc.font, name(effect), textX, row, nameColor.get(), true);
                graphics.text(mc.font, clock(effect), textX, row + mc.font.lineHeight + 1, timeColour, true);
            } else {
                graphics.text(mc.font, clock(effect), textX, row + (ICON - mc.font.lineHeight) / 2 + 1, timeColour, true);
            }
            row += rowHeight();
        }
    }
}
