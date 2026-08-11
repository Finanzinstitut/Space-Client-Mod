package gg.spaceclient.mixin;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.badge.UserRegistry;
import gg.spaceclient.modules.visual.BadgeModule;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the Jupiter badge in front of names in the tab list. */
@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {

    private static final Identifier SPACECLIENT$JUPITER =
            Identifier.of("spaceclient", "textures/gui/jupiter.png");

    @Inject(
            method = "renderLatencyIcon",
            at = @At("HEAD"),
            require = 0
    )
    private void spaceclient$drawBadge(DrawContext context, int width, int x, int y,
                                       PlayerListEntry entry, CallbackInfo ci) {
        BadgeModule badge = (BadgeModule) SpaceClient.getModuleManager().get("badge");
        if (badge == null || !badge.isEnabled() || !badge.inTabList()) return;
        if (!UserRegistry.hasBadge(entry.getProfile().getId())) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        boolean isSelf = mc.player != null && entry.getProfile().getId().equals(mc.player.getUuid());
        if (!badge.showFor(isSelf)) return;

        // Sits just left of the name column
        context.drawTexture(SPACECLIENT$JUPITER, x - 10, y, 0, 0, 8, 8, 8, 8);
    }
}
