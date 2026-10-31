package gg.spaceclient.modules;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.music.Cover;
import gg.spaceclient.music.Lyrics;
import gg.spaceclient.music.MediaSession;
import gg.spaceclient.music.MusicWatcher;
import gg.spaceclient.music.NowPlaying;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.ColorSetting;
import gg.spaceclient.setting.IntSetting;
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
    public static final int WIDTH = 200;
    private static final int HEIGHT = 52;

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
        requires(gg.spaceclient.module.Access.VIP);
        addSettings(source, theme, showCover, showProgress, showSource, hideWhenIdle,
                overName, showOnSelf, lyrics, skyLyrics, skyFont, skyHeight, skySize, titleColor, artistColor, accentColor);
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

    /**
     * The lyrics, and only the lyrics, large in the sky ahead of you - in
     * Inter Display, with the word being sung lit. Drawn by SkyLyrics.
     * Fetching them is the same lookup as above; nothing is shared for it.
     */
    private final BooleanSetting skyLyrics = new BooleanSetting(
            "sky_lyrics", "Lyrics in the sky",
            "Only the lyrics, large, floating in the sky ahead of you. Spotify only.", false);
    private final IntSetting skyHeight = new IntSetting(
            "sky_height", "Sky height (degrees)", "How far above the horizon the lyrics float", 28, 10, 70);
    private final ModeSetting skyFont = new ModeSetting(
            "sky_font", "Sky font",
            "SYSTEM: your computer's own (SF Pro on a Mac, Segoe UI on Windows). OWN_FILE: config/spaceclient/sky-font.ttf",
            gg.spaceclient.render.SkyFont.CHOICES, gg.spaceclient.render.SkyFont.SYSTEM);
    private final IntSetting skySize = new IntSetting(
            "sky_size", "Sky text size (percent)", "How large the lyrics are in the sky", 100, 40, 250);

    /** The line to put in the sky now, or NONE. */
    public Lyrics.Moment skyMoment() {
        if (!isEnabled() || !skyLyrics.get()) return Lyrics.Moment.NONE;
        NowPlaying playing = track();
        if (playing.isEmpty() || !playing.playing()) return Lyrics.Moment.NONE;
        double position = MediaSession.position();
        if (position < 0) return Lyrics.Moment.NONE;
        return Lyrics.moment(playing.artist(), playing.title(), position);
    }

    /** Which typeface the sky lyrics are set in (SkyFont). */
    public String skyFont() { return skyFont.get(); }

    /** Degrees above the horizon. */
    public int skyHeight() { return skyHeight.get(); }

    /** 1 is the default size. */
    public float skyScale() { return skySize.get() / 100f; }

    /** The colour the sung word is lit in, from the card's theme. */
    public int skyAccent() { return accentColor.get(); }

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
    public int getHeight() { return HEIGHT; }

    // ---------------- look ----------------

    /**
     * The colours of one player: plate, accent, title, subtitle, bar track.
     *
     * Both players get the same dark card; the accent is what tells them
     * apart - Spotify green, Amazon Music light blue - and it changes over by
     * itself when the music moves from one app to the other.
     */
    public record Palette(int plate, int accent, int title, int sub, int track) {}

    private static final Palette SPOTIFY =
            new Palette(0xE8101012, 0xFF1DB954, 0xFFFFFFFF, 0xFFA7A7A7, 0xFF3A3A3E);
    private static final Palette AMAZON =
            new Palette(0xE80C141C, 0xFF4CC8F4, 0xFFFFFFFF, 0xFFA3BACB, 0xFF2C3C4A);

    public Palette palette(NowPlaying playing) {
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

    /** Square-ish, like a player's mini view, rather than a capsule. */
    @Override
    protected int plateRadius(int plateHeight) { return 3; }

    /**
     * Nothing at all while nothing plays, when that is what was asked for -
     * the plate used to stay behind as an empty box. Except in the HUD
     * editor, where an element you cannot see is an element you cannot place.
     */
    @Override
    public void draw(GuiGraphicsExtractor graphics, int x, int y) {
        boolean editing = mc.gui != null
                && gg.spaceclient.compat.Screens.current() instanceof gg.spaceclient.ui.HudEditorScreen;
        if (!shouldDraw() && !editing) return;
        super.draw(graphics, x, y);
    }

    // Layout, in the element's own units:
    //
    //   +-------+ ■ PLAYING
    //   | cover | Title
    //   |       | Artist
    //   +-------+ ♪ lyric line
    //             ━━━━━━━━━━━━━━━━──────   ⏮ ⏯ ⏭
    //             0:56 / 2:00
    private static final int COVER = 40;
    private static final int TEXT_X = COVER + 6;
    private static final int ROW_Y = COVER + 3;

    /** Size of one control square, and the gap between them. */
    public static final int CONTROL = 11;
    public static final int CONTROL_GAP = 2;

    /** Where the first control square sits, from the element's corner. */
    public static int controlsX() { return WIDTH - (CONTROL * 3 + CONTROL_GAP * 2); }
    public static int controlsY() { return ROW_Y; }

    @Override
    public void render(GuiGraphicsExtractor graphics, int x, int y) {
        NowPlaying playing = track();
        boolean idle = playing.isEmpty();
        Palette colours = palette(playing);

        // A hairline along the top, as the players' own mini views have
        graphics.fill(x - 5, y - 5, x + WIDTH + 5, y - 4, 0x40FFFFFF);

        // Cover, or a record in its place while there is none
        Identifier cover = showCover.get() ? Cover.texture(playing) : null;
        boolean drewCover = cover != null
                && gg.spaceclient.ui.Textures.draw(graphics, cover, x, y, COVER, COVER);
        if (!drewCover) {
            graphics.fill(x, y, x + COVER, y + COVER, 0xFF1C1C20);
            drawRecord(graphics, x + (COVER - 28) / 2, y + (COVER - 28) / 2, 28, colours.accent());
        }

        int textX = x + TEXT_X;
        int room = WIDTH - TEXT_X;

        // Status: a small square and a word, in the accent while playing
        String state = idle ? "IDLE" : playing.playing() ? "PLAYING" : "PAUSED";
        int stateColour = !idle && playing.playing() ? colours.accent() : 0xFF8A8A8E;
        graphics.fill(textX, y + 2, textX + 3, y + 5, stateColour);
        String stateText = state;
        if (showSource.get() && !idle) {
            stateText += "  \u00B7  " + (playing.source().equals("Spotify") ? "SPOTIFY" : "AMAZON MUSIC");
        }
        small(graphics, stateText, textX + 5, y + 1, stateColour);

        String title = idle ? "Nothing playing" : playing.title();
        String subtitle = idle
                ? MusicWatcher.status()
                : playing.artist().isEmpty() ? playing.source() : playing.artist();

        String shownTitle = trimBold(title, room);
        graphics.text(mc.font,
                net.minecraft.network.chat.Component.literal(shownTitle)
                        .withStyle(net.minecraft.ChatFormatting.BOLD),
                textX, y + 9, colours.title(), false);
        graphics.text(mc.font, trim(subtitle, room), textX, y + 20, colours.sub(), false);

        double position = idle ? -1 : MediaSession.position();
        double duration = idle ? -1 : MediaSession.duration();

        if (lyrics.get() && !idle) {
            Lyrics.Moment moment = position < 0 ? Lyrics.Moment.NONE
                    : Lyrics.moment(playing.artist(), playing.title(), position);
            if (!moment.isEmpty()) drawLyric(graphics, moment, textX, y, room, colours.accent());
        }

        // Bottom row: bar and time on the left, the three controls on the right
        int barRight = x + controlsX() - 6;
        if (showProgress.get() && position >= 0 && duration > 0) {
            int barW = barRight - textX;
            int filled = (int) Math.round(barW * Math.min(1.0, position / duration));
            graphics.fill(textX, y + ROW_Y + 1, barRight, y + ROW_Y + 3, colours.track());
            graphics.fill(textX, y + ROW_Y + 1, textX + filled, y + ROW_Y + 3, colours.accent());

            String time = clock(position) + " / " + clock(duration);
            small(graphics, time, textX, y + ROW_Y + 6, colours.sub());
        }

        if (!idle) {
            int cx = x + controlsX();
            int cy = y + controlsY();
            drawControl(graphics, cx, cy, CONTROL, 0, playing.playing(), colours, false);
            drawControl(graphics, cx + CONTROL + CONTROL_GAP, cy, CONTROL, 1, playing.playing(), colours, false);
            drawControl(graphics, cx + (CONTROL + CONTROL_GAP) * 2, cy, CONTROL, 2, playing.playing(), colours, false);
        }
    }

    /**
     * One control square: 0 previous, 1 play or pause, 2 next.
     *
     * Public because the chat puts real, clickable buttons over these, and
     * they have to look exactly like what they cover.
     */
    public static void drawControl(GuiGraphicsExtractor graphics, int x, int y, int size,
                                   int kind, boolean playing, Palette colours, boolean hovered) {
        int base = kind == 1 ? colours.accent() : 0xFF2A2A2F;
        if (hovered) base = kind == 1 ? (base & 0x00FFFFFF) | 0xC0000000 : 0xFF3A3A40;
        graphics.fill(x, y, x + size, y + size, base);

        int glyph = kind == 1 ? 0xFF0B0B0C : 0xFFE8E8E8;
        int inner = Math.max(5, size - 6);
        int gx = x + (size - inner) / 2;
        int gy = y + (size - inner) / 2;

        switch (kind) {
            case 0 -> {
                graphics.fill(gx, gy, gx + 1, gy + inner, glyph);
                triangle(graphics, gx + 1, gy, inner, false, glyph);
            }
            case 2 -> {
                triangle(graphics, gx, gy, inner, true, glyph);
                graphics.fill(gx + inner - 1, gy, gx + inner, gy + inner, glyph);
            }
            default -> {
                if (playing) {
                    int bar = Math.max(1, inner / 3);
                    graphics.fill(gx, gy, gx + bar, gy + inner, glyph);
                    graphics.fill(gx + inner - bar, gy, gx + inner, gy + inner, glyph);
                } else {
                    triangle(graphics, gx + 1, gy, inner, true, glyph);
                }
            }
        }
    }

    /** A solid triangle of the given height, pointing right or left. */
    private static void triangle(GuiGraphicsExtractor graphics, int x, int y, int height,
                                 boolean right, int colour) {
        int half = (height - 1) / 2;
        for (int row = 0; row < height; row++) {
            int w = half - Math.abs(row - half) + 1;
            if (right) graphics.fill(x, y + row, x + w, y + row + 1, colour);
            else graphics.fill(x + half + 1 - w, y + row, x + half + 1, y + row + 1, colour);
        }
    }

    /**
     * The lyric line, whole.
     *
     * It used to be cut to the card's width with "..", which on a long line
     * left most of the words off - the one thing the line is there for. Now a
     * line that does not fit is drawn smaller, and if it still does not fit it
     * is wrapped onto a second small line in the same space.
     */
    private void drawLyric(GuiGraphicsExtractor graphics, Lyrics.Moment moment, int x, int y,
                           int room, int accent) {
        // The note in front shifts every character position by two
        String line = "\u266A " + moment.text();
        int wordStart = moment.hasWord() ? moment.wordStart() + 2 : -1;
        int wordEnd = moment.hasWord() ? moment.wordEnd() + 2 : -1;

        if (mc.font.width(line) <= room) {
            sung(graphics, line, 0, line.length(), wordStart, wordEnd, x, y + 31, 1f, accent);
            return;
        }

        int smallRoom = (int) (room / SMALL);
        if (mc.font.width(line) <= smallRoom) {
            sung(graphics, line, 0, line.length(), wordStart, wordEnd, x, y + 32, SMALL, accent);
            return;
        }

        // Two small lines, broken between words
        int split = line.length();
        int lastSpace = -1;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) != ' ') continue;
            if (mc.font.width(line.substring(0, i)) > smallRoom && lastSpace > 0) { split = lastSpace; break; }
            lastSpace = i;
        }
        if (split == line.length() && mc.font.width(line) > smallRoom && lastSpace > 0) split = lastSpace;

        sung(graphics, line, 0, split, wordStart, wordEnd, x, y + 29, SMALL, accent);
        if (split < line.length()) {
            // Whatever still does not fit is left off the end of the second line
            int end = line.length();
            while (end > split + 1 && mc.font.width(line.substring(split + 1, end)) > smallRoom) end--;
            sung(graphics, line, split + 1, end, wordStart, wordEnd, x, y + 36, SMALL, accent);
        }
    }

    /** Words already sung, the word being sung, and the ones still to come. */
    private static final int SUNG = 0xFFE6E6E6;
    private static final int TO_COME = 0xFF8E8E96;

    /**
     * Draws part of a lyric line - from `from` to `to` - with the word being
     * sung picked out: what came before it bright, the word in the accent
     * colour, what follows dimmed. Without a word the whole part is bright.
     */
    private void sung(GuiGraphicsExtractor graphics, String line, int from, int to,
                      int wordStart, int wordEnd, int x, int y, float scale, int accent) {
        if (to <= from) return;
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x, y);
            if (scale != 1f) graphics.pose().scale(scale, scale);

            String part = line.substring(from, to);
            if (wordStart < 0 || wordEnd <= from || wordStart >= to) {
                graphics.text(mc.font, part, 0, 0, wordStart >= to ? TO_COME : SUNG, false);
                return;
            }
            int a = Math.max(from, wordStart);
            int b = Math.min(to, wordEnd);
            String before = line.substring(from, a);
            String word = line.substring(a, b);
            String after = line.substring(b, to);
            int cursor = 0;
            graphics.text(mc.font, before, cursor, 0, SUNG, false);
            cursor += mc.font.width(before);
            graphics.text(mc.font, word, cursor, 0, accent | 0xFF000000, false);
            cursor += mc.font.width(word);
            graphics.text(mc.font, after, cursor, 0, TO_COME, false);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    /** Minutes and seconds, the way every player shows them. */
    private static String clock(double seconds) {
        int total = (int) Math.floor(seconds);
        return (total / 60) + ":" + String.format("%02d", total % 60);
    }

    private static final float SMALL = 0.75f;

    /** Text at three quarters size, for the status and the time. */
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

    /** Like trim, measured in bold, which is a pixel wider per character. */
    private String trimBold(String text, int room) {
        if (boldWidth(text) <= room) return text;
        while (text.length() > 1 && boldWidth(text + "..") > room) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "..";
    }

    private int boldWidth(String text) {
        return mc.font.width(net.minecraft.network.chat.Component.literal(text)
                .withStyle(net.minecraft.ChatFormatting.BOLD));
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
