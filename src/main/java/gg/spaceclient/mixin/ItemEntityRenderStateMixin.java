package gg.spaceclient.mixin;

import gg.spaceclient.access.ItemIdHolder;

import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Gives the render state somewhere to keep the item's id. */
@Mixin(ItemEntityRenderState.class)
public class ItemEntityRenderStateMixin implements ItemIdHolder {

    @Unique
    private String spaceclient$itemId = "";

    @Override
    public String spaceclient$itemId() { return spaceclient$itemId; }

    @Override
    public void spaceclient$setItemId(String id) { this.spaceclient$itemId = id; }
}
