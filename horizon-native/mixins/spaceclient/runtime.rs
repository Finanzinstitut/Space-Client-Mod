// Space Client for Horizon, as a native mod: the bundle adds this file to the
// game as `game::spaceclient`, and it is also this crate's library, for its
// tests. Nothing in here touches the game's own types; the small helpers next
// to it (one per hook) read the game and hand plain values to these functions.
//
// Settings live where the WebAssembly Space Client kept them,
// `horizon-mods/.horizon-mod-data/spaceclient/`, and the launcher writes the
// player's Java setup into the same folder: `settings` (which HUD parts are
// on), `itemsizes` (Item Shower) and `nowplaying` (the music bridge).
use std::cell::RefCell;
use std::path::Path;
use std::time::{Duration, Instant, SystemTime};

pub const ID: &str = "spaceclient";
pub const VERSION: &str = "2.0.0";
pub const DESCRIPTION: &str =
    "Space Client's HUD, keystrokes, zoom, Now Playing and Item Shower. Settings come from the Space Client launcher.";
pub const ZOOM_KEY: &str = "zoom";
pub const DEFAULT_ZOOM_KEY: &str = "key.keyboard.c";

// ---------------------------------------------------------------------------
// Item Shower

/// One item's sizes, as the Java client's ItemSizes keeps them.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Sizes {
    /// Every item drawn in the interface: the hotbar, inventories.
    pub gui: f32,
    /// Held in first person.
    pub hand: f32,
    /// Lying on the ground.
    pub ground: f32,
}

impl Sizes {
    pub const ONE: Sizes = Sizes { gui: 1.0, hand: 1.0, ground: 1.0 };
    fn times(self, other: Sizes) -> Sizes {
        Sizes {
            gui: clamp(self.gui * other.gui),
            hand: clamp(self.hand * other.hand),
            ground: clamp(self.ground * other.ground),
        }
    }
}

fn clamp(v: f32) -> f32 {
    if v.is_finite() { v.clamp(0.25, 4.0) } else { 1.0 }
}

/// The launcher's `itemsizes` file: `minecraft:totem_of_undying=1.5,1,2`
/// (interface, hand, ground) per line, `*=...` for every item.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct ItemSizes {
    global: Option<Sizes>,
    items: Vec<(String, Sizes)>,
}

impl ItemSizes {
    pub fn parse(text: &str) -> Self {
        let mut out = ItemSizes::default();
        for line in text.lines() {
            let Some((key, value)) = line.trim().split_once('=') else { continue };
            let parts: Vec<f32> = value.split(',').filter_map(|v| v.trim().parse().ok()).collect();
            let [gui, hand, ground] = parts[..] else { continue };
            let sizes = Sizes { gui: clamp(gui), hand: clamp(hand), ground: clamp(ground) };
            match key.trim() {
                "*" => out.global = Some(sizes),
                item => out.items.push((full_id(item), sizes)),
            }
        }
        out
    }

    /// An item's own sizes times the ones for every item, as ItemSizes.effective.
    pub fn of(&self, item: &str) -> Sizes {
        let id = full_id(item);
        let own = self.items.iter().find(|(k, _)| *k == id).map_or(Sizes::ONE, |e| e.1);
        own.times(self.global.unwrap_or(Sizes::ONE))
    }

    pub fn is_empty(&self) -> bool {
        self.global.is_none_or(|g| g == Sizes::ONE) && self.items.iter().all(|e| e.1 == Sizes::ONE)
    }
}

fn full_id(item: &str) -> String {
    if item.contains(':') { item.to_owned() } else { format!("minecraft:{item}") }
}

/// The file, read again at most every two seconds and only when it changed.
struct Loaded {
    sizes: ItemSizes,
    checked: Option<Instant>,
    stamp: Option<SystemTime>,
}

thread_local! {
    static SIZES: RefCell<Loaded> = const { RefCell::new(Loaded { sizes: ItemSizes { global: None, items: Vec::new() }, checked: None, stamp: None }) };
}

/// The sizes of `item`, from `dir`'s `itemsizes`.
pub fn sizes(dir: &Path, item: &str) -> Sizes {
    SIZES.with_borrow_mut(|l| {
        let due = l.checked.is_none_or(|t| t.elapsed() > Duration::from_secs(2));
        if due {
            l.checked = Some(Instant::now());
            let file = dir.join("itemsizes");
            let stamp = std::fs::metadata(&file).and_then(|m| m.modified()).ok();
            if stamp != l.stamp {
                l.stamp = stamp;
                l.sizes = std::fs::read_to_string(&file).map(|t| ItemSizes::parse(&t)).unwrap_or_default();
            }
        }
        if l.sizes.is_empty() { Sizes::ONE } else { l.sizes.of(item) }
    })
}

// ---------------------------------------------------------------------------
// Settings: the parts of the HUD, as the WebAssembly mod kept them.

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Part {
    Fps,
    Cps,
    Coordinates,
    Direction,
    Speed,
    Session,
    Compass,
    Keystrokes,
    Zoom,
    Music,
    ItemShower,
}

pub const PARTS: [(Part, &str, &str, bool); 11] = [
    (Part::Fps, "fps", "FPS", true),
    (Part::Cps, "cps", "CPS", true),
    (Part::Coordinates, "coordinates", "Coordinates", true),
    (Part::Direction, "direction", "Direction", false),
    (Part::Speed, "speed", "Speedometer", false),
    (Part::Session, "session", "Session Time", false),
    (Part::Compass, "compass", "Compass", false),
    (Part::Keystrokes, "keystrokes", "Keystrokes", true),
    (Part::Zoom, "zoom", "Zoom", true),
    (Part::Music, "music", "Now Playing", true),
    (Part::ItemShower, "itemshower", "Item Shower", true),
];

#[derive(Clone, Debug, PartialEq)]
pub struct Settings {
    pub on: [bool; PARTS.len()],
    /// The readouts on the right instead of the left.
    pub right: bool,
    pub plates: bool,
}

impl Settings {
    pub fn read(dir: &Path) -> Self {
        let get = |key: &str| std::fs::read_to_string(dir.join(key)).ok();
        Self::parse(get("settings").as_deref(), get("right").as_deref(), get("plates").as_deref())
    }

    pub fn parse(settings: Option<&str>, right: Option<&str>, plates: Option<&str>) -> Self {
        let mut on = PARTS.map(|p| p.3);
        for line in settings.unwrap_or_default().lines() {
            let Some((key, value)) = line.split_once('=') else { continue };
            if let Some(i) = PARTS.iter().position(|p| p.1 == key.trim()) {
                on[i] = value.trim() == "true";
            }
        }
        let flag = |v: Option<&str>, default: bool| v.map_or(default, |v| v.trim() == "true");
        Settings { on, right: flag(right, false), plates: flag(plates, true) }
    }

    pub fn is_on(&self, part: Part) -> bool {
        PARTS.iter().position(|p| p.0 == part).is_some_and(|i| self.on[i])
    }

    /// The settings page's buttons.
    pub fn labels(&self) -> Vec<String> {
        let state = |b: bool| if b { "ON" } else { "OFF" };
        let mut rows: Vec<String> =
            PARTS.iter().enumerate().map(|(i, p)| format!("{}: {}", p.2, state(self.on[i]))).collect();
        rows.push(format!("Readouts: {}", if self.right { "Right" } else { "Left" }));
        rows.push(format!("Background: {}", state(self.plates)));
        rows
    }

    pub fn click(&mut self, button: usize) {
        if button < PARTS.len() {
            self.on[button] = !self.on[button];
        } else if button == PARTS.len() {
            self.right = !self.right;
        } else if button == PARTS.len() + 1 {
            self.plates = !self.plates;
        }
    }

    pub fn save(&self, dir: &Path) {
        let text: String = PARTS.iter().zip(self.on).map(|(p, on)| format!("{}={}\n", p.1, on)).collect();
        let _ = std::fs::create_dir_all(dir);
        let _ = std::fs::write(dir.join("settings"), text);
        let _ = std::fs::write(dir.join("right"), self.right.to_string());
        let _ = std::fs::write(dir.join("plates"), self.plates.to_string());
    }
}

struct Cached {
    settings: Option<Settings>,
    read: Option<Instant>,
}

thread_local! {
    static SETTINGS: RefCell<Cached> = const { RefCell::new(Cached { settings: None, read: None }) };
}

/// The settings, read again every two seconds (the launcher may write them).
pub fn settings(dir: &Path) -> Settings {
    SETTINGS.with_borrow_mut(|c| {
        if c.settings.is_none() || c.read.is_none_or(|t| t.elapsed() > Duration::from_secs(2)) {
            c.settings = Some(Settings::read(dir));
            c.read = Some(Instant::now());
        }
        c.settings.clone().unwrap_or_else(|| Settings::parse(None, None, None))
    })
}

pub fn set_settings(dir: &Path, settings: Settings) {
    settings.save(dir);
    SETTINGS.with_borrow_mut(|c| {
        c.settings = Some(settings);
        c.read = Some(Instant::now());
    });
}

/// Whether Item Shower is on: a quick answer for the item hooks, which run
/// for every item drawn.
pub fn item_shower_on(dir: &Path) -> bool {
    settings(dir).is_on(Part::ItemShower)
}

/// The readouts' text, as the Java modules write it (the WebAssembly mod's
/// format.rs).
pub mod format {
    /// `12, 64, -3`: whole blocks, rounded the way String.format("%.0f") rounds.
    pub fn coordinates(x: f64, y: f64, z: f64) -> String {
        format!("{:.0}, {:.0}, {:.0}", x, y, z)
    }

    /// The eight compass names, from south round through west: Minecraft's yaw
    /// 0 faces south (+Z) and grows towards west.
    const NAMES: [&str; 8] = ["South", "South West", "West", "North West", "North", "North East", "East", "South East"];
    const AXES: [&str; 8] = ["+Z", "-X +Z", "-X", "-X -Z", "-Z", "+X -Z", "+X", "+X +Z"];
    const SHORT: [&str; 8] = ["S", "SW", "W", "NW", "N", "NE", "E", "SE"];

    /// Which of the eight directions a yaw faces.
    pub fn octant(yaw: f32) -> usize {
        let wrapped = yaw.rem_euclid(360.0);
        ((wrapped + 22.5) / 45.0).floor() as usize % 8
    }

    /// `North  -Z`, as the Direction module shows it with its axis on.
    pub fn direction(yaw: f32) -> String {
        let at = octant(yaw);
        format!("{}  {}", NAMES[at], AXES[at])
    }

    /// The compass strip's marks: each direction's short name and how far it is
    /// from straight ahead, in degrees (negative is to the left). Only those
    /// within `half` degrees are returned.
    pub fn compass_marks(yaw: f32, half: f32) -> Vec<(&'static str, f32)> {
        let mut out = Vec::new();
        for (i, name) in SHORT.iter().enumerate() {
            let at = i as f32 * 45.0;
            let off = (at - yaw + 180.0).rem_euclid(360.0) - 180.0;
            if off.abs() <= half {
                out.push((*name, off));
            }
        }
        out
    }

    /// `4.3 m/s`.
    pub fn speed(metres_per_second: f64) -> String {
        format!("{:.1} m/s", metres_per_second)
    }

    /// `1:02:09` from ticks spent in the world.
    pub fn session(ticks: u64) -> String {
        let seconds = ticks / 20;
        format!("{}:{:02}:{:02}", seconds / 3600, seconds / 60 % 60, seconds % 60)
    }

    /// The insets of a pill's rows from the top: row `i` of the top `r` rows is
    /// drawn `insets[i]` pixels in from each side, mirrored at the bottom. The
    /// same circle the Java client's plates are cut from.
    pub fn pill_insets(radius: u32) -> Vec<u32> {
        let r = radius as f64;
        (0..radius)
            .map(|row| {
                let dy = r - row as f64 - 0.5;
                let dx = (r * r - dy * dy).max(0.0).sqrt();
                (r - dx).round() as u32
            })
            .collect()
    }

    /// What the launcher writes for Now Playing (`nowplaying` in the mod's
    /// storage, see the launcher's music.rs): one `key=value` per line.
    #[derive(Clone, Debug, Default, PartialEq)]
    pub struct NowPlaying {
        /// playing, paused, idle or unsupported
        pub state: String,
        pub source: String,
        pub title: String,
        pub artist: String,
        pub position: f64,
        pub duration: f64,
        pub line: Option<String>,
        /// The word being sung, as char offsets into `line`.
        pub word: Option<(usize, usize)>,
    }

    pub fn now_playing(text: &str) -> Option<NowPlaying> {
        let mut out = NowPlaying { position: -1.0, duration: -1.0, ..Default::default() };
        let mut versioned = false;
        for line in text.lines() {
            let Some((key, value)) = line.split_once('=') else { continue };
            match key {
                "v" => versioned = value == "1",
                "state" => out.state = value.into(),
                "source" => out.source = value.into(),
                "title" => out.title = value.into(),
                "artist" => out.artist = value.into(),
                "position" => out.position = value.parse().unwrap_or(-1.0),
                "duration" => out.duration = value.parse().unwrap_or(-1.0),
                "line" => out.line = Some(value.into()).filter(|l: &String| !l.is_empty()),
                "word" => {
                    out.word = value
                        .split_once(',')
                        .and_then(|(a, b)| Some((a.parse().ok()?, b.parse().ok()?)))
                }
                _ => {}
            }
        }
        versioned.then_some(out)
    }

    /// `3:07` from seconds; `--:--` when unknown.
    pub fn clock(seconds: f64) -> String {
        if seconds < 0.0 || !seconds.is_finite() {
            return "--:--".into();
        }
        let s = seconds as u64;
        format!("{}:{:02}", s / 60, s % 60)
    }

    /// A lyric line in three parts: sung, the word being sung, still to come.
    pub fn split_word(line: &str, word: Option<(usize, usize)>) -> (String, String, String) {
        let chars: Vec<char> = line.chars().collect();
        let Some((start, end)) = word else { return (line.into(), String::new(), String::new()) };
        let start = start.min(chars.len());
        let end = end.clamp(start, chars.len());
        (
            chars[..start].iter().collect(),
            chars[start..end].iter().collect(),
            chars[end..].iter().collect(),
        )
    }

    #[cfg(test)]
    mod tests {
        use super::*;

        #[test]
        fn the_launchers_file() {
            let np = now_playing("v=1\nstate=playing\nsource=Spotify\ntitle=T\nartist=A\nposition=12.35\nduration=200.00\nline=Über den Wolken\nword=5,8\n").unwrap();
            assert_eq!(np.state, "playing");
            assert_eq!(np.position, 12.35);
            assert_eq!(np.word, Some((5, 8)));
            assert_eq!(split_word(np.line.as_deref().unwrap(), np.word), ("Über ".into(), "den".into(), " Wolken".into()));
            assert_eq!(clock(187.9), "3:07");
            assert!(now_playing("state=playing\n").is_none());
        }

        #[test]
        fn coordinates_round_like_java() {
            assert_eq!(coordinates(12.4, 64.0, -3.6), "12, 64, -4");
        }

        #[test]
        fn directions() {
            assert_eq!(direction(0.0), "South  +Z");
            assert_eq!(direction(90.0), "West  -X");
            assert_eq!(direction(180.0), "North  -Z");
            assert_eq!(direction(-90.0), "East  +X");
            assert_eq!(direction(-170.0), "North  -Z");
            assert_eq!(direction(45.0), "South West  -X +Z");
        }

        #[test]
        fn compass_shows_what_is_ahead() {
            let marks = compass_marks(180.0, 60.0);
            assert!(marks.contains(&("N", 0.0)));
            assert!(marks.contains(&("NE", 45.0)));
            assert!(marks.contains(&("NW", -45.0)));
            assert_eq!(marks.len(), 3);
        }

        #[test]
        fn session_and_speed() {
            assert_eq!(session(20 * 3729), "1:02:09");
            assert_eq!(speed(4.317), "4.3 m/s");
        }

        #[test]
        fn pills_are_round() {
            assert_eq!(pill_insets(0), Vec::<u32>::new());
            let rows = pill_insets(9);
            assert_eq!(rows.len(), 9);
            assert!(rows[0] > rows[8]);
            assert_eq!(rows[8], 0);
        }
    }
}

// ---------------------------------------------------------------------------
// The HUD: what the hooks read each tick and frame, and the shapes drawn.

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

/// What the game draws: a filled box (ARGB) or a line of text (RGB),
/// positions relative to the box `place` gave.
#[derive(Clone, Debug, PartialEq)]
pub enum Shape {
    Fill([f32; 4], u32),
    Text(String, [f32; 2], u32, bool),
}

#[derive(Default)]
struct State {
    session_ticks: u64,
    speed: f64,
    last_pos: Option<(f64, f64)>,
    zoom_held: Option<f32>,
    now_playing: Option<format::NowPlaying>,
    read_music: Option<Instant>,
}

thread_local! {
    static STATE: RefCell<State> = RefCell::new(State::default());
}

/// Once a tick in the window's game: the player's position (none outside a
/// world), for the speedometer and the session clock.
pub fn tick(position: Option<(f64, f64)>) {
    STATE.with_borrow_mut(|s| match position {
        Some((x, z)) => {
            s.session_ticks += 1;
            if let Some((lx, lz)) = s.last_pos {
                let (dx, dz) = (x - lx, z - lz);
                let now = (dx * dx + dz * dz).sqrt() * 20.0;
                // Smoothed a little, so walking reads steady
                s.speed = s.speed * 0.6 + now * 0.4;
                if s.speed < 0.05 {
                    s.speed = 0.0;
                }
            }
            s.last_pos = Some((x, z));
        }
        None => {
            s.last_pos = None;
            s.session_ticks = 0;
            s.speed = 0.0;
        }
    });
}

/// What the launcher last wrote for Now Playing, read again every 120 ms.
pub fn now_playing(dir: &Path) -> Option<format::NowPlaying> {
    STATE.with_borrow_mut(|s| {
        if s.read_music.is_none_or(|t| t.elapsed() > Duration::from_millis(120)) {
            s.read_music = Some(Instant::now());
            s.now_playing = std::fs::read_to_string(dir.join("nowplaying")).ok().and_then(|t| format::now_playing(&t));
        }
        s.now_playing.clone()
    })
}

/// Hands a press (toggle, next, prev) to the launcher's music bridge.
pub fn send_music(dir: &Path, what: &str) {
    let _ = std::fs::create_dir_all(dir);
    let _ = std::fs::write(dir.join("music-command"), what);
}

/// The chat commands this mod runs, without their slash.
pub const COMMANDS: [&str; 3] = ["sc", "spaceclient", "music"];

/// Whether `command` (as typed, without its slash) is one of ours.
pub fn is_ours(command: &str) -> bool {
    let name = command.trim().split(' ').next().unwrap_or_default();
    COMMANDS.contains(&name)
}

/// `/music` and `/sc`: what to say in chat.
pub fn command(dir: &Path, command: &str) -> Option<String> {
    let (name, args) = command.trim().split_once(' ').unwrap_or((command.trim(), ""));
    match name {
        "sc" | "spaceclient" => Some(
            "Space Client: Options > Mods > Space Client > Settings switches the HUD parts. Item Shower and Saturation are set in the Java client and come over with the launcher."
                .into(),
        ),
        "music" => {
            let np = now_playing(dir);
            let what = match args.trim() {
                "" => {
                    return Some(match &np {
                        Some(np) if !np.title.is_empty() => {
                            format!("Now Playing: {} - {}  (/music pause, next, prev)", np.title, np.artist)
                        }
                        Some(_) => "Nothing is playing.".into(),
                        None => "Now Playing needs Horizon started from the Space Client launcher.".into(),
                    });
                }
                "pause" | "play" | "toggle" => "toggle",
                "next" | "skip" => "next",
                "prev" | "previous" | "back" => "prev",
                _ => return Some("Use /music, or /music pause, next or prev".into()),
            };
            if np.is_none() {
                return Some("Now Playing needs Horizon started from the Space Client launcher.".into());
            }
            send_music(dir, what);
            None
        }
        _ => None,
    }
}

/// The zoom key went down (only while playing) or up.
pub fn zoom_key(playing: bool, pressed: bool) {
    if pressed && !playing {
        return;
    }
    STATE.with_borrow_mut(|s| s.zoom_held = pressed.then_some(4.0));
}

/// Wheel steps: true when the zoom key is held and they zoom.
pub fn zoom_wheel(steps: f32) -> bool {
    STATE.with_borrow_mut(|s| {
        let Some(z) = &mut s.zoom_held else { return false };
        // Each notch multiplies, so every notch feels the same
        *z = (*z * 1.25f32.powf(steps)).clamp(1.5, 10.0);
        true
    })
}

/// The zoom in effect: (factor, smooth, turning scale).
pub fn zoom() -> Option<(f32, bool, f32)> {
    STATE.with_borrow(|s| s.zoom_held.map(|f| (f, true, (1.0 / f).max(0.1))))
}

/// Everything the readouts show, read from the game by the hud hook.
pub struct Inputs {
    pub fps: u32,
    pub clicks: (u32, u32),
    /// x, y, z, yaw; none outside a world.
    pub player: Option<(f64, f64, f64, f32)>,
    /// forward, left, back, right: held and label; then jump, attack, use.
    pub keys: [(bool, String); 4],
    pub jump: bool,
    pub attack: bool,
    pub use_: bool,
}

/// The readout lines, in the order the column shows them.
pub fn lines(settings: &Settings, inputs: &Inputs) -> Vec<String> {
    let mut out = Vec::new();
    if settings.is_on(Part::Fps) {
        out.push(format!("{} FPS", inputs.fps));
    }
    if settings.is_on(Part::Cps) {
        out.push(format!("{} | {} CPS", inputs.clicks.0, inputs.clicks.1));
    }
    let Some((x, y, z, yaw)) = inputs.player else { return out };
    if settings.is_on(Part::Coordinates) {
        out.push(format::coordinates(x, y, z));
    }
    if settings.is_on(Part::Direction) {
        out.push(format::direction(yaw));
    }
    let (speed, ticks) = STATE.with_borrow(|s| (s.speed, s.session_ticks));
    if settings.is_on(Part::Speed) {
        out.push(format::speed(speed));
    }
    if settings.is_on(Part::Session) {
        out.push(format::session(ticks));
    }
    out
}

/// The readout column: its size, then its shapes from its top left.
pub fn column(settings: &Settings, lines: &[String], width: &dyn Fn(&str) -> f32) -> ((f32, f32), Vec<Shape>) {
    let plate_h = LINE + PAD * 2.0;
    let widths: Vec<f32> = lines.iter().map(|l| width(l) + PAD * 2.0).collect();
    let w = widths.iter().copied().fold(0.0, f32::max);
    let mut out = Vec::new();
    let mut y = 0.0;
    for (line, lw) in lines.iter().zip(&widths) {
        let x = if settings.right { w - lw } else { 0.0 };
        if settings.plates {
            pill(&mut out, x, y, *lw, plate_h, PLATE);
        }
        out.push(Shape::Text(line.clone(), [(x + PAD).round(), (y + PAD).round()], TEXT, !settings.plates));
        y += plate_h + GAP;
    }
    ((w, (y - GAP).max(0.0)), out)
}

pub const KEYS_SIZE: (f32, f32) = (3.0 * KEY + 2.0 * KEY_GAP, 3.0 * (KEY + KEY_GAP) + 12.0);

/// The keystrokes from their top left.
pub fn keystrokes(inputs: &Inputs, width: &dyn Fn(&str) -> f32) -> Vec<Shape> {
    let mut out = Vec::new();
    let step = KEY + KEY_GAP;
    let w = KEYS_SIZE.0;
    let [forward, left, back, right] = &inputs.keys;
    key_box(&mut out, width, [step, 0.0, KEY, KEY], forward.0, &forward.1);
    for (i, key) in [left, back, right].into_iter().enumerate() {
        key_box(&mut out, width, [i as f32 * step, step, KEY, KEY], key.0, &key.1);
    }
    let half = (w - KEY_GAP) / 2.0;
    key_box(&mut out, width, [0.0, 2.0 * step, half, KEY], inputs.attack, "LMB");
    key_box(&mut out, width, [half + KEY_GAP, 2.0 * step, half, KEY], inputs.use_, "RMB");
    let sy = 3.0 * step;
    key_box(&mut out, width, [0.0, sy, w, 12.0], inputs.jump, "");
    out.push(Shape::Fill([w / 2.0 - 12.0, sy + 5.0, 24.0, 2.0], 0xFF00_0000 | if inputs.jump { INK_HELD } else { TEXT }));
    out
}

/// The compass strip and the Now Playing card, centred at the top of a
/// screen `screen_w` GUI pixels wide: absolute positions.
pub fn top_centre(settings: &Settings, yaw: Option<f32>, music: Option<&format::NowPlaying>, screen_w: f32, width: &dyn Fn(&str) -> f32) -> Vec<Shape> {
    let mut out = Vec::new();
    let compass_on = settings.is_on(Part::Compass) && yaw.is_some();
    if let (true, Some(yaw)) = (compass_on, yaw) {
        compass(&mut out, screen_w, yaw, width);
    }
    if let (true, Some(np)) = (settings.is_on(Part::Music), music) {
        let top = if compass_on { MARGIN + 22.0 } else { MARGIN };
        music_card(&mut out, screen_w, top, np, width);
    }
    out
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
        out.push(Shape::Fill([x, y + r, w, h - 2.0 * r], color));
    }
    for (row, inset) in insets.iter().enumerate() {
        let inset = *inset as f32;
        let top = y + row as f32;
        let bottom = y + h - 1.0 - row as f32;
        out.push(Shape::Fill([x + inset, top, w - 2.0 * inset, 1.0], color));
        out.push(Shape::Fill([x + inset, bottom, w - 2.0 * inset, 1.0], color));
    }
}

fn text(out: &mut Vec<Shape>, s: &str, x: f32, y: f32, color: u32) {
    out.push(Shape::Text(s.into(), [x.round(), y.round()], color, false));
}

/// Text cut to fit `room` GUI pixels, with an ellipsis when it was cut.
fn fit(s: &str, room: f32, width: &dyn Fn(&str) -> f32) -> String {
    if width(s) <= room {
        return s.into();
    }
    let mut chars: Vec<char> = s.chars().collect();
    while !chars.is_empty() {
        chars.pop();
        let cut: String = chars.iter().collect::<String>() + "...";
        if width(&cut) <= room {
            return cut;
        }
    }
    String::new()
}

/// The Now Playing card: state and player, title, artist, the lyric line
/// with the word being sung, and the time bar.
fn music_card(out: &mut Vec<Shape>, screen_w: f32, top: f32, m: &format::NowPlaying, width: &dyn Fn(&str) -> f32) {
    const W: f32 = 200.0;
    let accent = if m.source.to_lowercase().contains("amazon") { 0x4CC8F4 } else { 0x1DB954 };
    let x = ((screen_w - W) / 2.0).round();
    let room = W - 16.0;
    let mut rows: Vec<(String, u32)> = Vec::new();
    let status = match m.state.as_str() {
        "playing" => format!("PLAYING  \u{00B7}  {}", m.source.to_uppercase()),
        "paused" => format!("PAUSED  \u{00B7}  {}", m.source.to_uppercase()),
        "unsupported" => "NOW PLAYING  \u{00B7}  NO PLAYER FOUND".into(),
        _ => "NOTHING PLAYING".into(),
    };
    let status_colour = if m.state == "playing" { accent } else { DIM };
    rows.push((fit(&status, room, width), status_colour));
    if !m.title.is_empty() {
        rows.push((fit(&m.title, room, width), TEXT));
        if !m.artist.is_empty() {
            rows.push((fit(&m.artist, room, width), DIM));
        }
    }
    let lyric = m.line.as_deref().filter(|_| !m.title.is_empty()).map(|l| format::split_word(l, m.word));
    let progress = (m.duration > 0.0 && m.position >= 0.0).then(|| (m.position / m.duration).clamp(0.0, 1.0) as f32);
    let h = 8.0 + rows.len() as f32 * 10.0 + if lyric.is_some() { 12.0 } else { 0.0 } + if progress.is_some() { 14.0 } else { 0.0 };
    rounded(out, x, top, W, h, 6.0, PLATE);
    let mut y = top + 6.0;
    for (i, (line, colour)) in rows.iter().enumerate() {
        if i == 0 {
            out.push(Shape::Fill([x + 8.0, y + 2.0, 3.0, 3.0], 0xFF00_0000 | status_colour));
            text(out, line, x + 14.0, y, *colour);
        } else {
            text(out, line, x + 8.0, y, *colour);
        }
        y += 10.0;
    }
    if let Some((sung, word, ahead)) = &lyric {
        y += 2.0;
        // The three parts side by side, each where the one before ended
        let mut lx = x + 8.0;
        let end = x + W - 8.0;
        for (part, colour) in [(sung, TEXT), (word, accent), (ahead, 0x8E8E96)] {
            if part.is_empty() || lx >= end {
                continue;
            }
            let shown = fit(part, end - lx, width);
            text(out, &shown, lx, y, colour);
            lx += width(&shown);
        }
        y += 10.0;
    }
    if let Some(progress) = progress {
        y += 3.0;
        let time = format!("{} / {}", format::clock(m.position), format::clock(m.duration));
        let time_w = width(&time);
        let bar_w = W - 16.0 - time_w - 6.0;
        out.push(Shape::Fill([x + 8.0, y + 3.0, bar_w, 2.0], 0x40FF_FFFF));
        out.push(Shape::Fill([x + 8.0, y + 3.0, (bar_w * progress).round(), 2.0], 0xFF00_0000 | accent));
        text(out, &time, x + W - 8.0 - time_w, y, DIM);
    }
}

/// A key: a rounded box, lit while held, with its label centred.
fn key_box(out: &mut Vec<Shape>, width: &dyn Fn(&str) -> f32, [x, y, w, h]: [f32; 4], held: bool, label: &str) {
    let (bg, ink) = if held { (HELD, INK_HELD) } else { (PLATE, TEXT) };
    out.push(Shape::Fill([x + 2.0, y, w - 4.0, h], bg));
    out.push(Shape::Fill([x + 1.0, y + 1.0, 1.0, h - 2.0], bg));
    out.push(Shape::Fill([x + w - 2.0, y + 1.0, 1.0, h - 2.0], bg));
    out.push(Shape::Fill([x, y + 2.0, 1.0, h - 4.0], bg));
    out.push(Shape::Fill([x + w - 1.0, y + 2.0, 1.0, h - 4.0], bg));
    if !label.is_empty() {
        let tw = width(label);
        text(out, label, x + (w - tw) / 2.0, y + (h - LINE) / 2.0, ink);
    }
}

/// The compass strip, centred at the top: directions slide past a marker.
fn compass(out: &mut Vec<Shape>, screen_w: f32, yaw: f32, width: &dyn Fn(&str) -> f32) {
    const W: f32 = 180.0;
    const HALF_DEGREES: f32 = 90.0;
    let x0 = ((screen_w - W) / 2.0).round();
    let y = MARGIN;
    pill(out, x0, y, W, 18.0, PLATE);
    for (name, off) in format::compass_marks(yaw, HALF_DEGREES - 8.0) {
        let cx = x0 + W / 2.0 + off / HALF_DEGREES * (W / 2.0 - 10.0);
        let tw = width(name);
        let color = if off.abs() < 12.0 { TEXT } else { DIM };
        text(out, name, cx - tw / 2.0, y + 5.0, color);
    }
    out.push(Shape::Fill([x0 + W / 2.0 - 0.5, y + 15.0, 1.0, 3.0], 0xFFF3_F3F6));
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn item_sizes_multiply_with_the_global_ones() {
        let s = ItemSizes::parse("minecraft:totem_of_undying=2,1,3\n*=0.5,1,2\nbad\nx=1,2\n");
        assert_eq!(s.of("minecraft:totem_of_undying"), Sizes { gui: 1.0, hand: 1.0, ground: 4.0 });
        assert_eq!(s.of("totem_of_undying"), s.of("minecraft:totem_of_undying"));
        assert_eq!(s.of("minecraft:stone"), Sizes { gui: 0.5, hand: 1.0, ground: 2.0 });
        assert!(!s.is_empty());
        assert!(ItemSizes::parse("*=1,1,1\nminecraft:stone=1,1,1").is_empty());
    }

    #[test]
    fn settings_read_the_webassembly_mods_file() {
        let s = Settings::parse(Some("fps=false\ncompass=true\nitemshower=false\n"), Some("true"), None);
        assert!(!s.is_on(Part::Fps));
        assert!(s.is_on(Part::Compass));
        assert!(s.is_on(Part::Cps));
        assert!(!s.is_on(Part::ItemShower));
        assert!(s.right && s.plates);
        let mut t = s.clone();
        t.click(0);
        t.click(PARTS.len() + 1);
        assert!(t.is_on(Part::Fps) && !t.plates);
        assert_eq!(t.labels().len(), PARTS.len() + 2);
    }

    #[test]
    fn hud_lays_out_like_the_webassembly_mod() {
        let width = |s: &str| s.len() as f32 * 6.0;
        let settings = Settings::parse(Some("compass=true\nspeed=true\n"), None, None);
        let inputs = Inputs {
            fps: 144,
            clicks: (3, 1),
            player: Some((12.4, 64.0, -3.6, 180.0)),
            keys: [(true, "W".into()), (false, "A".into()), (false, "S".into()), (false, "D".into())],
            jump: false,
            attack: true,
            use_: false,
        };
        tick(Some((0.0, 0.0)));
        tick(Some((0.2, 0.0)));
        let lines = lines(&settings, &inputs);
        assert_eq!(lines, ["144 FPS", "3 | 1 CPS", "12, 64, -4", "1.6 m/s"]);
        let ((w, h), shapes) = column(&settings, &lines, &width);
        assert_eq!(w, 10.0 * 6.0 + 10.0);
        assert_eq!(h, 4.0 * 18.0 + 3.0 * 3.0);
        assert!(shapes.contains(&Shape::Text("144 FPS".into(), [5.0, 5.0], TEXT, false)));
        assert!(!keystrokes(&inputs, &width).is_empty());
        let np = format::now_playing("v=1\nstate=playing\nsource=Spotify\ntitle=T\nartist=A\nposition=1\nduration=2\n").unwrap();
        let top = top_centre(&settings, Some(180.0), Some(&np), 400.0, &width);
        assert!(top.iter().any(|s| matches!(s, Shape::Text(t, _, _, _) if t == "N")));
        assert!(top.iter().any(|s| matches!(s, Shape::Text(t, _, _, _) if t == "T")));
        tick(None);
    }

    #[test]
    fn zoom_holds_and_lets_go() {
        zoom_key(false, true);
        assert_eq!(zoom(), None);
        zoom_key(true, true);
        assert_eq!(zoom().map(|z| z.0), Some(4.0));
        assert!(zoom_wheel(1.0));
        assert_eq!(zoom().map(|z| z.0), Some(5.0));
        zoom_key(true, false);
        assert_eq!(zoom(), None);
        assert!(!zoom_wheel(1.0));
    }

    #[test]
    fn music_commands_go_to_the_launcher() {
        let dir = std::env::temp_dir().join(format!("sc-native-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        assert!(command(&dir, "music next").unwrap().contains("launcher"));
        std::fs::create_dir_all(&dir).unwrap();
        std::fs::write(dir.join("nowplaying"), "v=1\nstate=playing\ntitle=Song\nartist=Band\n").unwrap();
        std::thread::sleep(Duration::from_millis(150));
        assert_eq!(command(&dir, "music next"), None);
        assert_eq!(std::fs::read_to_string(dir.join("music-command")).unwrap(), "next");
        assert!(command(&dir, "music").unwrap().contains("Song - Band"));
        assert_eq!(command(&dir, "other"), None);
        let _ = std::fs::remove_dir_all(&dir);
    }
}
