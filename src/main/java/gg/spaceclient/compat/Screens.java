package gg.spaceclient.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Opening and reading the current screen: on the GUI object from 26.2, on Minecraft before. */
public final class Screens {

    private Screens() {}

    public static void set(Screen screen) {
        //#if MC >= 26.2
        Minecraft.getInstance().gui.setScreen(screen);
        //#else
        //$$ Minecraft.getInstance().setScreen(screen);
        //#endif
    }

    public static Screen current() {
        //#if MC >= 26.2
        return Minecraft.getInstance().gui.screen();
        //#else
        //$$ return Minecraft.getInstance().screen;
        //#endif
    }
}
