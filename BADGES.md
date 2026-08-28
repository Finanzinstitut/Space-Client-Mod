# Space Client badge over the name tag

Both zips. Client and worker, badge visible for every Space Client user with
no way to switch it off, as asked.

## Built on 0.9.0

The first attempt was built on the core zip from the start of the thread, which
turned out to be **0.1.0** — `gradle.properties` says so, and the tree is
missing everything added since, including `GuiItemInvoker` and the icon-based
armour HUD. Applying it replaced twenty files with older versions, which is why
the armour readout changed back to plain text.

This build is the 0.9.0 tree with the badge added and nothing else touched.
Verified: `ArmorModule.java` is bit-identical to your 0.9.0 copy, and the only
files that differ from 0.9.0 at all are the four listed below plus two new
classes and the assets.

Your real worker URL in `SpaceApi.BASE` is preserved — 0.9.0 already had
`spaceclient-badges.spaceclient-finanzinstitut.workers.dev`, so there is
nothing to fill in this time.

## The open question, answered

**How do you draw your own texture beside a name tag in 26.2?**

You don't. You draw a character instead.

The name tag pipeline takes a `Component` and nothing else. Getting a texture
in beside it would mean a second draw call positioned by hand in world space,
against `SubmitNodeCollector` parameters that are not all understood — the
existing mixin comment already records two wrong guesses at those.

A glyph avoids the whole problem. The badge is a single character in a font of
its own, prefixed to the component that is already being drawn. It scales,
sorts, fades with distance and hides behind blocks exactly like the name,
because it *is* the name as far as the renderer is concerned. No new hook, no
new draw call, no `@Invoker` on the private blit methods.

## Verified from the jar, not guessed

Both class files were decoded from the base64 you pasted and parsed directly.

`Style.withFont` does not take an `Identifier` any more:

```
public withFont (Lnet/minecraft/network/chat/FontDescription;)Lnet/minecraft/network/chat/Style;
```

`FontDescription` is an interface with three implementations — `Resource`,
`PlayerSprite`, `AtlasSprite`. `Resource` is a record with a public
constructor:

```
net/minecraft/network/chat/FontDescription$Resource
  super:      java/lang/Record
  implements: net/minecraft/network/chat/FontDescription
  public <init> (Lnet/minecraft/resources/Identifier;)V
  public id  () Lnet/minecraft/resources/Identifier;
```

So the call in `NameBadge`:

```java
Style.EMPTY.withFont(new FontDescription.Resource(
        Identifier.fromNamespaceAndPath("spaceclient", "badge")));
```

`Identifier.fromNamespaceAndPath` rather than `Identifier.of`, because the
former is already used in `SpaceClient.java` and is therefore proven on this
version. `Style.EMPTY` is confirmed as a field on the dumped class.

Everything else the badge path touches was already in use in your code:
`Component.literal`, `AvatarRenderState.id`, `mc.level.getEntity(int)`.

## Both overloads

`NameBadge.decorate(state, text)` is applied inside the two existing
`@Redirect` handlers in `EntityRendererMixin`, replacing the `text` argument
on the way through. The badge therefore appears wherever the name appears, on
whichever overload happens to fire.

Nothing new draws. The song lines below are untouched — they call
`submitNameTag` from `addSong`, which is not an injected method, so the
redirect does not apply to them and the badge does not repeat on every line.

## KV load: this costs less than what you already run

The badge is deliberately built the opposite way round from `NowPlayingShare`,
because the free tier's binding constraint is **writes: 1000/day**, with
**lists: 1000/day** close behind. Reads are 100k/day and effectively free.

Per-player polling at now-playing rates would have been fatal here: one list
per client per poll, every few seconds, against a thousand a day total.

What it does instead:

| | Interval | KV cost |
|---|---|---|
| `/register` | 12 h, client side | 1 read, usually 0 writes |
| `/users` | 30 min, client side | 0 — served from edge cache |
| `/users` cache miss | 15 min, per datacentre | 1 list |

Two worker changes make that work:

- **`/users` is put into `caches.default` explicitly.** The `Cache-Control`
  header alone was doing nothing — a Worker response is not cached on the way
  out unless the Worker puts it there. That header has been in your code all
  along and never took effect. This is the single biggest saving.
- **`/register` skips the write when it saw you less than 12 h ago.** The
  entry lives 30 days, so rewriting it only changes a timestamp nobody reads.
  A player who restarts the game all evening now costs one write, not twenty.

Rough daily figures at 100 active players: ~100 writes, ~200 reads, ~96 lists.
Well inside the free tier, and it barely moves with player count — the roster
call is shared, so the tenth player costs the same as the thousandth.

Where this stops scaling: `/users` sends every badge UUID to every client.
At 1,000 users that is about 36 KB per fetch, which is fine. Past roughly
10,000 it wants a compact format or a bloom filter instead. Worth knowing, not
worth building yet.

## Fixed after the first attempt: the roster stayed empty

`/register` verified the uuid and name against Mojang's profile API with a
direct `fetch` to `sessionserver.mojang.com`. That call runs on Cloudflare, and
Mojang answers Cloudflare addresses with an Akamai block page — the exact
problem `MOJANG_PROXY` exists to solve, except the proxy relays `hasJoined`
only. So verification always failed, `/register` always answered 403, and KV
never received a single user.

`/register` now takes identity from the `/np/session` token instead. That token
is only issued after Mojang confirms a `hasJoined` handshake through the Deno
proxy, so the uuid is one Mojang vouched for. No Mojang call happens in
`/register` at all any more, and the mod already holds a token for reporting
songs, so it costs nothing extra.

This also closes the spoofing hole noted in the first version: registration
identity now comes from the token, not from a uuid in the request body.

Two smaller faults the same bug was hiding:

- **An empty roster was being cached for 15 minutes.** Empty results are no
  longer cached, and a first registration drops the cached list.
- **Badge failures were invisible on the diagnostics page.** `SpaceApi.status`
  is written by the now playing calls every few seconds, so a register error
  was overwritten with `ok` almost immediately. Badge calls now report through
  a separate `badgeStatus`.

## Fixed after the second attempt: KV list is eventually consistent

The worker was then provably correct — `/users` returned the account, both
through the cache and through the cache-bypassing `since` path — while the game
still said `0 with a badge (registered)`.

A key that has just been written does not appear in `USERS.list()` for up to
about a minute. Registration and the first roster fetch start on the same tick,
so the fetch reliably came back without this account in it. That empty answer
was treated as final: `rosterLoaded` went true and the next fetch was scheduled
half an hour out.

The roster now stays on the two minute retry while this account is missing from
it, for a bounded eight attempts. Bounded rather than open ended, because a
client that genuinely is not registered looks identical from here and must not
poll forever. The diagnostics line says `waiting for yours to propagate` during
that window, so the state is visible rather than looking like a silent failure.

## One thing to be aware of

**No opt-out means a privacy notice.** Every user's UUID and name go to your
worker automatically. LabyMod does the same, but for something distributed
publicly from Germany that belongs in a privacy policy — and it will come up
anyway once the Discord server is live.

## Assets

```
assets/spaceclient/font/badge.json          four glyphs, one font
assets/spaceclient/textures/font/           8x8, what the name tag draws
assets/spaceclient/textures/gui/badge/      16x16, for menus
```

`\uE000` standard, `\uE001` developer, `\uE002` owner, `\uE003` vip. Only
`\uE000` is wired up; the other three are present and unused, as asked. To use
one, change `STANDARD` in `NameBadge` — the rank lookup itself does not exist
yet.

## Files changed

Client:

- `net/Presence.java` — new. Roster and registration timers.
- `render/NameBadge.java` — new. Turns a name into a badged name.
- `mixin/EntityRendererMixin.java` — badge applied in both redirects.
- `net/SpaceApi.java` — added `register()` and `badgeUsers()`.
- `SpaceClient.java` — `Presence.tick()` in the client tick.
- `ui/DiagnosticsScreen.java` — a "Badges" line, since you can't debug locally.
- `net/SpaceApi.java` — separate `badgeStatus`, so a badge failure is not
  overwritten by the next now playing call.
- resources — font json and eight PNGs.

Worker:

- `src/index.js` — `/register` runs on the token instead of the blocked
  Mojang profile lookup; edge cache on `/users`; write dedupe in `/register`;
  empty rosters not cached; cache dropped on a first registration.
- `mojang-proxy.ts` — unchanged. It only ever needed to relay `hasJoined`.

`spaceclient.mixins.json` is unchanged: no new mixin class was needed.

## Note on the API zip

`node_modules/` is not included — it was 184 MB of Windows binaries. Everything
else is there; `npm install` restores it from the `package-lock.json` in the
zip.

## Order matters

Deploy the worker before building the client. The new client sends a bearer
token to `/register`; the old worker ignores it and keeps trying the blocked
Mojang call.

```
wrangler deploy
```

## Menu rebuild

The right shift menu is a window now, not a takeover. It sits in the middle at
between 400 and 620 wide, the world stays visible behind a veil, and opening it
reads as pausing rather than leaving the game. The palette is unchanged —
`Theme` was not touched, and the starfield backdrop still appears for anyone
who has that background style selected, drawn behind the panel instead of
instead of the game.

Categories used to be the only way through the list, which made them behave
like pages: reaching a module meant knowing its shelf first. Now there is one
continuous scroll with a search field above it, and the categories are a filter
you may use rather than a route you must take. Search covers name, id and
description, so "fps" finds the module that mentions frames without carrying
the word in its name. An `On` chip stacks with the categories rather than
replacing them, because HUD-and-enabled is a reasonable thing to ask for.

Modules are rows rather than cards. In a window a third the old size a 74 pixel
card showed four modules out of twenty six; a 26 pixel row shows eleven. The
row height is also what keeps the scroll safe: a row overhangs the list edge by
at most its own height while scrolling, which lands inside the footer where a
strip covers it. A card would have hung past the window and painted over the
world.

New file: `ui/ModRow.java`. Rewritten: `ui/SpaceMenuScreen.java`. `ModCard` is
left in place — nothing points at it now, but it is not this change's business
to delete it.

### Search input, verified

The search field is a vanilla `EditBox`, and the jar says that was the right
call. Input in 26.2 runs on `KeyEvent`, `CharacterEvent` and
`MouseButtonEvent` - a hand written `charTyped(char, int)` would have compiled
into a method nothing ever calls, and the field would have sat there refusing
to type with no error to explain why. A widget handles its own events
internally, so the screen never touches those signatures.

Checked against `EditBox.class`: the constructor
`(Font, int, int, int, int, Component)`, `setResponder(Consumer)`,
`setMaxLength`, `setBordered`, `setTextColor`, `setValue`, `getValue` and
`setHint` all exist. `setHint` turned out to be available, so the placeholder
is vanilla's rather than drawn by hand.

## Badge ring

The ring sat one pixel below the middle of the planet. The body was placed with
a distance function and the ring on a fixed row, and at eight pixels those never
agreed: the disc filled rows 0 to 6, so its centre row was 3, while the ring was
drawn on row 4. The disc was also half a pixel left of centre, because a circle
centred on 3.5 in an 8 wide grid rounds outward unevenly.

The 8x8 glyphs are now an explicit mask, symmetric by construction about the gap
between columns 3 and 4 and about row 3, which is where the ring runs. The tips
are drawn in the bright ring tone rather than the shadow tone — against the dark
outline the shadow tone disappeared, so the ring appeared to stop at the
planet's edge instead of passing through it.

The 16x16 set is untouched: its ring is centred by construction and you picked
that version.


## Menu fixes after first look

Two faults, one of them mine twice over.

**Rows drew across the whole screen.** The comment claimed a row overhangs the
list by at most its own height. That is only true if only the visible rows get
a widget, and that part was never written - `buildList` gave all twenty five
modules one, laid out from the top of the list downward, which at 28 pixels a
row runs seven hundred pixels past a window three hundred tall.

The strips meant to hide the overflow could not have worked either: they were
filled with `Theme.CONTENT`, whose alpha is `0xE6`. A cover that passes ten
percent of what is behind it does not hide anything, it dims it.

Both are gone rather than patched. Scrolling now counts whole rows, and only
rows that fit are given a widget, so nothing ever sits half in the list and
there is no overflow to cover. One wheel notch moves one row - marginally less
fluid than pixel scrolling and worth it, because a row is now either inside the
list or does not exist.

The panel also got an opaque base under `Theme.CONTENT`, so the world no longer
shows faintly through the menu.

**The footer overlapped itself.** The module count was drawn on the left and
the hovered description in the same place, so the two ran together into an
unreadable smear. The count is gone, as asked - the list is right there to be
counted - and the description now has the line to itself, trimmed with
`Font.plainSubstrByWidth` so it can never run into the player name on the
right. That method is verified: `EditBox` uses it in 26.2.

One trap worth recording, found while fixing the above: `EditBox.setValue`
fires the responder. Setting the value after the responder would have built the
list once there and again when `init` reached `buildList`, stacking two widgets
on every row - the same class of bug as the one above, arriving by a different
door.
