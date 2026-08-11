package gg.spaceclient.mixin;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.badge.UserRegistry;
import gg.spaceclient.modules.visual.BadgeModule;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceLocation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the Jupiter badge in front of names in the tab list. */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {

    private static final ResourceLocation SPACECLIENT$JUPITER =
            ResourceLocation.fromNamespaceAndPath("spaceclient", "textures/gui/jupiter.png");

    @Inject(
            method = "renderLatencyIcon",
            at = @At("HEAD"),
            require = 0
    )
    private void spaceclient$drawBadge(GuiGraphics context, int width, int x, int y,
                                       PlayerInfo entry, CallbackInfo ci) {
        BadgeModule badge = (BadgeModule) SpaceClient.getModuleManager().get("badge");
        if (badge == null || !badge.isEnabled() || !badge.inTabList()) return;
        if (!UserRegistry.hasBadge(entry.getProfile().getId())) return;

        Minecraft mc = Minecraft.getInstance();
        boolean isSelf = mc.player != null && entry.getProfile().getId().equals(mc.player.getUUID());
        if (!badge.showFor(isSelf)) return;

        // Sits just left of the name column
        context.blit(SPACECLIENT$JUPITER, x - 10, y, 0, 0, 8, 8, 8, 8);
    }
}
