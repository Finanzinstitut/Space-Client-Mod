//! The readouts' text, as the Java modules write it. Plain Rust with no
//! game calls, so it is tested on the host with `cargo test`.

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

/// `Day 3, 14:05`: day-time 0 is 6:00 on day 1, 24000 ticks a day.
pub fn day_time(day_time: u64) -> String {
    let day = day_time / 24000 + 1;
    let of_day = (day_time + 6000) % 24000;
    // The first six hours of a day still belong to the day before
    let day = if day_time % 24000 >= 18000 { day + 1 } else { day };
    let minutes = of_day * 1440 / 24000;
    format!("Day {}, {:02}:{:02}", day, minutes / 60, minutes % 60)
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

/// `20 HP  18 Food`: half hearts as the game counts them.
pub fn health(health: f32, food: u32) -> String {
    format!("{:.0} HP  {} Food", health.max(0.0), food)
}

/// `Oak Log` from `minecraft:oak_log`; a modded block keeps its namespace.
pub fn block_name(id: &str) -> String {
    let (namespace, path) = id.split_once(':').unwrap_or(("minecraft", id));
    let words: Vec<String> = path
        .split('_')
        .filter(|w| !w.is_empty())
        .map(|w| {
            let mut chars = w.chars();
            match chars.next() {
                Some(first) => first.to_uppercase().chain(chars).collect(),
                None => String::new(),
            }
        })
        .collect();
    let name = words.join(" ");
    if namespace == "minecraft" { name } else { format!("{name} ({namespace})") }
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
    fn day_clock() {
        assert_eq!(day_time(0), "Day 1, 06:00");
        assert_eq!(day_time(6000), "Day 1, 12:00");
        assert_eq!(day_time(18000), "Day 2, 00:00");
        assert_eq!(day_time(29999), "Day 2, 11:59");
    }

    #[test]
    fn session_and_speed() {
        assert_eq!(session(20 * 3729), "1:02:09");
        assert_eq!(speed(4.317), "4.3 m/s");
    }

    #[test]
    fn block_names() {
        assert_eq!(block_name("minecraft:oak_log"), "Oak Log");
        assert_eq!(block_name("create:brass_casing"), "Brass Casing (create)");
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
