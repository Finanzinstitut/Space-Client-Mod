package gg.spaceclient.modules;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.net.Presence;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.ColorSetting;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;

/**
 * How many people are playing with Space Client, and how many are next to you.
 *
 * Only this client can show this, which is the reason it exists. The presence
 * roster is already fetched for the name tag badge, so the global figure costs
 * nothing that was not already being paid, and the nearby figure is the same
 * set intersected with the players in the world.
 *
 * The nearby count is the interesting one. On a shared server it answers a
 * question no other overlay can - whether the people around you are running the
 * same thing you are.
 */
public class SpacePlayersModule extends HudModule {

    private final BooleanSetting showNearby = new BooleanSetting(
            "show_nearby", "Show nearby", "Include how many are in this world", true);

    private final BooleanSetting compact = new BooleanSetting(
            "compact", "Compact", "Numbers only, without the words", false);

    private final ColorSetting textColor = new ColorSetting(
            "text_color", "Text colour", "Colour of the readout", 0xFF9AD8FF);

    public SpacePlayersModule() {
        super("spaceplayers", "Space Players",
                "Space Client players online, and how many are near you",
                0.85f, 0.32f, false);
        addSettings(showNearby, compact, textColor);
    }

    /**
     * Twice a second.
     *
     * The roster itself only refreshes every ninety seconds, so this is really
     * about the nearby count, and that only changes as people walk in and out
     * of range.
     */
    @Override
    protected long refreshMillis() { return 500; }

    /**
     * Players in the loaded world who carry a badge.
     *
     * Counts everyone the client knows about rather than only those on screen -
     * someone standing behind you is still nearby, and a figure that changed
     * when you turned around would be a worse answer than a slightly generous
     * one.
     */
    private int nearby() {
        try {
            if (mc.level == null) return 0;
            int count = 0;
            for (Player player : mc.level.players()) {
                if (Presence.hasBadge(player.getUUID())) count++;
            }
            return count;
        } catch (Throwable ignored) {
            // The player list is not worth a broken frame
            return 0;
        }
    }

    private String text() { return cachedText(this::buildText); }

    private String buildText() {
        int online = Presence.onlineCount();
        if (online < 0) return compact.get() ? "--" : "Space: --";

        if (!showNearby.get()) {
            return compact.get() ? String.valueOf(online) : online + " on Space Client";
        }

        int here = nearby();
        return compact.get()
                ? online + " / " + here
                : online + " online, " + here + " here";
    }

    @Override
    public int getWidth() { return mc.font.width(text()); }

    @Override
    public int getHeight() { return mc.font.lineHeight; }

    @Override
    public void render(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.text(mc.font, text(), x, y, textColor.get(), true);
    }
}
