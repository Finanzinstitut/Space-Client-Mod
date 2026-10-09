package gg.spaceclient.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;

import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * The frames a post effect keeps between frames. Motion blur clears its
 * history through this on versions before 26.3, which have no public way to.
 */
@Mixin(PostChain.class)
public interface PostChainAccessor {

    @Accessor("persistentTargets")
    Map<Identifier, RenderTarget> spaceclient$persistentTargets();
}
