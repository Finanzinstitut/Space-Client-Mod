package gg.spaceclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets the font be swapped at runtime.
 *
 * `Minecraft.font` is final, and since Java 17 reflection cannot write a final
 * instance field - the attempt throws, gets caught, and the setting silently
 * does nothing. That was the bug: the switch looked wired up and never took.
 *
 * Mixin can do it, because @Mutable strips the final flag at load time rather
 * than fighting it at runtime. This is the whole reason this file exists.
 */
@Mixin(Minecraft.class)
public interface MinecraftFontAccessor {

    @Mutable
    @Accessor("font")
    void spaceclient$setFont(Font font);
}
