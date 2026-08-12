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

## A note on the keyboard view

`FULL` draws a proper keyboard — number row, TAB/QWERTZUI, CAPS/ASDFGHJ,
SHIFT/YXCVBN, CTRL and space — with the real key proportions and row offsets.
`CUSTOM` keeps that same shape and simply leaves out the keys you did not list,
so the layout stays recognisable.

Pressed state comes from Minecraft's key bindings. Keys the game binds by
default light up: WASD, space, shift, ctrl, the number row (hotbar slots), Q
(drop), E (inventory), F (offhand), T (chat), TAB (player list). Letters with no
default binding — Y, X, C, V, B, N and the rest — are drawn as part of the
keyboard but stay dark, because the game never reports them to a mod. Lighting
those up would need raw keyboard polling, which needs the window handle whose
accessor has not been confirmed for this version.

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
