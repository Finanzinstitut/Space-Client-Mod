// Space Client's row in Options > Mods, with the zoom key.
fn spaceclient_listed(mods: &mut Vec<crate::game::native_hooks::NativeMod>) {
    use crate::game::spaceclient as sc;
    mods.push(crate::game::native_hooks::NativeMod {
        id: sc::ID.into(),
        title: format!("Space Client {}", sc::VERSION),
        description: sc::DESCRIPTION.into(),
        keys: vec![[sc::ZOOM_KEY.into(), "Zoom".into(), sc::DEFAULT_ZOOM_KEY.into()]],
    });
}
