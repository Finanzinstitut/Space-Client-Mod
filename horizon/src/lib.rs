//! Space Client for Horizon.
//!
//! The Java client's HUD modules, keystrokes and zoom, as a Horizon mod:
//! Horizon runs no Java mods, so this is the part of Space Client that can
//! live in its sandbox. Everything the API lets a client mod see is here;
//! the features that need the network (friends, badges, now playing,
//! skins), the world around the player (light levels, Litematica) or the
//! inventory (armour, durability) have no way in yet.
//!
//! Drawing: the readouts are computed each tick, the shapes built in the
//! frame hook and handed to the game only when something on screen changed.
//! The game keeps them and draws them every frame itself.

pub mod format;

use horizon_mod_sdk::{
    Mod, Registration, game, input,
    render::{self, FillShape, Point, Rect, Shape, TextShape},
    storage,
    ui::{self, ListWidget, TextWidget, Widget},
};

/// The plate behind each readout: the Java client's glass, near black.
const PLATE: u32 = 0xE60E_0E14;
const TEXT: u32 = 0xF3F3F6;
const DIM: u32 = 0x97979F;
const HELD: u32 = 0xF0F3_F3F6;
const INK_HELD: u32 = 0x0808_0A;
const PAD: f32 = 5.0;
const LINE: f32 = 8.0;
const GAP: f32 = 3.0;
const MARGIN: f32 = 4.0;
const KEY: f32 = 22.0;
const KEY_GAP: f32 = 2.0;

/// One switchable part of the HUD: storage name, label, default.
#[derive(Clone, Copy, PartialEq, Eq)]
enum Part {
    Fps,
    Cps,
    Coordinates,
    Direction,
    DayTime,
    Speed,
    Session,
    Health,
    LookingAt,
    Compass,
    Keystrokes,
    Zoom,
    Music,
}

const PARTS: [(Part, &str, &str, bool); 13] = [
    (Part::Fps, "fps", "FPS", true),
    (Part::Cps, "cps", "CPS", true),
    (Part::Coordinates, "coordinates", "Coordinates", true),
    (Part::Direction, "direction", "Direction", false),
    (Part::DayTime, "daytime", "Day Time", false),
    (Part::Speed, "speed", "Speedometer", false),
    (Part::Session, "session", "Session Time", false),
    (Part::Health, "health", "Health & Food", false),
    (Part::LookingAt, "looking_at", "Looking At", false),
    (Part::Compass, "compass", "Compass", false),
    (Part::Keystrokes, "keystrokes", "Keystrokes", true),
    (Part::Zoom, "zoom", "Zoom (hold C)", true),
    (Part::Music, "music", "Now Playing (/music)", false),
];

/// Buttons of the /music screen.
const MUSIC_PREV: u32 = 201;
const MUSIC_TOGGLE: u32 = 202;
const MUSIC_NEXT: u32 = 203;

/// The settings list; its rows are the parts in order, then these two.
const LIST: u32 = 1;
const SIDE_ROW: usize = PARTS.len();
const BACKGROUND_ROW: usize = PARTS.len() + 1;

/// What the HUD shows; drawing happens when it differs from the last one.
#[derive(Clone, PartialEq, Default)]
struct View {
    size: (u32, u32),
    lines: Vec<String>,
    keys: Option<([(bool, String); 4], bool, bool, bool)>,
    compass: Option<i32>,
    music: Option<MusicView>,
}

/// The Now Playing card as drawn: rounded so a playing song redraws a few
/// times a second, not every frame.
#[derive(Clone, PartialEq, Default)]
struct MusicView {
    state: String,
    source: String,
    title: String,
    artist: String,
    lyric: Option<(String, String, String)>,
    /// Thousandths of the song played, when its length is known.
    progress: Option<u32>,
    time: String,
}

struct SpaceClient {
    on: [bool; PARTS.len()],
    /// The readouts' column on the right instead of the left.
    right: bool,
    plates: bool,
    // What the tick worked out
    player: Option<game::Player>,
    day_time: Option<u64>,
    speed: f64,
    last_pos: Option<(f64, f64)>,
    session_ticks: u64,
    looking_at: Option<String>,
    // Zoom
    zoomed: bool,
    zoom: f32,
    shown: Option<View>,
    /// The /sc screen is open (the settings page in Options > Mods is not it).
    screen_open: bool,
    /// What the launcher last wrote for Now Playing; None without a launcher.
    now_playing: Option<format::NowPlaying>,
    music_screen: bool,
}

fn stored(key: &str) -> Option<String> {
    storage::get(key).and_then(|v| String::from_utf8(v).ok())
}

impl SpaceClient {
    fn is_on(&self, part: Part) -> bool {
        PARTS.iter().position(|p| p.0 == part).is_some_and(|i| self.on[i])
    }

    fn load() -> Self {
        let mut on = PARTS.map(|p| p.3);
        if let Some(text) = stored("settings") {
            for line in text.lines() {
                let Some((key, value)) = line.split_once('=') else { continue };
                if let Some(i) = PARTS.iter().position(|p| p.1 == key.trim()) {
                    on[i] = value.trim() == "true";
                }
            }
        }
        let flag = |key: &str, default: bool| stored(key).map_or(default, |v| v.trim() == "true");
        SpaceClient {
            on,
            right: flag("right", false),
            plates: flag("plates", true),
            player: None,
            day_time: None,
            speed: 0.0,
            last_pos: None,
            session_ticks: 0,
            looking_at: None,
            zoomed: false,
            zoom: 4.0,
            shown: None,
            screen_open: false,
            now_playing: None,
            music_screen: false,
        }
    }

    fn save(&self) {
        let text: String = PARTS
            .iter()
            .zip(self.on)
            .map(|(p, on)| format!("{}={}\n", p.1, on))
            .collect();
        let _ = storage::set("settings", text.as_bytes());
        let _ = storage::set("right", if self.right { b"true" } else { b"false" });
        let _ = storage::set("plates", if self.plates { b"true" } else { b"false" });
    }

    /// The settings: the mod's page in Options > Mods, and the /sc screen.
    /// One list, whose rows the player clicks to switch: it scrolls, where
    /// a button each would not fit a small window.
    fn widgets(&self) -> Vec<Widget> {
        let state = |b: bool| if b { "ON" } else { "OFF" };
        let mut rows: Vec<String> = PARTS
            .iter()
            .enumerate()
            .map(|(i, part)| format!("{}: {}", part.2, state(self.on[i])))
            .collect();
        rows.push(format!("Readouts: {}", if self.right { "Right" } else { "Left" }));
        rows.push(format!("Background: {}", state(self.plates)));
        vec![
            Widget::Text(TextWidget {
                id: 0,
                text: "Click to switch. Hold C to zoom, scroll to zoom further.".into(),
                color: DIM,
            }),
            Widget::List(ListWidget { id: LIST, rows, selected: None }),
        ]
    }

    fn page(&self) {
        ui::set_settings(&self.widgets());
    }

    /// `/music`: the controls; `/music pause|next|prev` straight away.
    fn music_command(&mut self, args: &str) -> Result<Option<String>, String> {
        match args {
            "" => {
                self.music_screen = true;
                self.screen_open = false;
                self.music_screen_open();
                Ok(None)
            }
            "pause" | "play" | "toggle" => self.music_now("toggle"),
            "next" | "skip" => self.music_now("next"),
            "prev" | "previous" | "back" => self.music_now("prev"),
            _ => Err("Use /music, or /music pause, next or prev".into()),
        }
    }

    fn music_now(&mut self, what: &str) -> Result<Option<String>, String> {
        if self.now_playing.is_none() && stored("nowplaying").is_none() {
            return Err("Now Playing needs Horizon started from the Space Client launcher".into());
        }
        self.send_music(what);
        Ok(None)
    }

    /// Hands a press to the launcher, which tells the player.
    fn send_music(&mut self, what: &str) {
        let _ = storage::set("music-command", what.as_bytes());
    }

    fn music_screen_open(&self) {
        let np = self.now_playing.clone().or_else(|| stored("nowplaying").and_then(|t| format::now_playing(&t)));
        let line = match &np {
            Some(np) if !np.title.is_empty() => format!("{} - {}", np.title, np.artist),
            Some(_) => "Nothing is playing".into(),
            None => "Start Horizon from the Space Client launcher to see and control your music".into(),
        };
        let button = |id, label: &str| Widget::Button(ui::ButtonWidget { id, label: label.into() });
        ui::open_screen(&ui::Screen {
            title: "Now Playing".into(),
            widgets: vec![
                Widget::Text(TextWidget { id: 0, text: line, color: 0 }),
                button(MUSIC_TOGGLE, "Play / Pause"),
                button(MUSIC_NEXT, "Next"),
                button(MUSIC_PREV, "Previous"),
            ],
        });
    }

    fn screen(&mut self) {
        ui::open_screen(&ui::Screen {
            title: "Space Client".into(),
            widgets: self.widgets(),
        });
        self.screen_open = true;
    }

    /// The readout lines, in the order the column shows them.
    fn lines(&self) -> Vec<String> {
        let mut out = Vec::new();
        if self.is_on(Part::Fps) {
            out.push(format!("{} FPS", render::frame_stats().fps));
        }
        if self.is_on(Part::Cps) {
            let (left, right) = input::clicks_per_second();
            out.push(format!("{left} | {right} CPS"));
        }
        let Some(p) = &self.player else { return out };
        if self.is_on(Part::Coordinates) {
            out.push(format::coordinates(p.x, p.y, p.z));
        }
        if self.is_on(Part::Direction) {
            out.push(format::direction(p.yaw));
        }
        if self.is_on(Part::DayTime) {
            if let Some(t) = self.day_time {
                out.push(format::day_time(t));
            }
        }
        if self.is_on(Part::Speed) {
            out.push(format::speed(self.speed));
        }
        if self.is_on(Part::Session) {
            out.push(format::session(self.session_ticks));
        }
        if self.is_on(Part::Health) {
            out.push(format::health(p.health, p.food));
        }
        if self.is_on(Part::LookingAt) {
            if let Some(block) = &self.looking_at {
                out.push(block.clone());
            }
        }
        out
    }

    fn view(&self) -> View {
        let (w, h) = render::gui_size();
        let key = |name: &str| input::mapping(name).map_or((false, String::new()), |m| (m.down, m.label));
        View {
            size: (w as u32, h as u32),
            lines: self.lines(),
            keys: (self.is_on(Part::Keystrokes) && self.player.is_some()).then(|| {
                (
                    ["key.forward", "key.left", "key.back", "key.right"].map(key),
                    key("key.jump").0,
                    key("key.attack").0,
                    key("key.use").0,
                )
            }),
            compass: self
                .player
                .as_ref()
                .filter(|_| self.is_on(Part::Compass))
                .map(|p| (p.yaw * 2.0).round() as i32),
            music: self.music_view(),
        }
    }

    fn music_view(&self) -> Option<MusicView> {
        if !self.is_on(Part::Music) {
            return None;
        }
        let np = self.now_playing.as_ref()?;
        Some(MusicView {
            state: np.state.clone(),
            source: np.source.clone(),
            title: np.title.clone(),
            artist: np.artist.clone(),
            lyric: np.line.as_deref().map(|l| format::split_word(l, np.word)),
            progress: (np.duration > 0.0 && np.position >= 0.0)
                .then(|| ((np.position / np.duration).clamp(0.0, 1.0) * 1000.0) as u32),
            time: if np.duration > 0.0 {
                format!("{} / {}", format::clock(np.position), format::clock(np.duration))
            } else {
                String::new()
            },
        })
    }

    fn draw(&mut self) {
        let view = self.view();
        if self.shown.as_ref() == Some(&view) {
            return;
        }
        let width = view.size.0 as f32;
        let mut out = Vec::with_capacity(256);
        let mut y = MARGIN;
        let plate_h = LINE + PAD * 2.0;
        for line in &view.lines {
            let w = render::text_width(line) + PAD * 2.0;
            let x = if self.right { width - MARGIN - w } else { MARGIN };
            if self.plates {
                pill(&mut out, x, y, w, plate_h, PLATE);
            }
            text(&mut out, line, x + PAD, y + PAD, TEXT, !self.plates);
            y += plate_h + GAP;
        }
        if let Some((moves, jump, attack, use_)) = &view.keys {
            let keys_w = 3.0 * KEY + 2.0 * KEY_GAP;
            let keys_h = 3.0 * (KEY + KEY_GAP) + 12.0;
            // Under the readouts when they leave room, else on the other side
            let (right, top) = if y + 2.0 + keys_h <= view.size.1 as f32 - MARGIN {
                (self.right, y + 2.0)
            } else {
                (!self.right, MARGIN)
            };
            let x0 = if right { width - MARGIN - keys_w } else { MARGIN };
            keystrokes(&mut out, x0, top, moves, *jump, *attack, *use_);
        }
        if let Some(yaw2) = view.compass {
            compass(&mut out, width, yaw2 as f32 / 2.0);
        }
        if let Some(music) = &view.music {
            // Top centre, as in the Java client; under the compass when it is on
            let top = if view.compass.is_some() { MARGIN + 22.0 } else { MARGIN };
            music_card(&mut out, width, top, music);
        }
        render::set_hud(&out);
        self.shown = Some(view);
    }

    fn handle_input(&mut self) {
        for event in input::poll() {
            match event {
                input::Event::KeyDown(name) if name == "zoom" && self.is_on(Part::Zoom) => {
                    self.zoomed = true;
                    self.zoom = 4.0;
                    input::claim_wheel(true);
                    self.apply_zoom();
                }
                input::Event::KeyUp(name) if name == "zoom" => {
                    if self.zoomed {
                        self.zoomed = false;
                        input::claim_wheel(false);
                        self.apply_zoom();
                    }
                }
                input::Event::Wheel(steps) if self.zoomed => {
                    // Each notch multiplies, so every notch feels the same
                    self.zoom = (self.zoom * 1.25f32.powf(steps)).clamp(1.5, 10.0);
                    self.apply_zoom();
                }
                _ => {}
            }
        }
    }

    fn apply_zoom(&self) {
        let factor = if self.zoomed { self.zoom } else { 1.0 };
        render::set_zoom(render::Zoom {
            factor,
            smooth: true,
            // Slower turning the further in, as the Java zoom does
            turn: (1.0 / factor).max(0.1),
        });
    }

    fn toggle(&mut self, row: usize) {
        match row {
            SIDE_ROW => self.right = !self.right,
            BACKGROUND_ROW => self.plates = !self.plates,
            i if i < PARTS.len() => {
                self.on[i] = !self.on[i];
                if PARTS[i].0 == Part::Zoom && !self.on[i] && self.zoomed {
                    self.zoomed = false;
                    input::claim_wheel(false);
                    self.apply_zoom();
                }
            }
            _ => return,
        }
        self.save();
        self.shown = None;
    }
}

/// A rounded plate: full rows in the middle, the ends cut from a circle.
fn pill(out: &mut Vec<Shape>, x: f32, y: f32, w: f32, h: f32, color: u32) {
    rounded(out, x, y, w, h, (h / 2.0).floor(), color);
}

/// A rectangle with corners cut from a circle of `radius`.
fn rounded(out: &mut Vec<Shape>, x: f32, y: f32, w: f32, h: f32, radius: f32, color: u32) {
    let radius = radius.min(h / 2.0).min(w / 2.0).floor() as u32;
    let insets = format::pill_insets(radius);
    let r = insets.len() as f32;
    if h > 2.0 * r {
        out.push(fill(x, y + r, w, h - 2.0 * r, color));
    }
    for (row, inset) in insets.iter().enumerate() {
        let inset = *inset as f32;
        let top = y + row as f32;
        let bottom = y + h - 1.0 - row as f32;
        out.push(fill(x + inset, top, w - 2.0 * inset, 1.0, color));
        out.push(fill(x + inset, bottom, w - 2.0 * inset, 1.0, color));
    }
}

fn fill(x: f32, y: f32, w: f32, h: f32, color: u32) -> Shape {
    Shape::Fill(FillShape { rect: Rect { x, y, w, h }, color })
}

fn text(out: &mut Vec<Shape>, s: &str, x: f32, y: f32, color: u32, shadow: bool) {
    out.push(Shape::Text(TextShape {
        text: s.into(),
        at: Point { x: x.round(), y: y.round() },
        color,
        centered: false,
        shadow,
    }));
}

/// Text cut to fit `room` GUI pixels, with an ellipsis when it was cut.
fn fit(s: &str, room: f32) -> String {
    if render::text_width(s) <= room {
        return s.into();
    }
    let mut chars: Vec<char> = s.chars().collect();
    while !chars.is_empty() {
        chars.pop();
        let cut: String = chars.iter().collect::<String>() + "...";
        if render::text_width(&cut) <= room {
            return cut;
        }
    }
    String::new()
}

/// The Now Playing card: state and player, title, artist, the lyric line
/// with the word being sung, and the time bar - the Java card without its
/// cover, which Horizon's mods cannot draw.
fn music_card(out: &mut Vec<Shape>, width: f32, top: f32, m: &MusicView) {
    const W: f32 = 200.0;
    let accent = if m.source.to_lowercase().contains("amazon") { 0x4CC8F4 } else { 0x1DB954 };
    let x = ((width - W) / 2.0).round();
    let room = W - 16.0;
    let mut rows: Vec<(String, u32)> = Vec::new();
    let status = match m.state.as_str() {
        "playing" => format!("PLAYING  \u{00B7}  {}", m.source.to_uppercase()),
        "paused" => format!("PAUSED  \u{00B7}  {}", m.source.to_uppercase()),
        "unsupported" => "NOW PLAYING  \u{00B7}  NO PLAYER FOUND".into(),
        _ => "NOTHING PLAYING".into(),
    };
    let status_colour = if m.state == "playing" { accent } else { DIM };
    rows.push((fit(&status, room), status_colour));
    if !m.title.is_empty() {
        rows.push((fit(&m.title, room), TEXT));
        if !m.artist.is_empty() {
            rows.push((fit(&m.artist, room), DIM));
        }
    }
    let lyric_row = m.lyric.is_some() && !m.title.is_empty();
    let bar = m.progress.is_some();
    let h = 8.0 + rows.len() as f32 * 10.0 + if lyric_row { 12.0 } else { 0.0 } + if bar { 14.0 } else { 0.0 };
    rounded(out, x, top, W, h, 6.0, PLATE);
    let mut y = top + 6.0;
    for (i, (line, colour)) in rows.iter().enumerate() {
        if i == 0 {
            out.push(fill(x + 8.0, y + 2.0, 3.0, 3.0, 0xFF00_0000 | status_colour));
            text(out, line, x + 14.0, y, *colour, false);
        } else {
            text(out, line, x + 8.0, y, *colour, false);
        }
        y += 10.0;
    }
    if let (true, Some((sung, word, ahead))) = (lyric_row, &m.lyric) {
        y += 2.0;
        // The three parts side by side, each where the one before ended
        let mut lx = x + 8.0;
        let end = x + W - 8.0;
        for (part, colour) in [(sung, TEXT), (word, accent), (ahead, 0x8E8E96)] {
            if part.is_empty() || lx >= end {
                continue;
            }
            let shown = fit(part, end - lx);
            text(out, &shown, lx, y, colour, false);
            lx += render::text_width(&shown);
        }
        y += 10.0;
    }
    if let Some(progress) = m.progress {
        y += 3.0;
        let time_w = render::text_width(&m.time);
        let bar_w = W - 16.0 - time_w - 6.0;
        out.push(fill(x + 8.0, y + 3.0, bar_w, 2.0, 0x40FF_FFFF));
        out.push(fill(x + 8.0, y + 3.0, (bar_w * progress as f32 / 1000.0).round(), 2.0, 0xFF00_0000 | accent));
        text(out, &m.time, x + W - 8.0 - time_w, y, DIM, false);
    }
}

/// A key: a rounded box, lit while held, with its label centred.
fn key_box(out: &mut Vec<Shape>, [x, y, w, h]: [f32; 4], held: bool, label: &str) {
    let (bg, ink) = if held { (HELD, INK_HELD) } else { (PLATE, TEXT) };
    // Rounded by a pixel or two, as the Java keystrokes are
    out.push(fill(x + 2.0, y, w - 4.0, h, bg));
    out.push(fill(x + 1.0, y + 1.0, 1.0, h - 2.0, bg));
    out.push(fill(x + w - 2.0, y + 1.0, 1.0, h - 2.0, bg));
    out.push(fill(x, y + 2.0, 1.0, h - 4.0, bg));
    out.push(fill(x + w - 1.0, y + 2.0, 1.0, h - 4.0, bg));
    if !label.is_empty() {
        let tw = render::text_width(label);
        text(out, label, x + (w - tw) / 2.0, y + (h - LINE) / 2.0, ink, false);
    }
}

fn keystrokes(out: &mut Vec<Shape>, x: f32, y: f32, moves: &[(bool, String); 4], jump: bool, attack: bool, use_: bool) {
    let step = KEY + KEY_GAP;
    let w = 3.0 * KEY + 2.0 * KEY_GAP;
    let [forward, left, back, right] = moves;
    key_box(out, [x + step, y, KEY, KEY], forward.0, &forward.1);
    for (i, key) in [left, back, right].into_iter().enumerate() {
        key_box(out, [x + i as f32 * step, y + step, KEY, KEY], key.0, &key.1);
    }
    let half = (w - KEY_GAP) / 2.0;
    key_box(out, [x, y + 2.0 * step, half, KEY], attack, "LMB");
    key_box(out, [x + half + KEY_GAP, y + 2.0 * step, half, KEY], use_, "RMB");
    let sy = y + 3.0 * step;
    key_box(out, [x, sy, w, 12.0], jump, "");
    out.push(fill(x + w / 2.0 - 12.0, sy + 5.0, 24.0, 2.0, 0xFF00_0000 | if jump { INK_HELD } else { TEXT }));
}

/// The compass strip, centred at the top: directions slide past a marker.
fn compass(out: &mut Vec<Shape>, width: f32, yaw: f32) {
    const W: f32 = 180.0;
    const HALF_DEGREES: f32 = 90.0;
    let x0 = (width - W) / 2.0;
    let y = MARGIN;
    pill(out, x0, y, W, 18.0, PLATE);
    for (name, off) in format::compass_marks(yaw, HALF_DEGREES - 8.0) {
        let cx = x0 + W / 2.0 + off / HALF_DEGREES * (W / 2.0 - 10.0);
        let tw = render::text_width(name);
        let color = if off.abs() < 12.0 { TEXT } else { DIM };
        text(out, name, cx - tw / 2.0, y + 5.0, color, false);
    }
    out.push(fill(x0 + W / 2.0 - 0.5, y + 15.0, 1.0, 3.0, 0xFFF3_F3F6));
}

impl Mod for SpaceClient {
    fn init() -> Result<(Self, Registration), String> {
        let sc = SpaceClient::load();
        input::register_key("zoom", "Zoom", "key.keyboard.c")?;
        sc.page();
        Ok((sc, Registration::new().on_tick().on_frame().command("sc").command("music")))
    }

    fn tick(&mut self, tick: u64) {
        if self.is_on(Part::Music) && tick % 2 == 0 {
            self.now_playing = stored("nowplaying").and_then(|t| format::now_playing(&t));
        }
        let player = game::get_player();
        match &player {
            Some(p) => {
                self.session_ticks += 1;
                if let Some((x, z)) = self.last_pos {
                    let (dx, dz) = (p.x - x, p.z - z);
                    let now = (dx * dx + dz * dz).sqrt() * 20.0;
                    // Smoothed a little, so walking reads steady
                    self.speed = self.speed * 0.6 + now * 0.4;
                    if self.speed < 0.05 {
                        self.speed = 0.0;
                    }
                }
                self.last_pos = Some((p.x, p.z));
            }
            None => {
                self.last_pos = None;
                self.session_ticks = 0;
            }
        }
        self.player = player;
        self.day_time = game::get_clock().map(|c| c.day_time);
        self.looking_at = if self.is_on(Part::LookingAt) {
            game::raycast(5.0).map(|hit| format::block_name(&hit.block))
        } else {
            None
        };
    }

    fn frame(&mut self, _seconds: f32) {
        self.handle_input();
        self.draw();
    }

    fn command(&mut self, name: &str, args: &str) -> Result<Option<String>, String> {
        if name == "music" {
            return self.music_command(args.trim());
        }
        self.screen();
        Ok(None)
    }

    fn ui_select(&mut self, id: u32, row: u32) {
        match id {
            MUSIC_PREV => return self.send_music("prev"),
            MUSIC_TOGGLE => return self.send_music("toggle"),
            MUSIC_NEXT => return self.send_music("next"),
            LIST => {}
            _ => return,
        }
        self.toggle(row as usize);
        self.page();
        // The /sc screen gets the new labels too, when it is the one showing
        if self.screen_open {
            self.screen();
        }
    }

    fn ui_closed(&mut self) {
        self.screen_open = false;
        self.music_screen = false;
    }

    fn ui_settings_opened(&mut self) {
        self.page();
    }
}

horizon_mod_sdk::export_mod!(SpaceClient);
