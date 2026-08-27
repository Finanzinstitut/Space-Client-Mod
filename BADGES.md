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

The first build showed `0 with a badge (ok)` — worker reachable, nobody in it.

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
identity now comes from the token, not from a uuid in the request body, so a
badge cannot be registered for somebody who never installed the mod.

Two smaller faults the same bug was hiding:

- **An empty roster was being cached for 15 minutes.** On a fresh deployment
  the first client asks before it has finished registering, and that empty
  answer stuck around — hiding everyone from exactly the person trying to
  debug it. Empty results are no longer cached, and a first registration drops
  the cached list so a newcomer shows up immediately.
- **Badge failures were invisible on the diagnostics page.** `SpaceApi.status`
  is written by the now playing calls every few seconds, so a register error
  was overwritten with `ok` almost as soon as it happened. Badge calls now
  report through a separate `badgeStatus`, which is what the "Badges" line
  reads.

Registration and the roster fetch also start on the same timer, so on a first
run the fetch usually finished before the registration landed. A successful
registration now triggers an immediate refetch if the roster does not yet list
this account, instead of leaving the new user badgeless for half an hour.

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
