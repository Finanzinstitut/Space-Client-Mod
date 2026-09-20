package gg.spaceclient.modules;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.module.Module;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.IntSetting;
import gg.spaceclient.setting.ModeSetting;
import gg.spaceclient.setting.SettingGroup;
import gg.spaceclient.util.Reflect;

import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Locale;

/**
 * Shifts how strong the world's colours are, from grey to oversaturated.
 *
 * This is not a HUD element and draws nothing of its own. It is a post
 * processing pass: after the world has been rendered, every pixel is pulled
 * towards or away from its own brightness. The weights are Mojang's own
 * (0.3, 0.59, 0.11) rather than the technically correct Rec.709 ones, so a
 * fully desaturated world looks the same as the game's built-in greyscale
 * effects rather than subtly different.
 *
 * <h2>Why fixed steps rather than a free slider</h2>
 *
 * The strength lives in the effect's JSON as a uniform, and that JSON is read
 * once when the chain is loaded. Changing it afterwards would mean writing into
 * the uniform buffer directly - which this version does not allow: PostPass
 * creates those buffers with USAGE_UNIFORM only, no USAGE_MAP_WRITE, so mapping
 * one for writing fails at runtime. Confirmed from the bytecode rather than
 * guessed, and it would have failed in game rather than at build time.
 *
 * So the mod ships one small JSON per step and picks between them. At five
 * percent apart the slider reads as continuous; forty files of a kilobyte each
 * is a cheaper price than a value that cannot move at all.
 *
 * <h2>Why reflection rather than a mixin</h2>
 *
 * Applying an effect means GameRenderer.setPostEffect, which is private, and
 * reading the current one means currentPostEffect. Following this mod's rule -
 * anything proven by a compile is called directly, anything guessed goes
 * through Reflect - both go through reflection, because neither has been
 * compiled against. A wrong name then costs a log line instead of a build.
 *
 * Going through the game's own field also means the frame graph, the render
 * targets and the timing stay vanilla's problem rather than ours.
 */
public class SaturationModule extends Module {

    /** Coarsest step there is a file for. 100 is absent: see snap(). */
    private static final int STEP = 5;
    private static final int MAX = 200;

    // --- strength ---

    private final IntSetting level = new IntSetting(
            "level", "Saturation (percent)",
            "100 leaves colours alone, 0 is greyscale, above 100 pushes them", 100, 0, MAX);

    /**
     * A quick pick alongside the slider.
     *
     * The slider is the honest control but a bad way to find a look you have
     * not seen yet - the useful settings are a handful of places on it, and
     * hunting for them five percent at a time is worse than pressing a button
     * that says "Muted".
     */
    private final ModeSetting preset = new ModeSetting(
            "preset", "Preset", "Jump to a strength, or leave on Custom to use the slider",
            Arrays.asList("CUSTOM", "GREYSCALE", "FADED", "MUTED", "NEUTRAL", "RICH", "VIVID", "INTENSE"),
            "CUSTOM");

    // --- behaviour ---

    /**
     * Vanilla allows exactly one post effect at a time, and uses that slot
     * itself when you spectate a creeper or a spider. On, this module stands
     * aside for it; off, it takes the slot back on the next tick.
     */
    private final BooleanSetting respectVanilla = new BooleanSetting(
            "respect_vanilla", "Yield to game effects",
            "Let spectator effects take over instead of fighting them", true);

    /**
     * Whether to keep putting the effect back.
     *
     * The game drops the post effect on its own in more places than are worth
     * enumerating - a resource reload, a camera change, spectating something.
     * Reapplying every tick papers over all of them at the cost of a field read
     * twenty times a second. Off, the effect is set once and left alone, which
     * is the honest behaviour to compare against when something looks wrong.
     */
    private final BooleanSetting persist = new BooleanSetting(
            "persist", "Keep reapplying",
            "Put the effect back whenever the game drops it", true);

    /** Skips the effect while spectating, where it fights vanilla the most. */
    private final BooleanSetting skipSpectator = new BooleanSetting(
            "skip_spectator", "Off while spectating",
            "Leave colours alone in spectator mode", false);

    // --- live state ---

    /** The effect this module put in place, so it only ever clears its own. */
    private Identifier applied = null;

    private String lastPreset = "CUSTOM";
    private boolean warned = false;

    /**
     * How long to leave an effect alone before putting it back again.
     *
     * Applying a post effect is not free - the chain behind it is rebuilt - so
     * doing it on every tick is visible as a flicker. Half a second is slower
     * than an eye notices a missing effect and twenty times cheaper than the
     * tick loop.
     */
    private static final long REPAIR_INTERVAL_MS = 500;

    private long lastApply = 0L;

    /** How many times in a row the effect had to be put back. */
    private int repairs = 0;

    /**
     * Set when the game's state cannot be read back at all.
     *
     * Proven rather than assumed: setPostEffect has to write the identifier
     * somewhere, so if reading it straight afterwards gives nothing, it is the
     * reading that is broken and not the setting.
     *
     * It matters because "keep reapplying" needs to know the effect is gone.
     * Blind, the honest thing is to set it once and stop - reapplying on a
     * guess is what flickers, and flickering is worse than an effect that
     * might quietly disappear after a resource reload.
     */
    private boolean blind = false;

    private static String status = "not applied";

    /** What the module last did, for the diagnostics screen. */
    public static String lastResult() { return status; }

    public SaturationModule() {
        super("saturation", "Saturation", "Shifts how strong the world's colours are", false);
        addGroups(
                SettingGroup.of("Strength", "How far the colours are pushed",
                        preset, level),
                SettingGroup.of("Behaviour", "How the effect shares the screen with the game",
                        respectVanilla, persist, skipSpectator)
        );
    }

    /** The strength a preset stands for, or -1 for Custom. */
    private static int presetLevel(String name) {
        return switch (name) {
            case "GREYSCALE" -> 0;
            case "FADED" -> 35;
            case "MUTED" -> 65;
            case "NEUTRAL" -> 100;
            case "RICH" -> 130;
            case "VIVID" -> 160;
            case "INTENSE" -> 200;
            default -> -1;
        };
    }

    /**
     * The nearest strength there is a file for.
     *
     * 100 has no file because it is the neutral value, and the cheapest way to
     * render "no change" is to render nothing rather than to run two passes
     * that between them do nothing. Anything landing on it is treated as off.
     */
    private static int snap(int value) {
        int clamped = Math.max(0, Math.min(MAX, value));
        return Math.round(clamped / (float) STEP) * STEP;
    }

    private static Identifier idFor(int step) {
        return Identifier.fromNamespaceAndPath(SpaceClient.MOD_ID, "saturation_" + step);
    }

    /**
     * Whether an effect is one of ours.
     *
     * Compares against the identifiers this module can produce rather than
     * reading the namespace off the object, because the accessor's name under
     * this version's mappings is not something a compile here has proven.
     */
    private static boolean isOurs(Identifier id) {
        if (id == null) return false;
        for (int step = 0; step <= MAX; step += STEP) {
            if (step != 100 && idFor(step).equals(id)) return true;
        }
        return false;
    }

    @Override
    public void onTick() {
        // A preset press moves the slider, then hands control back to it, so
        // the slider stays the single source of truth for the strength
        String chosen = preset.get();
        if (!chosen.equals(lastPreset)) {
            lastPreset = chosen;
            int wanted = presetLevel(chosen);
            if (wanted >= 0) {
                level.set(wanted);
                preset.set("CUSTOM");
                lastPreset = "CUSTOM";
            }
        }

        Object renderer = gameRenderer();
        if (renderer == null) {
            status = "renderer not reachable";
            return;
        }

        Identifier current = currentEffect(renderer);

        if (respectVanilla.get() && current != null && !isOurs(current)) {
            // Something else owns the slot - spectating a creeper, most likely
            applied = null;
            status = "yielded to a game effect";
            return;
        }

        int step = snap(level.get());
        if (step == 100 || (skipSpectator.get() && isSpectator())) {
            clear(renderer);
            return;
        }

        Identifier wanted = idFor(step);

        // A strength that changed is applied at once - the slider has to feel
        // immediate. Everything else is a repair, and a repair is rate limited
        // below.
        boolean changed = !wanted.equals(applied);

        // Both fields have to line up, not just the identifier.
        //
        // The render path checks postEffectId AND a separate `effectActive`
        // flag, and skips the pass if either is missing. currentPostEffect()
        // only reports the first of the two, so an effect can read as set
        // while nothing at all is drawn - which is exactly what happens in
        // some camera modes. Returning early on the identifier alone left the
        // flag off with nothing ever to turn it back on. Calling setPostEffect
        // again is the repair, because it writes both.
        boolean live = effectActive(renderer);

        if (wanted.equals(current) && live) {
            applied = wanted;
            repairs = 0;
            blind = false;
            status = "active at " + step + "%";
            return;
        }

        // Once set, leave it: this is the comparison case for working out
        // whether something else is taking the effect away
        if (!persist.get() && wanted.equals(applied)) {
            status = "set at " + step + "%, not reapplying";
            return;
        }

        if (sceneChanged()) {
            // A new camera or a new world is the moment the game is most
            // likely to have thrown the effect away, and the one moment a
            // rebuild costs nothing anybody can see - the picture is changing
            // anyway.
            changed = true;
        }

        if (!changed && blind) {
            status = "set at " + step + "%, the game's state cannot be read - not reapplying";
            return;
        }

        long now = System.currentTimeMillis();
        if (!changed && now - lastApply < REPAIR_INTERVAL_MS) {
            // Still not reading as applied, but it was only just set. Saying so
            // is the point: a module that is quietly reapplying every tick and
            // one that applied it cleanly used to look identical from here.
            status = "set at " + step + "%, waiting to see if it holds"
                    + (repairs > 1 ? " (" + repairs + " repairs)" : "");
            return;
        }

        if (!changed) repairs++; else repairs = 0;
        lastApply = now;
        setEffect(renderer, wanted, step);
    }

    /**
     * The flag the render path checks alongside the identifier.
     *
     * Absent from any public method, so it is read straight off the field.
     * Unreadable counts as true: the module then behaves as it did before this
     * was known about rather than reapplying the effect every single tick.
     */
    private boolean effectActive(Object renderer) {
        Object value = readField(renderer, "effectActive");
        return !(value instanceof Boolean flag) || flag;
    }

    @Override
    protected void onEnable() {
        // A fresh start deserves a fresh attempt at reading the state
        blind = false;
        repairs = 0;
        lastApply = 0L;
    }

    @Override
    protected void onDisable() {
        Object renderer = gameRenderer();
        if (renderer != null) clear(renderer);
        status = "off";
    }

    /** Takes our effect down, leaving anyone else's alone. */
    private void clear(Object renderer) {
        Identifier current = currentEffect(renderer);
        if (!isOurs(current)) {
            applied = null;
            status = "neutral";
            return;
        }
        Reflect.call(renderer, "clearPostEffect");
        applied = null;
        status = "neutral";
    }

    private void setEffect(Object renderer, Identifier id, int step) {
        Reflect.callWith(renderer, "setPostEffect", id);
        applied = id;

        // callWith returns null for a void method as well as for a miss, so a
        // failure shows up as the effect simply never appearing. Reading the
        // fields back is what tells the two apart.
        if (currentEffect(renderer) == null) {
            // Either the call missed or the read did. Both end here, and both
            // mean the same thing for what happens next: stop guessing.
            blind = true;
            warnOnce();
            status = "set at " + step + "%, but the game's state cannot be read";
        } else if (repairs > 4) {
            // It keeps coming back set and keeps reading as gone. Something
            // else is taking it away, and that is worth naming rather than
            // reporting "active" while the screen flickers.
            status = "at " + step + "%, but something keeps clearing it ("
                    + repairs + " repairs)";
        } else if (!effectActive(renderer)) {
            // Set, but the game will not draw it. Worth saying out loud
            // rather than reporting a cheerful "active"
            status = "set at " + step + "%, but the render flag is off";
        } else {
            status = "active at " + step + "%";
        }
    }

    /** Reads a field by name, walking up the class hierarchy. */
    private static Object readField(Object target, String name) {
        if (target == null) return null;
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * Which effect the game currently has, by getter or by field.
     *
     * The field is the half that matters and it is new here. Reflect.call only
     * tries methods, so if this version has no method by that name - and the
     * name was a guess, like every other one in this class - the answer came
     * back null on every single tick. Null reads as "not applied", so the
     * module reapplied the effect twenty times a second, forever, while the
     * effect was in fact already there. That is the flicker: setting a post
     * effect rebuilds the chain, and rebuilding it twenty times a second shows.
     *
     * setPostEffect has to write the identifier somewhere, so the field exists
     * whatever the getter is called.
     */
    private Identifier currentEffect(Object renderer) {
        Object value = Reflect.call(renderer, "currentPostEffect", "getPostEffect");
        if (value instanceof Identifier id) return id;

        Field field = effectField(renderer);
        if (field == null) return null;
        try {
            Object held = field.get(renderer);
            return held instanceof Identifier id ? id : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Not static, unlike the renderer lookup beside it.
     *
     * There is only ever one renderer class in a running game, so a static
     * cache would be correct there - but it would also mean the answer for one
     * kind of renderer outliving it, and the only thing that buys is a field
     * read saved once per session. Not worth being wrong for.
     */
    private Field effectIdField = null;
    private boolean effectFieldLookedUp = false;

    /**
     * The field holding the current post effect, found by type.
     *
     * By name first, then by type, and the second half is what makes the
     * watchdog work at all. If the module cannot read which effect is set, it
     * cannot tell that the game has dropped one - which is the whole story
     * behind the effect vanishing in third person and staying gone: something
     * takes it away, and a module that cannot see that has nothing to react to.
     *
     * A renderer carries exactly one Identifier that is not a constant, and
     * that is this one. The constants beside it - shader paths and the like -
     * are static, so they are skipped rather than guessed at.
     */
    private Field effectField(Object renderer) {
        if (effectFieldLookedUp) return effectIdField;
        effectFieldLookedUp = true;
        if (renderer == null) return null;

        java.util.List<Field> candidates = new java.util.ArrayList<>();
        Class<?> current = renderer.getClass();
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                if (field.getType() != Identifier.class) continue;
                field.setAccessible(true);

                String name = field.getName().toLowerCase(Locale.ROOT);
                if (name.contains("post") || name.contains("effect")) {
                    effectIdField = field;
                    return field;
                }
                candidates.add(field);
            }
            current = current.getSuperclass();
        }

        // Exactly one is an answer; several is a guess, and a guess here would
        // have the module reading somebody else's field every tick
        if (candidates.size() == 1) effectIdField = candidates.get(0);
        else if (!candidates.isEmpty()) {
            SpaceClient.LOGGER.warn("Saturation: {} candidate effect fields, none named for it",
                    candidates.size());
        }
        return effectIdField;
    }

    private Object lastCamera = null;
    private Object lastLevel = null;
    private boolean sceneKnown = false;

    /**
     * Whether the camera or the world changed since the last tick.
     *
     * Third person is the reported case: switching into it, and moving the
     * camera around in it, is where the effect went away and stayed away. The
     * camera type is compared by its text rather than by identity because it
     * is an enum whose class cannot be named from here.
     */
    private boolean sceneChanged() {
        Object camera = Reflect.call(mc.options, "getCameraType", "getCameraDistance");
        Object level = mc.level;

        String now = String.valueOf(camera);
        boolean changed = sceneKnown
                && (!now.equals(String.valueOf(lastCamera)) || level != lastLevel);

        lastCamera = now;
        lastLevel = level;
        sceneKnown = true;
        return changed;
    }

    /** Spectator mode, read through the player rather than the game mode enum. */
    private boolean isSpectator() {
        Object value = Reflect.call(mc.player, "isSpectator");
        return value instanceof Boolean flag && flag;
    }

    private void warnOnce() {
        if (warned) return;
        warned = true;
        SpaceClient.LOGGER.warn("Saturation could not apply a post effect on this version");
    }

    // --- reaching the renderer ---

    private static Field rendererField = null;
    private static boolean rendererLookedUp = false;

    /**
     * The game's renderer, read by reflection for the same reason the calls
     * above are: the field's name under this version's mappings has not been
     * proven by a compile.
     */
    private Object gameRenderer() {
        if (mc == null) return null;
        if (!rendererLookedUp) {
            rendererLookedUp = true;
            Class<?> current = mc.getClass();
            while (current != null && rendererField == null) {
                try {
                    Field field = current.getDeclaredField("gameRenderer");
                    field.setAccessible(true);
                    rendererField = field;
                } catch (NoSuchFieldException ignored) {
                    current = current.getSuperclass();
                } catch (Throwable ignored) {
                    break;
                }
            }
        }
        if (rendererField == null) return null;
        try {
            return rendererField.get(mc);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
