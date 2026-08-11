# Space Client — In-Game Mod 🚀

The client-side companion to the Space Client launcher: a Fabric mod that adds a
HUD and utility modules, opened with **Right Shift**.

## Status: foundation + first module batch

This is deliberately built as a framework first. Adding a module is one class
plus one line in `ModuleManager`, so the remaining features can be added in
batches without touching the core.

**Working now — 20 modules**

| Module | The bit other clients don't have |
|---|---|
| FPS | — |
| CPS | shared counter other modules read from |
| Coordinates | facing direction inline |
| Ping | — |
| Clock | — |
| Keystrokes | three layouts: keybinds, full keyboard, custom key list |
| Mouse Tracker | drawn from primitives, so every colour is configurable; movement dot follows your aim |
| Armor Status | — |
| Fullbright | restores *your* gamma on disable instead of a hardcoded default |
| **Hitbox** | Mojang's own boxes and blue look-arrow, plus per-category filters, and `DISTANCE_FADE` / `HEALTH_TINT` styles |
| **Zoom** | scroll wheel adjusts magnification *while zoomed*, remembered for the session; sensitivity scales with it |
| **Toggle Sprint** | hunger-aware: releases sprint before you eat into your last shanks |
| **Reach Display** | records the longest reach you actually *landed*, not just what the crosshair says |
| **Combo Counter** | tracks health actually lost across the combo, so `7x (-9.5 HP)` |
| **TPS** | sparkline of the last 30 samples, so burst lag is visible instead of averaged away |
| **Saturation** | converts saturation into an estimate of remaining sprint seconds |
| **Speedometer** | shows speed as a percentage of the theoretical max for your current state |
| **Potion Status** | sorted by remaining time, blinks before expiry |
| **Session** | uptime, in-game day, and an optional break reminder after two hours |
| **Item Counter** | burn-rate estimate: how long your gapples/pearls last at your current usage |
| **Durability Alert** | estimates *hits remaining*, not a percentage |
| **TNT Timer** | tells you whether you are actually inside the blast radius |
| **Auto Reconnect** | exponential backoff and an attempt limit instead of hammering a downed server |
| **Time Changer** | `FOLLOW_REAL_TIME` maps the in-game sky to your actual clock |
| **Jupiter Badge** | you choose whether to show it for everyone, only yourself, or only others |

**Hitbox** deserves a note since it was specifically requested: the geometry is
vanilla's, including the blue eye-direction arrow, because the module simply
flips Minecraft's own `renderHitboxes` flag per entity through a mixin rather
than drawing its own boxes. Filters cover players, hostile mobs, passive mobs,
other entities and yourself, and line width, box colour and arrow colour are all
configurable.

**Keystrokes layouts**

- `KEYBINDS` — the classic WASD block plus mouse buttons and space
- `FULL` — a complete QWERTY keyboard
- `CUSTOM` — only the keys you list, e.g. `W,A,S,D,SPACE,F,Q`

## The Jupiter badge — read this before relying on it

A Minecraft client **cannot detect what client another player is using.** Nothing
about it travels over vanilla protocol, and servers do not relay it. So "everyone
who downloaded the client gets a Jupiter icon" cannot work automatically without
a service to register with.

What is implemented instead:

- **You always see your own badge.**
- **Other players get one only if their UUID is in `users.json`** in the mod
  repository, which the mod fetches on startup.
- **Players without the mod see nothing at all** — the badge is drawn by your
  client, on your screen. It is not visible to anyone else.

To make it automatic, the mod would need to register its UUID with a small
backend on startup, and query that backend for the list — roughly what NoRisk
does. That is a service to host, not a mod feature. Say the word and I can
sketch it; a single endpoint on a free tier would cover it.

In the tab list the badge is the 16×16 texture; above heads it is the Jupiter
glyph `♃`, because a texture cannot be injected into a Text component.

## Menu

Right Shift opens the menu. Left click toggles a module, right click (or the
`...` on the card) opens its settings, and `E` opens the HUD editor where
elements are dragged into place and scaled with the scroll wheel. Positions are
stored as a fraction of the screen, so they survive resolution changes.

Settings live in `config/spaceclient.json` inside the instance.

## Not built yet

Roughly forty of the sixty modules in the reference screenshots are still open.
The ones needing deeper rendering hooks are the larger remaining chunk:
Nametags, Scoreboard customisation, Freelook, Motion blur, NoFog, Block
outlines, Glint colouriser, Chat heads, Shulker preview, Hitcolor, Arrow trail.

Lighter ones still to come: Auto Reconnect, AutoText, Screenshot tool,
Waypoints, Time/Weather changer, Streamer mode, TNT timer, Item highlighter,
Jump reset, Day counter as its own element.

**Deliberately not planned**: anything tied to another launcher's
infrastructure — its server list, cosmetics system, capes or creator codes.
Those are their services, not features that can be reimplemented.

## Building

Push to GitHub; `.github/workflows/build.yml` builds on `main` and uploads the
jar as an artifact.

## Notes on Minecraft 26.2

Two areas are the likely sources of first-build failures:

- **Rendering hooks.** `HudRenderCallback` and `DrawContext` have moved
  repeatedly across recent versions.
- **Mixin targets.** `EntityRenderDispatcher#render` and `GameRenderer#getFov`
  change signature often. If the mixin fails to apply, the message names the
  method it could not find, which is usually a one-line fix.

Minecraft ships **unobfuscated** from 26.1 onwards, which changes the build
setup in three ways that trip up anyone following an older tutorial:

- The plugin id is **`net.fabricmc.fabric-loom`**, not `fabric-loom`. The new
  one does not remap anything; the old one only works on obfuscated versions.
- **There is no `mappings` line at all.** Not Yarn (no longer published), not
  `officialMojangMappings()` (Loom cannot find mappings that do not exist).
- Dependencies use plain **`implementation`**, not `modImplementation`, and the
  output comes from **`jar`**, not `remapJar` — nothing gets remapped.

The source uses Mojang names throughout — `Minecraft`, `GuiGraphics`,
`Component`, `mc.options.keyAttack`. That conversion was done mechanically
across all 35 files, so expect a handful of method names to still be off on the
first successful configure. Each is a one-line fix the compiler names precisely.

## License

All Rights Reserved.
