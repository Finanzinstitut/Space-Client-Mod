# Third party work

## WaveyCapes — tr7zw
- Licence: tr7zw Protective License (see `THIRD-PARTY-WaveyCapes-LICENSE.txt`)
- Copyright (c) tr7zw, 2021
- https://github.com/tr7zw/WaveyCapes

The licence permits use, modification and compilation, and forbids using the
work for commercial advantage or monetary compensation.

**WaveyCapes code is included in Space Client.** The cape simulation and the
cape renderer in `src/main/java/gg/spaceclient/wavey/` are tr7zw's WaveyCapes
(26.2 branch), ported with the Stonecutter version branches for 26.2 resolved,
Lombok and tr7zw's helper libraries replaced by plain code, and the optional
integrations with other mods left out. Each ported file carries a header naming
the source and the licence; the mixins that attach it
(`WaveyCapeEntityMixin`, `AvatarRendererMixin`, `CapeLayerMixin`) say so too.

Because of the licence's condition, Space Client must not be used to earn
money while this code is in it: no paid ranks, paid cosmetics or other sales
tied to the client. If that ever changes, the WaveyCapes code has to come out
first (or tr7zw's permission obtained).

## ItemPhysic — CreativeMD / team.creative
- Licence: LGPL-3.0
- https://github.com/CreativeMD/ItemPhysic

Space Client's own `ItemPhysicsModule` implements the same idea independently.
**No ItemPhysic code is included in Space Client.** LGPL-3.0 source cannot be
copied into a work distributed as all rights reserved, which is how Space
Client is currently licensed (`fabric.mod.json`: `"license": "ARR"`). Any item
physics in this client has to be written independently.

Two lawful alternatives, if the goal is to ship ItemPhysic's actual behaviour:
1. Ship ItemPhysic as a separate mod the user installs alongside, and have
   Space Client detect it. Nothing is copied, so nothing is triggered.
2. Relicense the parts of Space Client that would contain LGPL code, and meet
   the LGPL's source and relinking requirements.

## Inter — The Inter Project Authors (Rasmus Andersson)
- Licence: SIL Open Font License 1.1 (see `src/main/resources/assets/spaceclient/font/inter-OFL.txt`)
- https://github.com/rsms/inter

Inter Display SemiBold, subset to Latin, Greek and Cyrillic, is the bundled
choice for the lyrics in the sky (`assets/spaceclient/font/sky.ttf`). The OFL
allows bundling and subsetting; Inter declares no Reserved Font Name.

Apple's SF fonts are **not** bundled: their licence allows them only for
mock-ups of software for Apple's own systems. On a Mac the sky lyrics read
the copy of SF Pro that macOS itself has installed, as any program drawing
text with the system font does; nothing of it is in the jar.

## Horizon — Ludovic Marie and the Horizon contributors
- Licence: MIT
- https://github.com/horizon-rust/horizon

`horizon-native/` is a horizon-mixins bundle that Space Client's CI compiles
into Horizon (`.github/workflows/horizon.yml`). The published archives are
Horizon's own Nightly archives, its `LICENSE` included, with only the
`horizon-client` program rebuilt from that Nightly's source with the bundle
applied. The helpers' shape (one small file per hook point) follows
Horizon's official native mods (horizon-rust/horizon-mods, MIT).

## horizon-mixins — levzzz5154 and contributors
- Licence: MIT
- https://github.com/horizon-rust/horizon-mixins

Used as a build tool only; none of its code is in the game.

## Minecraft — Mojang Studios
Space Client is a modification and is not affiliated with, endorsed by, or
connected to Mojang Studios or Microsoft.

## Fabric — FabricMC
- Licence: Apache-2.0

## Main menu layout — Chill Client
The shape of the opening sequence was taken from screenshots. No code was
copied; the implementation is entirely Space Client's own.
