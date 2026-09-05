package gg.spaceclient.ui;

import gg.spaceclient.SpaceClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * The typeface this client draws itself in.
 *
 * Bitmap fonts, not TrueType. Every TTF attempt looked wrong for the same
 * reason: a typeface drawn for print, squeezed into eight pixels of height, is
 * a smear no matter how it is rasterised. These are pixel fonts drawn for
 * Minecraft at exactly that size, which is why they simply look right.
 *
 * There is no longer any global override in the resources. Switching builds a
 * Font per definition and puts it where the game keeps its own, so every choice
 * - including the game's own font - takes effect the moment it is picked, and
 * nothing has to be undone at load.
 *
 * Switching at runtime cannot be done with resource files - a resource pack is
 * chosen at load and `minecraft:default` is fixed once it is. So the Font
 * object itself is built here instead, which also has the advantage of being
 * reversible without a resource reload.
 */
public final class Fonts {

    /** Style name to the font definition it draws with. */
    private static Identifier idFor(String style) {
        String file = switch (style) {
            case "SMOOTH" -> "smooth_ui";
            case "ANTIALIAS" -> "antialias_ui";
            case "SMALLCAPS" -> "smallcaps_ui";
            case "SQUARE" -> "square_ui";
            case "DOODLE" -> "doodle_ui";
            case "BLOCKY" -> "blocky_ui";
            case "MINECRAFT" -> "vanilla_ui";
            default -> null;
        };
        if (file == null) return null;
        return Identifier.fromNamespaceAndPath(SpaceClient.MOD_ID, file);
    }

    private static final Map<String, Font> cache = new HashMap<>();
    private static boolean broken = false;

    /** The game's own Font, kept so "Minecraft" can hand it straight back. */
    private static Font original = null;

    /**
     * The font to draw the interface with.
     *
     * Falls back to the game's own font whenever anything is not as expected,
     * which is the right failure: an interface in the wrong typeface is a
     * cosmetic disappointment, an interface that throws is a black screen.
     */
    public static Font ui() {
        // Just the game's font, because apply() has already made that the
        // chosen one. Two separate paths would mean the mod's screens and the
        // rest of the game could disagree about which typeface is current.
        return Minecraft.getInstance().font;
    }

    /**
     * Puts the chosen typeface everywhere, by swapping the game's own Font.
     *
     * A resource pack cannot do this: it is read once at load and
     * `minecraft:default` is settled from then on. But `Minecraft.font` is an
     * ordinary field holding an ordinary object, and everything in the game
     * draws through it - menus, chat, signs, item names. Replacing it changes
     * all of them at once, and putting the original back undoes it just as
     * completely.
     *
     * Called when the setting changes and once at startup.
     */
    public static void apply() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;

            if (original == null) original = mc.font;
            if (original == null) return;

            String style = SpaceClient.getSettings().fontStyle();

            // "Minecraft" is built like every other style now, from a
            // definition that names the vanilla providers by hand.
            //
            // Handing back the captured `original` looked right and was not: if
            // another font mod - Caxton, say - had already replaced the game's
            // font before this mod started, then `original` is that mod's font,
            // not the pixel one. Choosing "Minecraft" then gave Caxton's Open
            // Sans back. Asking for the vanilla providers by name sidesteps
            // whatever any other mod did to the default.
            Identifier id = idFor(style);
            if (id == null) return;

            Font target = cache.get(style);
            if (target == null) {
                target = build(mc, id);
                if (target == null) {
                    status = "could not build " + style;
                    SpaceClient.LOGGER.warn(
                            "Could not build the {} font; leaving the current one", style);
                    return;
                }
                cache.put(style, target);
            }

            // Through the accessor, not reflection. Minecraft.font is final,
            // and since Java 17 reflection cannot write a final instance field
            // - it throws, the throw gets caught, and the setting quietly does
            // nothing. That is exactly what was happening.
            ((gg.spaceclient.mixin.MinecraftFontAccessor) (Object) mc)
                    .spaceclient$setFont(target);

            status = style.toLowerCase(java.util.Locale.ROOT) + " applied";

        } catch (Throwable t) {
            status = "failed: " + t.getMessage();
            SpaceClient.LOGGER.warn("Could not apply the interface font: {}", t.getMessage());
        }
    }

    /** What the last attempt did, for the diagnostics page. */
    public static String status() { return status; }

    private static String status = "not applied yet";

    /**
     * Builds a Font that draws from one particular definition.
     *
     * A Font is a wrapper around a single Provider, and its constructor is
     * public and takes exactly that. The redirection is therefore only: hand it
     * a Provider that answers with our definition rather than the one asked
     * for.
     *
     * The Provider is built as a dynamic proxy rather than a lambda, and that
     * is not decoration. `Font.Provider` has more than one abstract method, so
     * it is not a functional interface and a lambda does not compile - which is
     * exactly how the last build failed. A proxy forwards everything to the
     * real provider untouched and intercepts the one call that matters, without
     * this code having to know what the other methods are or growing a new bug
     * every time Mojang adds one.
     */
    private static Font build(Minecraft mc, Identifier id) {
        try {
            Font source = original != null ? original : mc.font;
            Font.Provider real =
                    ((gg.spaceclient.mixin.FontAccessor) (Object) source).spaceclient$provider();
            if (real == null) return null;

            FontDescription description = new FontDescription.Resource(id);

            // A real Provider, not a proxy. The dump settled what it needs:
            // glyphs(FontDescription) and effect(). The proxy forwarded
            // effect() to the source provider, whose effect glyph is tied to
            // the source's own definition, not ours - a mismatch that failed
            // the build. Implementing both explicitly keeps them consistent:
            // glyphs answers for our definition, effect comes from the real
            // one unchanged.
            Font.Provider redirected = new Font.Provider() {
                @Override
                public net.minecraft.client.gui.GlyphSource glyphs(FontDescription ignored) {
                    return real.glyphs(description);
                }

                @Override
                public net.minecraft.client.gui.font.glyphs.EffectGlyph effect() {
                    return real.effect();
                }
            };

            return new Font(redirected);

        } catch (Throwable t) {
            SpaceClient.LOGGER.warn("Could not build a font for {}: {}", id, t.getMessage());
            return null;
        }
    }

    /** Forgets the built fonts and re-applies, so a change takes effect at once. */
    public static void invalidate() {
        cache.clear();
        broken = false;
        apply();
    }

    private Fonts() {}
}
