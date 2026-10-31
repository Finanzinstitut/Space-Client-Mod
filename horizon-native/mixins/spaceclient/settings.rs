// Space Client's settings page: one button per HUD part, then the side and
// the background.
fn spaceclient_page(game: &mut crate::game::Game<'_>, mod_id: &str, buttons: &mut Vec<String>) {
    use crate::game::spaceclient as sc;
    if mod_id != sc::ID {
        return;
    }
    buttons.extend(sc::settings(&game.native_data_dir(sc::ID)).labels());
}
