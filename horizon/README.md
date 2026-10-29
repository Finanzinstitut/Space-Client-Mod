# Space Client for Horizon

[Horizon](https://github.com/horizon-rust/horizon) is Minecraft: Java Edition 26.3 rewritten in Rust. It runs no Java
mods, so this folder is the part of Space Client that can live in Horizon's mod sandbox: a WebAssembly mod (`.hmod`)
built against Horizon's mod SDK (API 0.1, pinned in `Cargo.toml`).

**What it has:** FPS, CPS, coordinates, direction, compass, day time, speedometer, session time, health and food,
the block you look at, keystrokes and zoom (hold C, scroll to zoom further). `/sc` opens the settings, which are also
on the mod's page in Horizon's Options > Mods.

**What it cannot have yet:** everything that needs the network (friends, badges, now playing, skins), the world
around the player (light levels, the Litematica helper), the inventory (armour, durability) or drawing in the world
(hitboxes, block highlight). Horizon's mod API has no way in for those today; it lists most of them as planned.

The launcher installs it into a Horizon instance's `horizon-mods/` and turns it on.

## Building

```sh
rustup target add wasm32-unknown-unknown
cargo install --locked --git https://github.com/horizon-rust/horizon --rev 071580e7160c6fdfdcdd3f0dedd84fde93b7b864 cargo-horizon-mod
cargo test --lib                 # the readouts' text
cargo horizon-mod build          # target/horizon-mod/spaceclient.hmod
cargo horizon-mod check target/horizon-mod/spaceclient.hmod
```
