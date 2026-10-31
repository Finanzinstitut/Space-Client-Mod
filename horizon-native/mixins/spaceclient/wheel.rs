// While zoom is held, the wheel zooms instead of moving the hotbar.
fn spaceclient_wheel(game: &crate::game::Game<'_>, steps: f32, claimed: &mut bool) {
    use crate::game::spaceclient as sc;
    if game.native_on(sc::ID) && sc::zoom_wheel(steps) {
        *claimed = true;
    }
}
