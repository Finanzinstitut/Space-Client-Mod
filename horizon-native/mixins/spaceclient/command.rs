// /sc and /music, answered in chat; /music pause|next|prev goes to the
// launcher's music bridge.
fn spaceclient_command(game: &mut crate::game::Game<'_>, command: &str, ran: &mut bool) {
    use crate::game::spaceclient as sc;
    if *ran || !game.native_on(sc::ID) || !sc::is_ours(command) {
        return;
    }
    *ran = true;
    let dir = game.native_data_dir(sc::ID);
    if let Some(reply) = sc::command(&dir, command) {
        game.native_chat(&reply);
    }
}
