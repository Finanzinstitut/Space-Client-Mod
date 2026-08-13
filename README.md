# Space Client — In-Game Mod 🚀

Fabric mod for Minecraft **26.2** that adds HUD modules to the Space Client launcher.

## Status: minimal core

This is deliberately cut back to a core that uses only APIs verified against
Fabric's own 26.2 reference code. An earlier, much larger version was written
against guessed API names and produced 100 compile errors — rather than guessing
again, the scope was reduced to what is known correct, to be expanded once each
addition is confirmed building.

**Included**

| Module | Default | Notes |
|---|---|---|
| FPS | on | |
| CPS | on | shared counter other modules read from |
| Coordinates | on | |
| Keystrokes | on | KEYBINDS / FULL / CUSTOM, laid out like a real keyboard |
| Ping | off | |
| Clock | off | |
| Speedometer | off | also shows % of the theoretical max for your state |
| Session | off | uptime plus an optional break reminder |
| Mouse Tracker | off | drawn from rectangles, every colour configurable |
| Memory | off | bar turns amber then red as the heap fills |
| Compass | off | scrolling strip, so you can hold a heading between cardinals |
| Travelled | off | also converts the distance to Nether equivalents |
| Zoom | off | hold C; rebind under Options → Controls → Space Client |
| Hitbox | off | per-category boxes with own colour and width — see below |
| Chunk | off | chunk coordinates plus position inside the chunk |
| Players Online | off | flags briefly when the count changes |
| Aim | off | yaw and pitch, plus the nearest 45° snap |
| Align | off | only the deviation from the nearest axis; turns green when exact |
| Click Graph | off | rolling graph, so bursts and steady clicking look different |
| Marker | off | sneak + drop stores a spot; shows bearing and distance back |

Press **Right Shift** to open the menu. The interface deliberately matches the
launcher rather than vanilla Minecraft: the same violet and cyan accents, the
same deep-violet starfield with a glowing planet in the corner, flat panels and
hairline borders instead of Minecraft's bevelled buttons.

**Appearance** changes the background (space, dark, solid black, transparent)
and the accent colour with RGB sliders, with a live preview. Everything is
written to `config/spaceclient.json` as soon as it changes.

The middle mouse button has no vanilla binding, so Space Client registers its
own — it appears in the normal Controls screen under "Space Client" and can be
rebound there.

## What 26.2 changed

Worth writing down, because almost every tutorial online is wrong for this version:

- Minecraft ships **unobfuscated**. The Loom plugin id is
  `net.fabricmc.fabric-loom`, there is **no `mappings` line**, and dependencies
  use plain `implementation`.
- `GuiGraphics` is now **`GuiGraphicsExtractor`** (`net.minecraft.client.gui`).
- `ResourceLocation` is **`Identifier`** (`net.minecraft.resources`).
- `HudRenderCallback` is gone. HUD drawing goes through
  `HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id, element)`,
  where an element takes `(GuiGraphicsExtractor, DeltaTracker)`.
- Text is drawn with `graphics.text(font, str, x, y, argb, shadow)`.
- `Screen#render` is now **`extractRenderState`**, same parameters.
- Custom widgets override **`extractWidgetRenderState`**, not `renderWidget`, and
  it is **`public`** — declaring it `protected` fails to compile.
- On **buttons** that method is `final`. A button subclass overrides
  **`extractContents`** instead, which runs after the vanilla sprite is drawn —
  filling the full bounds there paints over it, giving a flat custom look.
- Key mappings take a registered **`KeyMapping.Category`** object, not a
  translation key string, and register through
  `net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper`.
- **`setScreen` moved onto `Minecraft.gui`** — `minecraft.gui.setScreen(...)`,
  not `minecraft.setScreen(...)`.
- Colours are **ARGB**, not RGB — an RGB value renders fully transparent.

## How risky calls are handled

Every Minecraft API call in this mod falls into one of two groups.

**Proven** — confirmed by a successful compile — is called directly:
`getX/getY/getZ`, `getYRot/getXRot`, `getFps`, `isSprinting`, `getUUID`,
`getConnection().getPlayerInfo()`, `mc.level`, `mc.options.key*`,
`mc.options.sensitivity()`, `mc.font`, `getWindow().getGuiScaled*`.

**Unverified** goes through reflection instead: `mc.options.fov()`, the player
list accessor, the whole world render context, entity bounding boxes and view
vectors, the window handle, and adding a widget to someone else's screen. A
wrong guess there costs a null and one log line rather than a failed build.

That includes **subscribing to Fabric's world render event**. Naming
`WorldRenderEvents` in an import was itself enough to fail the build, because it
moved out of `rendering.v1` in this version. `render/WorldRenderHook` looks the
class up by name at runtime, builds a dynamic proxy against whichever callback
interface it declares, and reports failure to the caller instead of exploding.

Every Fabric and Minecraft import left in the source has now compiled at least
once, which is checked mechanically rather than assumed.

That split exists because each failed build means another upload from a phone,
so the cost of guessing wrong is much higher than the cost of a little
reflection.

## Colours

Every colour — the interface accent and each module's own colours — is picked on
a **hue and saturation wheel** with a brightness bar beside it, not by typing a
hex code. Opacity keeps a slider, since it has no place on a hue wheel.

The wheel is drawn as small filled squares and does its own HSB conversion
rather than pulling in `java.awt`, which lives in a module that is not
guaranteed to be on the runtime image.

## Hitboxes

Four categories — **yourself, other players, mobs, items** — each in its own
sub-menu with a switch, a colour and a line width. On top of that the
look-direction arrows can be turned off, or limited to players only, since on
dropped items they are mostly clutter.

Line width is faked by drawing the box several times, each slightly larger than
the last: the render pipeline does not expose line thickness directly.

The drawing goes through `LevelRenderer.renderLineBox`, located by name and
parameter count through reflection rather than called directly, because the
render rewrite in this version moved a great deal around.

If the world render event cannot be subscribed to at all, the module **falls
back to Minecraft's own hitbox view** — the same boxes and arrows as F3+B, for
every entity at once. Per-category colours and widths are lost in that mode, but
the module still does something rather than silently nothing.

## Accounts and sessions

The **server list** carries a *Space Client* button in the top left corner,
because that is where switching accounts is actually needed — the in-game menu
cannot be reached from the main menu. It opens the same Accounts screen.

Fabric's usual helper for putting a widget on someone else's screen
(`Screens.getButtons`) does not exist in this version, so the widget is handed
to the screen's own protected `addRenderableWidget` through reflection, found by
name and parameter count. If that ever stops matching, the button quietly does
not appear and a line goes in the log — the rest of the mod is unaffected.

### Keeping the session alive

Reacting to a failed join was not enough: an "invalid session" failure happens
*during* the connect attempt, so no connection is ever established and there is
no drop to react to. The token is therefore renewed **before** it can go stale —
once shortly after launch, since the launcher may have been open for hours
before the game started, and then hourly, well inside the roughly one day a
session lasts. The drop trigger is kept as a second chance.

A refresh follows **the account picked in game**, not the one the launcher has
active. Reading only the launcher is what made a refresh after switching jump
back to the account the game started with.

**Accounts** in the menu lists whatever accounts the Space Client launcher has
signed in, switches between them, and refreshes the current session — all
without restarting the game.

Why it needs to exist: a Minecraft session token lasts about a day. Leave the
game running longer and joining a server fails with "invalid session", which
normally means quitting and relaunching. The mod re-runs the token chain
(refresh token → Microsoft → Xbox Live → XSTS → Minecraft) and swaps the result
into the running client.

It also reacts on its own: when a server connection drops, the session is
quietly refreshed in the background, with a 30 second cooldown so a failing
refresh cannot loop. Join again from the server list — no restart.

The disconnect is spotted by watching the network connection rather than the
screen, because reading the disconnect reason would mean reaching into the
screen's private state for a message whose wording differs per server.

### How the swap works, and what could break it

Replacing the live session means replacing Minecraft's `User` instance, which is
a private field. That is done with **reflection, matching the field by its type
rather than its name**, and the `User` itself is built by filling whichever
constructor is present positionally by parameter type. Both choices are
deliberate: names and constructor shapes move between versions, types do not,
and a mismatch here produces a logged warning instead of a broken build.

The mod reads the launcher's `accounts.json` (`%APPDATA%/space-client` on
Windows) but never writes to it — accounts are added and removed in the
launcher. If the game was not started through Space Client, the Accounts screen
says so and does nothing else.

## A note on the keyboard view

`FULL` draws a proper keyboard — number row, TAB/QWERTZUI, CAPS/ASDFGHJ,
SHIFT/YXCVBN, CTRL and space — with the real key proportions and row offsets.
`CUSTOM` keeps that same shape and simply leaves out the keys you did not list,
so the layout stays recognisable.

Pressed state is read from the **physical keyboard**, not from key bindings.
That distinction matters: with hotbar slot 5 bound to F, pressing F should light
up F — a binding-based display would light up the "5" key instead, or nothing at
all. Every key on the layout responds, including letters the game does not bind.

This uses GLFW directly. It needs the window handle, whose accessor has moved
between versions, so the handle is found by type through reflection — it is the
only `long` on the window object. If that lookup ever fails the module falls
back to key bindings and logs a warning, rather than going dark.

The mouse buttons deliberately stay on the bindings, since those genuinely are
controls and may be swapped.

## Deliberately not included yet

Each of these needs API details this version changed and that are not yet
confirmed, so they are left out rather than guessed at:

- **The HUD editor** (dragging elements into place). Dragging needs the mouse
  event API, which changed in this version: `Screen#mouseClicked` now takes a
  `MouseButtonEvent`. The menu itself sidesteps this by being built from Button
  widgets, which handle their own clicks.
- **Mixins** (hitbox filtering, zoom, the Jupiter badge next to player names).
- Modules touching inventory, potion effects, options (`OptionInstance`),
  or entity positions — all of those method names moved.

## Building

Push to GitHub; the workflow builds on `main` and uploads the jar as an artifact.

## License

All Rights Reserved.
