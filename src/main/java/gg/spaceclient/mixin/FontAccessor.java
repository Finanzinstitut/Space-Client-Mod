package gg.spaceclient.mixin;

import net.minecraft.client.gui.Font;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches the glyph provider inside a Font.
 *
 * A Font is a thin wrapper: one private field holding a Provider, which maps a
 * font description to a set of glyphs. Borrowing that provider is what lets a
 * second Font be built that draws from a different definition while using the
 * same loaded glyph atlases.
 */
@Mixin(Font.class)
public interface FontAccessor {

    @Accessor("provider")
    Font.Provider spaceclient$provider();
}
