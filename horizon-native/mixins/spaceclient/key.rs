// The zoom key (C unless the player chose another in Options > Mods, or the
// launcher carried the Java client's over).
fn spaceclient_key(game: &mut crate::game::Game<'_>, key: &str, pressed: bool) {
    use crate::game::spaceclient as sc;
    if !game.native_on(sc::ID) {
        return;
    }
    if key != game.native_mod_key(sc::ID, sc::ZOOM_KEY, sc::DEFAULT_ZOOM_KEY) {
        return;
    }
    let on = sc::settings(&game.native_data_dir(sc::ID)).is_on(sc::Part::Zoom);
    let playing = game.native_playing();
    sc::zoom_key(playing && on, pressed);
}
