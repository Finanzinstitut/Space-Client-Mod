// A click on Space Client's settings page: switch it and keep it.
fn spaceclient_click(game: &mut crate::game::Game<'_>, mod_id: &str, button: usize) {
    use crate::game::spaceclient as sc;
    if mod_id != sc::ID {
        return;
    }
    let dir = game.native_data_dir(sc::ID);
    let mut settings = sc::settings(&dir);
    settings.click(button);
    sc::set_settings(&dir, settings);
}
