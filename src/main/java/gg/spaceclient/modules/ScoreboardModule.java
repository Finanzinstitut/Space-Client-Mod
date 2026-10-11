package gg.spaceclient.modules;

import gg.spaceclient.mixin.HudAccessor;
import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.ColorSetting;
import gg.spaceclient.setting.SettingGroup;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.List;

/**
 * The server's scoreboard on the right, movable, hideable and recoloured.
 *
 * The board is still drawn by the game's own code - same lines, same team
 * colours, same number formats a server sends - so nothing a server does with
 * it gets lost. The module only decides where (the drawing is shifted from the
 * game's spot to wherever the element sits in the HUD editor), whether at all,
 * the two background colours, and whether the red numbers show.
 *
 * While the module is on the game's own pass is skipped (HudOverlayMixin), so
 * the board is never drawn twice.
 */
public class ScoreboardModule extends HudModule {
    private static ScoreboardModule instance;

    /** Set only while this module asks the game to draw, so the colour hooks touch only that. */
    private static boolean drawing = false;

    private final BooleanSetting show = new BooleanSetting(
            "show", "Show scoreboard", "Switch off to hide the server's scoreboard completely", true);
    private final BooleanSetting numbers = new BooleanSetting(
            "numbers", "Red numbers", "The score numbers on the right of each line", true);
    private final ColorSetting background = new ColorSetting(
            "board_background", "Background", "Colour behind the lines", 0x4C000000);
    private final ColorSetting titleBackground = new ColorSetting(
            "title_background", "Title background", "Colour behind the title", 0x66000000);

    public ScoreboardModule() {
        super("scoreboard", "Scoreboard", "Move, hide or recolour the server's scoreboard",
                0.84f, 0.32f, false);
        // The board brings its own background; a plate under it would be a second one
        setBackgroundEnabled(false);
        addGroups(
                SettingGroup.of("Board", "What is shown", show, numbers),
                SettingGroup.of("Colours", "Behind the board", background, titleBackground)
        );
        instance = this;
    }

    /** Whether the game's own scoreboard pass should be skipped. */
    public static boolean replacesVanilla() {
        ScoreboardModule module = instance;
        return module != null && module.isEnabled();
    }

    /** Called from the hooks inside the game's drawing. */
    public static boolean drawing() { return drawing; }

    public static int backgroundColour(boolean title, int vanilla) {
        ScoreboardModule module = instance;
        if (!drawing || module == null) return vanilla;
        return title ? module.titleBackground.get() : module.background.get();
    }

    public static NumberFormat numberFormat(NumberFormat vanilla) {
        ScoreboardModule module = instance;
        if (!drawing || module == null || module.numbers.get()) return vanilla;
        return BlankFormat.INSTANCE;
    }

    /** The objective the game would show: the team colour's slot first, then the sidebar. */
    private Objective objective() {
        if (mc.level == null || mc.player == null) return null;
        Scoreboard board = mc.level.getScoreboard();
        Objective objective = null;
        PlayerTeam team = board.getPlayersTeam(mc.player.getScoreboardName());
        //#if MC >= 26.2
        if (team != null && team.getColor().isPresent()) {
            DisplaySlot slot = team.getColor().get().displaySlot();
            if (slot != null) objective = board.getDisplayObjective(slot);
        }
        //#else
        //$$ if (team != null) {
        //$$     DisplaySlot slot = DisplaySlot.teamColorToSlot(team.getColor());
        //$$     if (slot != null) objective = board.getDisplayObjective(slot);
        //$$ }
        //#endif
        return objective != null ? objective : board.getDisplayObjective(DisplaySlot.SIDEBAR);
    }

    /** The board's size, worked out the way the game works it out. */
    private record Box(int maxWidth, int lines) {}

    private Box box(Objective objective) {
        Scoreboard board = objective.getScoreboard();
        NumberFormat format = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
        if (!numbers.get()) format = BlankFormat.INSTANCE;
        List<PlayerScoreEntry> entries = board.listPlayerScores(objective).stream()
                .filter(entry -> !entry.isHidden())
                .sorted(HudAccessor.spaceclient$order())
                .limit(15)
                .toList();
        int max = mc.font.width(objective.getDisplayName());
        int colon = mc.font.width(": ");
        for (PlayerScoreEntry entry : entries) {
            Component name = PlayerTeam.formatNameForTeam(board.getPlayersTeam(entry.owner()), entry.ownerName());
            int scoreWidth = mc.font.width(entry.formatValue(format));
            max = Math.max(max, mc.font.width(name) + (scoreWidth > 0 ? colon + scoreWidth : 0));
        }
        return new Box(max, entries.size());
    }

    private boolean editing() {
        return mc.gui != null && gg.spaceclient.compat.Screens.current() instanceof gg.spaceclient.ui.HudEditorScreen;
    }

    @Override
    public void draw(GuiGraphicsExtractor graphics, int x, int y) {
        if (!show.get() && !editing()) return;
        if (objective() == null && !editing()) return;
        super.draw(graphics, x, y);
    }

    @Override
    public int getWidth() {
        Objective objective = objective();
        if (objective == null) return 90;
        return box(objective).maxWidth() + 4;
    }

    @Override
    public int getHeight() {
        Objective objective = objective();
        if (objective == null) return 9 * 4;
        return (box(objective).lines() + 1) * 9 + 1;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int x, int y) {
        Objective objective = objective();
        if (objective == null) {
            // Only reached in the editor: a stand-in so there is something to place
            graphics.fill(x, y, x + 90, y + 9, 0x66000000);
            graphics.fill(x, y + 9, x + 90, y + 36, 0x4C000000);
            graphics.text(mc.font, "Scoreboard", x + 18, y + 1, 0xFFFFFFFF, false);
            return;
        }
        if (!show.get()) return;

        Box box = box(objective);
        int lines = box.lines();
        int guiWidth = graphics.guiWidth();
        int guiHeight = graphics.guiHeight();
        // Where the game would put the board's top left corner
        int height = lines * 9;
        int bottom = guiHeight / 2 + height / 3;
        int left = guiWidth - box.maxWidth() - 3 - 2;
        int top = bottom - height - 9 - 1;

        graphics.pose().pushMatrix();
        drawing = true;
        try {
            graphics.pose().translate(x - left, y - top);
            ((HudAccessor) gg.spaceclient.compat.HudCompat.hud()).spaceclient$sidebar(graphics, objective);
        } finally {
            drawing = false;
            graphics.pose().popMatrix();
        }
    }
}
