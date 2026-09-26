package gg.spaceclient.mixin;

import gg.spaceclient.access.TintHolder;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Gives every entity render state room for the hit colour decision.
 *
 * On the base class rather than the living one, because end crystals are not
 * living entities and are coloured too.
 */
@Mixin(EntityRenderState.class)
public class EntityRenderStateMixin implements TintHolder {

    @Unique
    private int spaceclient$tint = TintHolder.NONE;

    @Override
    public int spaceclient$tint() { return spaceclient$tint; }

    @Override
    public void spaceclient$setTint(int tint) { this.spaceclient$tint = tint; }
}
