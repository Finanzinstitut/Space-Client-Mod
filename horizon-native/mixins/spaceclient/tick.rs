// The window's tick: the player's position, for the speedometer and the
// session clock (a server's own Game ticks too, and is left out).
fn spaceclient_tick(game: &mut crate::game::Game<'_>) {
    if !game.native_window() {
        return;
    }
    let position = game.native_player().map(|p| (p.x, p.z));
    crate::game::spaceclient::tick(position);
}
