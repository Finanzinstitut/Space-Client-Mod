# Space Client badge over the name tag

Both zips. Client and worker, badge visible for every Space Client user with
no way to switch it off, as asked.

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

## Two things to be aware of

**No opt-out means a privacy notice.** Every user's UUID and name go to your
worker automatically. LabyMod does the same, but for something distributed
publicly from Germany that belongs in a privacy policy — and it will come up
anyway once the Discord server is live.

**`/register` is spoofable.** It checks that the UUID and name belong together
via Mojang's public profile lookup; it does not prove the caller owns the
account. Someone could register a stranger and give them a badge they never
installed. Your own worker comment already says this. Closing it would mean
routing `/register` through the `hasJoined` handshake that `/np/session`
already uses — a small change, but it costs a Mojang round trip per new user
and I left the cheap path in place rather than decide that for you.

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
- resources — font json and eight PNGs.

Worker:

- `src/index.js` — edge cache on `/users`, write dedupe in `/register`.

`spaceclient.mixins.json` is unchanged: no new mixin class was needed.

## Note on the API zip

`node_modules/` is not included — it was 184 MB of Windows binaries. Everything
else is there; `npm install` restores it from the `package-lock.json` in the
zip.

## Before you spend a build

`SpaceApi.BASE` is still the placeholder:

```java
public static final String BASE = "https://spaceclient-badges.example.workers.dev";
```

The badge will do nothing until that points at your deployed worker. It fails
quietly, so the build will still succeed and you will just see no badges.
