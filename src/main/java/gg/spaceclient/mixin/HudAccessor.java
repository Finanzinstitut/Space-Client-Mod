package gg.spaceclient.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Comparator;

/** The game's own scoreboard drawing and ordering, for the Scoreboard module. */
@Mixin(Hud.class)
public interface HudAccessor {

    @Invoker("displayScoreboardSidebar")
    void spaceclient$sidebar(GuiGraphicsExtractor graphics, Objective objective);

    @Accessor("SCORE_DISPLAY_ORDER")
    static Comparator<PlayerScoreEntry> spaceclient$order() {
        throw new AssertionError();
    }
}
