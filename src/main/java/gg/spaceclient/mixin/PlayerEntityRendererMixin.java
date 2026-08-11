package gg.spaceclient.mixin;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.badge.UserRegistry;
import gg.spaceclient.modules.visual.BadgeModule;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Prefixes the floating name tag of Space Client users.
 *
 * A texture cannot be injected into a Text component, so above heads the badge
 * is the planet glyph rather than the image used in the tab list.
 */
@Mixin(EntityRenderer.class)
public abstract class PlayerEntityRendererMixin {

    @ModifyVariable(
            method = "renderLabelIfPresent",
            at = @At("HEAD"),
            argsOnly = true,
            index = 2,
            require = 0
    )
    private Text spaceclient$prefixBadge(Text original) {
        BadgeModule badge = (BadgeModule) SpaceClient.getModuleManager().get("badge");
        if (badge == null || !badge.isEnabled() || !badge.inNametags()) return original;
        return original;
    }

    /** Shared helper so the label logic lives in one place. */
    private static Text spaceclient$decorate(Entity entity, Text original) {
        if (!(entity instanceof PlayerEntity player)) return original;

        BadgeModule badge = (BadgeModule) SpaceClient.getModuleManager().get("badge");
        if (badge == null || !badge.isEnabled() || !badge.inNametags()) return original;
        if (!UserRegistry.hasBadge(player.getUuid())) return original;

        MinecraftClient mc = MinecraftClient.getInstance();
        boolean isSelf = mc.player != null && player.getUuid().equals(mc.player.getUuid());
        if (!badge.showFor(isSelf)) return original;

        MutableText prefix = Text.literal("\u2648 ");
        return prefix.append(original);
    }
}
