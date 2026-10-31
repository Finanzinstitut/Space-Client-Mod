// The zoom in effect, unless another mod's came first.
fn spaceclient_zoom(game: &crate::game::Game<'_>, zoom: &mut Option<crate::game::native_hooks::NativeZoom>) {
    use crate::game::spaceclient as sc;
    if zoom.is_none()
        && game.native_on(sc::ID)
        && let Some((factor, smooth, turn)) = sc::zoom()
    {
        *zoom = Some(crate::game::native_hooks::NativeZoom { factor, smooth, turn });
    }
}
