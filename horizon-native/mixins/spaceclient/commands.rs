// Our commands, so the chat box completes and colours them.
fn spaceclient_commands(commands: &mut Vec<&'static str>) {
    commands.extend(crate::game::spaceclient::COMMANDS);
}
