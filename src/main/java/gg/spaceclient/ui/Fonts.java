package gg.spaceclient.ui;

import gg.spaceclient.SpaceClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The typeface this client draws itself in.
 *
 * The game's font is replaced everywhere by `assets/minecraft/font/default.json`
 * - menus, chat, signs, books, item names. That is deliberate.
 *
 * This class is the separate question of what Space Client's own screens draw
 * in, which can be changed while the game runs. It defaults to the same face as
 * everything else, so the client looks of a piece; picking "Minecraft" here
 * gives the original pixel font back for this mod's screens only.
 *
 * Switching at runtime cannot be done with resource files - a resource pack is
 * chosen at load and `minecraft:default` is fixed once it is. So the Font
 * object itself is built here instead, which also has the advantage of being
 * reversible without a resource reload.
 */
public final class Fonts {

    /** Style name to the font definition it draws with. */
    private static Identifier idFor(String style) {
        return switch (style) {
            case "OPEN_SANS" -> Identifier.fromNamespaceAndPath(
                    SpaceClient.MOD_ID, "opensans_ui");
            case "BARLOW" -> Identifier.fromNamespaceAndPath(
                    SpaceClient.MOD_ID, "barlow_ui");
            // Not null: the game's own font is now Open Sans, because
            // default.json replaces it everywhere. Reaching the original pixel
            // font again means asking for the vanilla providers by name.
            case "MINECRAFT" -> Identifier.fromNamespaceAndPath(
                    SpaceClient.MOD_ID, "vanilla_ui");
            default -> null;
        };
    }

    private static final Map<String, Font> cache = new HashMap<>();
    private static boolean broken = false;

    /**
     * The font to draw the interface with.
     *
     * Falls back to the game's own font whenever anything is not as expected,
     * which is the right failure: an interface in the wrong typeface is a
     * cosmetic disappointment, an interface that throws is a black screen.
     */
    public static Font ui() {
        Minecraft mc = Minecraft.getInstance();
        String style = SpaceClient.getSettings().fontStyle();

        Identifier id = idFor(style);
        if (id == null || broken) return mc.font;

        Font cached = cache.get(style);
        if (cached != null) return cached;

        Font built = build(mc, id);
        if (built == null) {
            broken = true;
            SpaceClient.LOGGER.warn(
                    "Custom interface fonts unavailable on this version; using the game's own");
            return mc.font;
        }

        cache.put(style, built);
        return built;
    }

    /**
     * Builds a Font that resolves every lookup to one particular definition.
     *
     * A Font is little more than a function from a font id to a set of glyphs.
     * Rather than reach for the font manager - which is private and has moved
     * between versions - this borrows that function out of the game's own Font
     * and wraps it so that whatever id is asked for, ours is returned.
     *
     * Entirely reflective, because none of it is public API. A miss costs the
     * custom font and nothing else.
     */
    @SuppressWarnings("unchecked")
    private static Font build(Minecraft mc, Identifier id) {
        try {
            Function<Identifier, Object> lookup = null;

            for (Field field : Font.class.getDeclaredFields()) {
                if (!Function.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                Object value = field.get(mc.font);
                if (value instanceof Function<?, ?> function) {
                    lookup = (Function<Identifier, Object>) function;
                    break;
                }
            }
            if (lookup == null) return null;

            final Function<Identifier, Object> resolved = lookup;

            for (Constructor<?> constructor : Font.class.getDeclaredConstructors()) {
                Class<?>[] parameters = constructor.getParameterTypes();
                if (parameters.length == 0) continue;
                if (!Function.class.isAssignableFrom(parameters[0])) continue;

                constructor.setAccessible(true);
                Function<Identifier, Object> always = ignored -> resolved.apply(id);

                // Later parameters are flags such as "filter fishy glyphs";
                // false is the ordinary setting for all of them
                Object[] args = new Object[parameters.length];
                args[0] = always;
                for (int i = 1; i < parameters.length; i++) {
                    args[i] = parameters[i] == boolean.class ? Boolean.FALSE : null;
                }

                return (Font) constructor.newInstance(args);
            }
            return null;

        } catch (Throwable t) {
            return null;
        }
    }

    /** Forgets the built fonts, so a change of setting takes effect at once. */
    public static void invalidate() {
        cache.clear();
        broken = false;
    }

    private Fonts() {}
}
