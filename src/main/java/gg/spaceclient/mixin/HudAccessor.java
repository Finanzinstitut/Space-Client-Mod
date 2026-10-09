package gg.spaceclient.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
//#if MC >= 26.2
import net.minecraft.client.gui.Hud;
//#else
//$$ import net.minecraft.client.gui.Gui;
//#endif
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Comparator;

/** The game's own scoreboard drawing and ordering, for the Scoreboard module. */
//#if MC >= 26.2
@Mixin(Hud.class)
//#else
//$$ @Mixin(Gui.class)
//#endif
public interface HudAccessor {

    @Invoker("displayScoreboardSidebar")
    void spaceclient$sidebar(GuiGraphicsExtractor graphics, Objective objective);

    @Accessor("SCORE_DISPLAY_ORDER")
    static Comparator<PlayerScoreEntry> spaceclient$order() {
        throw new AssertionError();
    }
}
