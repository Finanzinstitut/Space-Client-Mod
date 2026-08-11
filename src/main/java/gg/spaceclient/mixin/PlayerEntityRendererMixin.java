package gg.spaceclient.mixin;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.badge.UserRegistry;
import gg.spaceclient.modules.visual.BadgeModule;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Prefixes the floating name tag of Space Client users.
 *
 * A texture cannot be injected into a Component component, so above heads the badge
 * is the planet glyph rather than the image used in the tab list.
 */
@Mixin(EntityRenderer.class)
public abstract class PlayerRendererMixin {

    @ModifyVariable(
            method = "renderLabelIfPresent",
            at = @At("HEAD"),
            argsOnly = true,
            index = 2,
            require = 0
    )
    private Component spaceclient$prefixBadge(Component original) {
        BadgeModule badge = (BadgeModule) SpaceClient.getModuleManager().get("badge");
        if (badge == null || !badge.isEnabled() || !badge.inNametags()) return original;
        return original;
    }

    /** Shared helper so the label logic lives in one place. */
    private static Component spaceclient$decorate(Entity entity, Component original) {
        if (!(entity instanceof Player player)) return original;

        BadgeModule badge = (BadgeModule) SpaceClient.getModuleManager().get("badge");
        if (badge == null || !badge.isEnabled() || !badge.inNametags()) return original;
        if (!UserRegistry.hasBadge(player.getUUID())) return original;

        Minecraft mc = Minecraft.getInstance();
        boolean isSelf = mc.player != null && player.getUUID().equals(mc.player.getUUID());
        if (!badge.showFor(isSelf)) return original;

        MutableComponent prefix = Component.literal("\u2648 ");
        return prefix.append(original);
    }
}
