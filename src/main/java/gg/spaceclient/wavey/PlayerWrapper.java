/*
 * Glue for the WaveyCapes port (see CapeHolder for the attribution).
 * Space Client code, written for Minecraft 26.2.
 */
package gg.spaceclient.wavey;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * What the cape renderer asks about a player, answered from the render state.
 *
 * The same questions vanilla's CapeLayer asks, in the same way: whether the
 * chest slot carries wings (no cape under an elytra) or a humanoid layer (the
 * cape sits a little further out over a chestplate).
 */
public class PlayerWrapper {

    /** Set by the renderer mixin, from the entity renderer context. */
    public static EquipmentAssetManager equipmentAssets;

    private final AvatarRenderState state;

    public PlayerWrapper(AvatarRenderState state) {
        this.state = state;
    }

    public AvatarRenderState getRenderState() { return state; }

    public boolean isPlayerInvisible() { return state.isInvisible; }

    public boolean isCapeVisible() { return state.showCape && state.skin != null && state.skin.cape() != null; }

    public Identifier getCapeTexture() {
        if (state.skin == null || state.skin.cape() == null) return null;
        return state.skin.cape().texturePath();
    }

    public boolean hasElytraEquipped() {
        return hasLayer(state.chestEquipment, EquipmentClientInfo.LayerType.WINGS);
    }

    public boolean hasChestplateEquipped() {
        return hasLayer(state.chestEquipment, EquipmentClientInfo.LayerType.HUMANOID);
    }

    /** The player the state was drawn from, for the simulation it carries. */
    public Avatar getAvatar() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Entity entity = mc.level.getEntity(state.id);
        return entity instanceof Avatar avatar ? avatar : null;
    }

    private static boolean hasLayer(ItemStack stack, EquipmentClientInfo.LayerType layer) {
        if (stack == null || stack.isEmpty() || equipmentAssets == null) return false;
        var equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.assetId().isEmpty()) return false;
        return !equipmentAssets.get(equippable.assetId().get()).getLayers(layer).isEmpty();
    }
}
