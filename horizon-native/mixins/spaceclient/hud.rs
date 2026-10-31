// Space Client's HUD: the readout column and the keystrokes in a top corner,
// the compass and the Now Playing card at the top centre.
fn spaceclient_hud(
    game: &mut crate::game::Game<'_>,
    batch: &mut horizon_client::hud::HudBatch,
    corners: &mut horizon_client::extras_hud::Corners,
) {
    use crate::game::spaceclient as sc;
    use horizon_client::extras::Corner;
    if !game.native_on(sc::ID) {
        return;
    }
    let dir = game.native_data_dir(sc::ID);
    let settings = sc::settings(&dir);
    let mut key = |name: &str| game.native_mapping(name).unwrap_or_default();
    let keys = ["key.forward", "key.left", "key.back", "key.right"].map(&mut key);
    let (jump, attack, use_) = (key("key.jump").0, key("key.attack").0, key("key.use").0);
    let clicks = if settings.is_on(sc::Part::Cps) { game.native_clicks() } else { (0, 0) };
    let player = game.native_player();
    let inputs = sc::Inputs {
        fps: game.native_frame_rate().0,
        clicks,
        player: player.as_ref().map(|p| (p.x, p.y, p.z, p.yaw)),
        keys,
        jump,
        attack,
        use_,
    };
    let game = &*game;
    let width = |text: &str| game.native_text_width(text);
    let draw = |batch: &mut horizon_client::hud::HudBatch, at: (f32, f32), shapes: Vec<sc::Shape>| {
        for shape in shapes {
            match shape {
                sc::Shape::Fill([x, y, w, h], argb) => game.native_fill(batch, [at.0 + x, at.1 + y, w, h], argb),
                sc::Shape::Text(text, [x, y], rgb, shadow) => {
                    game.native_text(batch, &text, [at.0 + x, at.1 + y], rgb, shadow)
                }
            }
        }
    };
    let corner = if settings.right { Corner::TopRight } else { Corner::TopLeft };
    if game.native_corner_shown(corner) {
        let lines = sc::lines(&settings, &inputs);
        if !lines.is_empty() {
            let ((w, h), shapes) = sc::column(&settings, &lines, &width);
            let at = corners.place(corner, w, h);
            draw(batch, at, shapes);
        }
        if settings.is_on(sc::Part::Keystrokes) && inputs.player.is_some() {
            let (w, h) = sc::KEYS_SIZE;
            let at = corners.place(corner, w, h);
            draw(batch, at, sc::keystrokes(&inputs, &width));
        }
    }
    let music = if settings.is_on(sc::Part::Music) { sc::now_playing(&dir) } else { None };
    let yaw = inputs.player.map(|p| p.3);
    let screen_w = batch.width;
    draw(batch, (0.0, 0.0), sc::top_centre(&settings, yaw, music.as_ref(), screen_w, &width));
}
