# Space Client for Horizon (native)

Horizon (Minecraft 26.3 in Rust, https://github.com/horizon-rust/horizon) no
longer runs WebAssembly mods: a mod is a [horizon-mixins](https://github.com/horizon-rust/horizon-mixins)
bundle compiled into the game. This is Space Client's.

| What | Where |
|---|---|
| HUD readouts (FPS, CPS, coordinates, direction, speed, session), keystrokes, compass | `hud.rs`, `tick.rs` on `native_hud` / `native_tick` |
| Zoom (C, the wheel zooms further) | `key.rs`, `wheel.rs`, `zoom.rs` |
| Now Playing card, `/music`, `/sc` | `hud.rs`, `command.rs`, `commands.rs` (the launcher's music bridge writes `nowplaying`) |
| Item Shower (interface, hand, ground) | `world.rs`: a cancelling HEAD hook on `item_draw::resolve_item` that scales the resolved model |
| Options > Mods row and settings page | `list.rs`, `settings.rs`, `click.rs` |
| The logic and its tests | `runtime.rs`, added to the game as `game::spaceclient` |

Settings live in `horizon-mods/.horizon-mod-data/spaceclient/`: `settings`
(which parts are on), `itemsizes` (Item Shower, written by the launcher from
the Java client's setup) and `nowplaying` / `music-command` (the launcher's
music bridge). Saturation is not in here: the launcher installs it as a
Horizon shader pack.

The item hook reaches into the game's item drawing, not only its stable hook
points, so a Nightly that rewrites `resolve_item` fails the build; the
launcher then keeps installing the last build that worked.

```sh
cargo test                                           # the logic
horizon-mixins apply --workspace <horizon source> --mods <folder with mixins/spaceclient> --out stage
(cd stage && cargo build --locked --profile fast -p horizon-client --bin horizon-client)
```

CI does this for Windows, Linux and both Macs on every new Nightly
(`.github/workflows/horizon.yml`) and publishes the release `horizon`, which
the Space Client launcher installs.
