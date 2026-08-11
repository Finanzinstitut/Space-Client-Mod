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
| Keystrokes | on | three layouts, reads Minecraft's own key bindings |
| Ping | off | |
| Clock | off | |
| Speedometer | off | also shows % of the theoretical max for your state |
| Session | off | uptime plus an optional break reminder |
| Mouse Tracker | off | drawn from rectangles, every colour configurable |

Press **Right Shift** to open the menu and toggle modules. Settings are written
to `config/spaceclient.json` as soon as anything changes.

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
- Key mappings take a registered **`KeyMapping.Category`** object, not a
  translation key string, and register through
  `net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper`.
- **`setScreen` moved onto `Minecraft.gui`** — `minecraft.gui.setScreen(...)`,
  not `minecraft.setScreen(...)`.
- Colours are **ARGB**, not RGB — an RGB value renders fully transparent.

## Deliberately not included yet

Each of these needs API details this version changed and that are not yet
confirmed, so they are left out rather than guessed at:

- **The HUD editor** (dragging elements into place). Dragging needs the mouse
  event API, which changed in this version: `Screen#mouseClicked` now takes a
  `MouseButtonEvent`. The menu itself sidesteps this by being built from Button
  widgets, which handle their own clicks.
- **Per-module settings screens.** The menu toggles modules on and off; colours
  and options are still edited in the config file.
- **Mixins** (hitbox filtering, zoom, the Jupiter badge in the tab list).
- Modules touching inventory, potion effects, options (`OptionInstance`),
  or entity positions — all of those method names moved.

## Building

Push to GitHub; the workflow builds on `main` and uploads the jar as an artifact.

## License

All Rights Reserved.
