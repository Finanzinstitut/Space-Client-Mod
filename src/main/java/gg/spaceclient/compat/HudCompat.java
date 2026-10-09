package gg.spaceclient.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;

/**
 * The in-game HUD: its own class (Hud, reached through gui.hud) from 26.2,
 * the Gui object itself before.
 */
public final class HudCompat {

    private HudCompat() {}

    /** The object the HUD mixins are applied to. */
    public static Object hud() {
        //#if MC >= 26.2
        return Minecraft.getInstance().gui.hud;
        //#else
        //$$ return Minecraft.getInstance().gui;
        //#endif
    }

    /** Whether the HUD is hidden (F1). */
    public static boolean isHidden() {
        //#if MC >= 26.2
        return Minecraft.getInstance().gui.hud.isHidden();
        //#else
        //$$ return Minecraft.getInstance().options.hideGui;
        //#endif
    }

    public static ChatComponent chat() {
        //#if MC >= 26.2
        return Minecraft.getInstance().gui.hud.getChat();
        //#else
        //$$ return Minecraft.getInstance().gui.getChat();
        //#endif
    }

    public static Identifier effectSprite(Holder<MobEffect> effect) {
        //#if MC >= 26.2
        return net.minecraft.client.gui.Hud.getMobEffectSprite(effect);
        //#else
        //$$ return net.minecraft.client.gui.Gui.getMobEffectSprite(effect);
        //#endif
    }
}
