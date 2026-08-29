package gg.spaceclient.modules;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.net.Twitch;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.ColorSetting;
import gg.spaceclient.ui.Textures;
import gg.spaceclient.ui.Theme;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Twitch follower count and most recent follower, with the channel mark.
 *
 * Reads whatever Twitch last told the worker; it never talks to Twitch itself.
 * When the channel is not linked it says so rather than showing a zero, because
 * a zero is a number someone might believe.
 */
public class TwitchModule extends HudModule {

    private static final Identifier ICON =
            Identifier.fromNamespaceAndPath("spaceclient", "textures/gui/stream/twitch.png");

    private static final int ICON_SIZE = 8;
    private static final int ICON_GAP = 4;

    private final ColorSetting textColor = new ColorSetting(
            "text_color", "Text colour", "Colour of the follower count", 0xFFFFFFFF);

    private final BooleanSetting showLast = new BooleanSetting(
            "show_last", "Show newest follower",
            "Append the most recent follower's name", true);

    private final BooleanSetting showIcon = new BooleanSetting(
            "show_icon", "Show icon", "Draw the Twitch mark in front", true);

    public TwitchModule() {
        super("twitch", "Twitch Followers",
                "Follower count and newest follower for your linked channel",
                0.02f, 0.32f, false);
        addSettings(showIcon, showLast, textColor);
    }

    /**
     * Once a second is plenty.
     *
     * The underlying number only changes once a minute when the worker is
     * polled, so rebuilding this string any faster would be formatting the same
     * characters over and over.
     */
    @Override
    protected long refreshMillis() { return 1000; }

    private String text() { return cachedText(this::buildText); }

    private String buildText() {
        if (!Twitch.isLinked()) return "Twitch not linked";

        int followers = Twitch.followers();
        if (followers < 0) return "Loading...";

        StringBuilder out = new StringBuilder();
        out.append(followers == 1 ? "1 follower" : followers + " followers");

        String last = Twitch.lastFollower();
        if (showLast.get() && !last.isEmpty()) {
            out.append("  +").append(last);
        }
        return out.toString();
    }

    private int iconWidth() {
        return showIcon.get() ? ICON_SIZE + ICON_GAP : 0;
    }

    @Override
    public int getWidth() { return iconWidth() + mc.font.width(text()); }

    @Override
    public int getHeight() { return Math.max(ICON_SIZE, mc.font.lineHeight); }

    @Override
    public void render(GuiGraphicsExtractor graphics, int x, int y) {
        if (showIcon.get()) {
            // Centred against the line rather than sat on the baseline, so the
            // mark and the text read as one row
            int iconY = y + (mc.font.lineHeight - ICON_SIZE) / 2;

            // Through Textures rather than a direct blit: the texture call's
            // signature on 26.2 is unverified, and that class already resolves
            // it at runtime so a wrong guess costs a coloured square instead of
            // a failed build.
            if (!Textures.draw(graphics, ICON, x, iconY, ICON_SIZE, ICON_SIZE)) {
                graphics.fill(x, iconY, x + ICON_SIZE, iconY + ICON_SIZE, 0xFF9146FF);
            }
        }

        int color = Twitch.isLinked() ? textColor.get() : Theme.TEXT_DIM;
        graphics.text(mc.font, text(), x + iconWidth(), y, color, true);
    }
}
