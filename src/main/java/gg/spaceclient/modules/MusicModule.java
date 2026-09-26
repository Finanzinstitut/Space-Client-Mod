package gg.spaceclient.modules;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.music.Cover;
import gg.spaceclient.music.Lyrics;
import gg.spaceclient.music.MediaSession;
import gg.spaceclient.music.MusicWatcher;
import gg.spaceclient.music.NowPlaying;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.ColorSetting;
import gg.spaceclient.setting.ModeSetting;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.Arrays;

/**
 * Shows what the local Spotify or Amazon Music app is playing.
 *
 * The card takes the colours of the player it shows - Spotify black and
 * green, Amazon Music blue - with the album cover, a time bar and how long
 * the song has left.
 *
 * Only those two are read, on purpose: the track comes from the desktop app's
 * window title, so music playing in a browser has no process to read and is
 * ignored.
 *
 * Playback controls appear when the chat is open - see MusicControls, which
 * puts real buttons over this element while a screen has the mouse.
 */
public class MusicModule extends HudModule {
    private static final int WIDTH = 200;
    private static final int ART = 40;
    private static final int CARD_H = ART + 4;
    private static final int LYRIC_H = 12;

    private final ModeSetting source = new ModeSetting(
            "source", "Source", "Which player to read",
            Arrays.asList("BOTH", "SPOTIFY", "AMAZON"), "BOTH");

    private final BooleanSetting showSource = new BooleanSetting(
            "show_source", "Show player name", "Print which app the track came from", true);

    private final BooleanSetting hideWhenIdle = new BooleanSetting(
            "hide_when_idle", "Hide when nothing plays", "Draw nothing while paused", true);

    private final ColorSetting titleColor = new ColorSetting(
            "title_color", "Title colour", "Colour of the track name", 0xFFFFFFFF);

    private final ColorSetting artistColor = new ColorSetting(
            "artist_color", "Artist colour", "Colour of the artist line", 0xFF9A95C9);

    private final ModeSetting theme = new ModeSetting(
            "theme", "Colours",
            "PLAYER: Spotify green or Amazon Music blue, whichever is playing. "
                    + "CUSTOM: the colours below",
            Arrays.asList("PLAYER", "CUSTOM"), "PLAYER");

    private final ColorSetting accentColor = new ColorSetting(
            "accent_color", "Accent colour", "Bar and highlights, with custom colours", 0xFF7C5CFF);

    private final BooleanSetting showCover = new BooleanSetting(
            "show_cover", "Cover", "Show the album cover", true);

    private final BooleanSetting showProgress = new BooleanSetting(
            "show_progress", "Progress", "Show the time bar and how long is left", true);

    /**
     * Off by default, and that is the whole point of it being a setting: this
     * publishes what you are listening to, continuously, to anyone running the
     * client who can see you. That is a thing to opt into, not out of.
     */
    private final BooleanSetting overName = new BooleanSetting(
            "over_name", "Over name",
            "Show your track above your name tag to other Space Client players", false);

    /**
     * Starts the control host when the element is switched on.
     *
     * Starting it is the slow part, and the moment somebody presses play is
     * exactly when that is felt. Doing it here means the press is a line of
     * text down a pipe that is already open.
     */
    @Override
    protected void onEnable() {
        gg.spaceclient.music.MusicWatcher.warmControls();
    }

    public MusicModule() {
        // Top centre by default, where the players' own mini views sit
        super("music", "Now Playing", "Shows the track from Spotify or Amazon Music",
                0.34f, 0.02f, false);
        addSettings(source, theme, showCover, showProgress, showSource, hideWhenIdle,
                overName, showOnSelf, lyrics, titleColor, artistColor, accentColor);
    }

    /**
     * Normally your own song is not drawn over your own head - it is already in
     * the HUD element, so it would just be there twice. This puts it there
     * anyway, which is the only way to check the whole chain without a second
     * account: the line you see has been to the worker and back.
     */
    private final BooleanSetting showOnSelf = new BooleanSetting(
            "over_name_self", "Over my own name",
            "Also draw your own track over your head, for testing", false);

    /** Whether your own track is drawn over your own head too. */
    public boolean showsOnSelf() {
        return showOnSelf.get();
    }

    /**
     * Off by default, and the description carries the warning rather than
     * burying it: the lyrics come from a community database, not from a
     * licensed source, and switching this on sends a line of someone else's
     * copyrighted words through the Space Client backend.
     */
    private final BooleanSetting lyrics = new BooleanSetting(
            "lyrics", "Lyrics",
            "Show the current line under the track. Lyrics come from LRCLIB, "
                    + "a community database - they are not licensed, and enabling "
                    + "this shares one line with other players. Spotify only.", false);

    /** Whether the current lyric line should be shown and shared. */
    public boolean showsLyrics() {
        return lyrics.get();
    }

    /** Whether the track is being published over the name tag. */
    public boolean sharesOverName() {
        return overName.get();
    }

    /** The track, after the source filter is applied. */
    public NowPlaying track() {
        NowPlaying playing = MusicWatcher.current();
        if (playing.isEmpty()) return playing;

        if (source.is("SPOTIFY") && !playing.source().equals("Spotify")) return NowPlaying.NOTHING;
        if (source.is("AMAZON") && !playing.source().equals("Amazon Music")) return NowPlaying.NOTHING;
        return playing;
    }

    public boolean shouldDraw() {
        return !(track().isEmpty() && hideWhenIdle.get());
    }

    @Override
    public void onTick() {
        MusicWatcher.tick();
    }

    @Override
    public int getWidth() { return WIDTH; }

    @Override
    public int getHeight() { return CARD_H + (lyrics.get() ? LYRIC_H : 0); }

    // ---------------- look ----------------

    /**
     * The colours of one player: plate, accent, title, subtitle, bar track.
     *
     * Spotify's own black and green, and for Amazon Music its light blue on a
     * deep blue, so which app is playing can be told at a glance - and the
     * card changes over by itself when the music moves from one to the other.
     */
    private record Palette(int plate, int accent, int title, int sub, int track) {}

    private static final Palette SPOTIFY =
            new Palette(0xE6121212, 0xFF1DB954, 0xFFFFFFFF, 0xFFB3B3B3, 0xFF4D4D4D);
    private static final Palette AMAZON =
            new Palette(0xE60B1A2A, 0xFF25D1DA, 0xFFFFFFFF, 0xFFA7C7D9, 0xFF2A4458);

    private Palette palette(NowPlaying playing) {
        if (theme.is("PLAYER") && !playing.isEmpty()) {
            return playing.source().equals("Spotify") ? SPOTIFY : AMAZON;
        }
        // Your own colours, on the plate the HUD settings give every element
        return new Palette(super.plateColour(), accentColor.get(),
                titleColor.get(), artistColor.get(), 0x55FFFFFF);
    }

    @Override
    protected int plateColour() {
        return palette(track()).plate();
    }

    /**
     * Nothing at all while nothing plays, when that is what was asked for -
     * the plate used to stay behind as an empty box. Except in the HUD
     * editor, where an element you cannot see is an element you cannot place.
     */
    @Override
    public void draw(GuiGraphicsExtractor graphics, int x, int y) {
        boolean editing = mc.gui != null
                && mc.gui.screen() instanceof gg.spaceclient.ui.HudEditorScreen;
        if (!shouldDraw() && !editing) return;
        super.draw(graphics, x, y);
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int x, int y) {
        NowPlaying playing = track();
        boolean idle = playing.isEmpty();
        Palette colours = palette(playing);

        // Cover, or a record in its place while there is none
        Identifier cover = showCover.get() ? Cover.texture(playing) : null;
        boolean drewCover = cover != null
                && gg.spaceclient.ui.Textures.draw(graphics, cover, x, y + 2, ART, ART);
        if (!drewCover) {
            graphics.fill(x, y + 2, x + ART, y + 2 + ART, (colours.accent() & 0x00FFFFFF) | 0x33000000);
            drawRecord(graphics, x + (ART - 26) / 2, y + 2 + (ART - 26) / 2, 26, colours.accent());
        }

        int textX = x + ART + 8;
        int room = WIDTH - ART - 8;

        String title = idle ? "Nothing playing" : playing.title();
        String subtitle = idle
                ? MusicWatcher.status()
                : playing.artist().isEmpty() ? playing.source() : playing.artist();

        graphics.text(mc.font, trim(title, room), textX, y + 4, colours.title(), false);
        graphics.text(mc.font, trim(subtitle, room), textX, y + 15, colours.sub(), false);

        double position = idle ? -1 : MediaSession.position();
        double duration = idle ? -1 : MediaSession.duration();

        if (showProgress.get() && position >= 0 && duration > 0) {
            int barY = y + 28;
            int barW = room;
            int filled = (int) Math.round(barW * Math.min(1.0, position / duration));
            graphics.fill(textX, barY, textX + barW, barY + 3, colours.track());
            graphics.fill(textX, barY, textX + filled, barY + 3, colours.accent());
            // A knob at the play head, like the players draw it
            graphics.fill(textX + filled - 1, barY - 1, textX + filled + 2, barY + 4, colours.title());

            String elapsed = clock(position);
            String left = "-" + clock(Math.max(0, duration - position));
            small(graphics, elapsed, textX, y + 34, colours.sub());
            small(graphics, left, textX + barW - smallWidth(left), y + 34, colours.sub());

            if (showSource.get()) {
                String label = playing.source().equals("Spotify") ? "SPOTIFY" : "AMAZON MUSIC";
                small(graphics, label, textX + (barW - smallWidth(label)) / 2, y + 34, colours.accent());
            }
        } else if (showSource.get() && !idle) {
            String label = playing.source().equals("Spotify") ? "SPOTIFY" : "AMAZON MUSIC";
            small(graphics, label, textX, y + 30, colours.accent());
        }

        if (lyrics.get()) {
            String line = idle || position < 0 ? ""
                    : Lyrics.line(playing.artist(), playing.title(), position);
            if (line.isEmpty()) line = idle ? "" : "\u266A";
            int lineW = mc.font.width(trim(line, WIDTH));
            graphics.text(mc.font, trim(line, WIDTH), x + (WIDTH - lineW) / 2,
                    y + CARD_H + 2, colours.accent(), false);
        }
    }

    /** Minutes and seconds, the way every player shows them. */
    private static String clock(double seconds) {
        int total = (int) Math.floor(seconds);
        return (total / 60) + ":" + String.format("%02d", total % 60);
    }

    private static final float SMALL = 0.75f;

    /** Text at three quarters size, for the times under the bar. */
    private void small(GuiGraphicsExtractor graphics, String text, int x, int y, int colour) {
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x, y);
            graphics.pose().scale(SMALL, SMALL);
            graphics.text(mc.font, text, 0, 0, colour, false);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private int smallWidth(String text) {
        return Math.round(mc.font.width(text) * SMALL);
    }

    /** A small record, drawn from rings so no texture has to load. */
    private void drawRecord(GuiGraphicsExtractor graphics, int x, int y, int size, int label) {
        int radius = size / 2;
        int cx = x + radius;
        int cy = y + radius;

        for (int row = 0; row < size; row++) {
            int dy = row - radius;
            double half = Math.sqrt(Math.max(0, radius * radius - dy * dy));
            if (half < 0.5) continue;

            int x1 = (int) Math.round(cx - half);
            int x2 = (int) Math.round(cx + half);
            graphics.fill(x1, y + row, x2, y + row + 1, 0xFF1A1A22);
        }

        // Label in the middle and a highlight ring, so it reads as a record
        graphics.fill(cx - 3, cy - 3, cx + 3, cy + 3, label);
        graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFF1A1A22);
    }

    private String trim(String text, int room) {
        if (mc.font.width(text) <= room) return text;
        while (text.length() > 1 && mc.font.width(text + "..") > room) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "..";
    }
}
