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
| Zoom | off | key chosen in the module's own settings, read from the device |
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
`WorldRenderEvents` in an import was itself enough to fail the build, because
Fabric API 26.1 renamed it to `LevelRenderEvents` and moved it into a `level`
subpackage. `render/WorldRenderHook` looks the class up by name at runtime,
builds a dynamic proxy against whichever callback interface it declares, and
reports failure to the caller instead of exploding.

Every Fabric and Minecraft import left in the source has now compiled at least
once, which is checked mechanically rather than assumed.

That split exists because each failed build means another upload from a phone,
so the cost of guessing wrong is much higher than the cost of a little
reflection.

## Diagnostics

The last button in the menu opens a page listing every reflective lookup the mod
makes, with OK or a reason it failed: the world render event, the line box
renderer, the raw keyboard handle, the field of view option, the account field
on `Minecraft`, and whether the launcher's accounts can be read.

This exists because a failed lookup otherwise only writes to a log file, and the
symptom in game is simply that a feature does nothing. One screenshot of this
page says which lookup is at fault, instead of another round of guessing.

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

### The bug that made switching accounts fail

The account object is built by filling whichever constructor this version
declares, positionally by parameter type. The values were assigned by counting
String parameters: first the name, then the uuid, then the token.

That is wrong whenever the uuid has a **`UUID`-typed parameter of its own** —
because then the *second* String is the access token, and it was being handed
the uuid instead. The result was an account with the right name and a nonsense
token: the game showed the new username, and every server rejected the login as
an invalid session.

It passed the verification step because that only compared the name. Both halves
are fixed: the token is placed correctly, and after the swap the token is read
back out of the live account and compared, so a wrong slot cannot pass as
success again. The Diagnostics page shows the live token's length and last six
characters, which makes a real token distinguishable from a uuid at a glance.

### Zoom, and why changing the option was not enough

The field of view option is clamped to the slider's range, roughly 30 to 110.
A zoom wants to go well below that: at four times magnification from a base of
110, the target is 27. The setter does not refuse that — it silently clamps to
30, so the mouse sensitivity dropped as configured while the view barely moved.

The value is therefore written into the option's own field when the setter's
result comes back clamped, past the validation. The renderer reads the same
field, so the zoom is real rather than a nudge to the slider's edge.

### "invalid_public_key_signature" after switching accounts

Minecraft signs chat with a keypair tied to the signed-in profile. After a
session swap the keypair still belongs to the previous account, and any server
running with `enforce-secure-profile` refuses the join because the signature does
not match the profile presenting it.

The key manager is now rebuilt after a swap, through whichever static factory
its class exposes, with arguments matched by type from the running game. If that
fails it costs chat signing only, so it is logged rather than treated as fatal —
and the Diagnostics page shows whether the manager was found at all.

### Why "invalid session" kept coming back

Microsoft **rotates refresh tokens**: every use hands back a new one and retires
the one just used. The mod read the launcher's copy but never kept the new one,
so the second refresh in a session replayed a token Microsoft had already
invalidated and failed — while still reporting that a refresh had run.

Rotated tokens now live in `config/spaceclient-tokens.json`, preferred over the
launcher's copy and dropped again when Microsoft rejects one, so the launcher's
version gets a turn. The launcher's own file is never written to.

The swap is also **verified instead of assumed**: after writing the new session,
the game is asked who it thinks it is, and a mismatch is reported rather than
passing as success. The Accounts screen shows that live value under the heading,
so a swap that did not take is visible immediately.

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
