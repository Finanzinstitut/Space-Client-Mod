// An item's model, scaled around its own origin for where it is drawn: the
// middle of its slot in the interface, the hand in first person, and for a
// dropped item the point its lift off the ground is worked out from (from the
// scaled box, so it still rests on the ground).
fn spaceclient_world(
    game: &crate::game::Game<'_>,
    stack: &horizon_client::items::ItemStack,
    display: horizon_assets::item_model::DisplayContext,
    owner: Option<&horizon_assets::item_model::OwnerState>,
    seed: i32,
) -> core::ops::ControlFlow<ItemRenderState> {
    use crate::game::spaceclient as sc;
    use horizon_assets::item_model::DisplayContext as D;
    // `ItemModelResolver.updateForTopItem`, as Horizon has it
    let level = game.item_level();
    let mut value = game.pack.item_renderer.resolve(
        &horizon_client::item_render::stack_view(stack),
        display,
        owner,
        Some(&level),
        seed,
        &game.item_env(),
    );
    if value.layers.is_empty() || !game.native_on(sc::ID) {
        return core::ops::ControlFlow::Break(value);
    }
    let dir = game.native_data_dir(sc::ID);
    if !sc::item_shower_on(&dir) {
        return core::ops::ControlFlow::Break(value);
    }
    let sizes = sc::sizes(&dir, &stack.item);
    let scale = match display {
        D::Gui => sizes.gui,
        D::FirstPersonLeftHand | D::FirstPersonRightHand => sizes.hand,
        D::Ground => sizes.ground,
        _ => 1.0,
    };
    if scale != 1.0 {
        let m = glam::Mat4::from_scale(glam::Vec3::splat(scale));
        for layer in &mut value.layers {
            layer.matrix = m * layer.matrix;
        }
        // The interface clips an item to its 16x16 slot unless it is drawn
        // as an oversized one, from its own bounds
        if display == D::Gui && scale > 1.0 {
            value.oversized_in_gui = true;
        }
    }
    core::ops::ControlFlow::Break(value)
}
