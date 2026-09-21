# The friends worker

Friend requests, friends, and messages between them. This is the half of the
feature that cannot live in the mod: two people who are not in the same world
have nothing between them but a server.

## What I could not do

**I cannot deploy this.** Deploying needs your Cloudflare account, and the
account is yours. Everything else is done: the worker is written, its database
schema is here, and it is tested - but the address in the client points at a
deployment that does not exist yet, so until you run the three commands below
the friends screen will say it cannot reach the server.

It is also **its own worker**, not an addition to the badge one. The badge
worker's source is not in any repository I have, so adding to it would have
meant editing code I cannot see.

## Deploying

```bash
cd worker/friends
npx wrangler d1 create spaceclient-friends      # prints a database_id
# put that id into wrangler.toml
npx wrangler d1 execute spaceclient-friends --remote --file=schema.sql
npx wrangler deploy
```

`wrangler deploy` prints the address. If it is not
`https://spaceclient-friends.spaceclient-finanzinstitut.workers.dev`, tell me
and I will change the one line in `Friends.java` - or start the game with
`-Dspaceclient.friends=https://your-address` and nothing has to be rebuilt.

## How it decides who you are

The same proof the rest of the client uses, and the only one that works
without a password: the game tells Mojang it is joining a server with a random
id, this worker asks Mojang whether that happened, and only then hands back a
token. The account's own secret never comes near this code.

## How a message gets there in about a second

The client asks for anything newer than it has, and this holds the request open
for up to twenty five seconds instead of answering "nothing" straight away. A
message therefore arrives about a second after it is sent, while an idle client
makes two or three calls a minute rather than sixty.

That matters because of what it costs. The free plan allows 100,000 requests a
day; at three a minute an idle client uses about 4,300 of them, so the free
plan carries on the order of twenty connected players. Past that the answer is
not a shorter hold - it is a WebSocket on a Durable Object, which is the five
dollar plan. `POLL_HOLD_MS` in `src/index.js` is the number to change.

## What is stored, and what is not

`messages` holds the text of every message until it is fourteen days old, in
your own Cloudflare database. Nobody else can read it - but you can, and the
people writing to each other should know that a friend's chat is not a private
channel between two computers. Nothing is encrypted end to end and this does
not pretend to be.

## Running it here

```bash
node test/serve.mjs 8799     # the worker, on Node's own SQLite, with a fake Mojang
node --test test/            # the tests
```

`test/serve.mjs` is also what the client's own tests talk to, so the Java side
and this side are checked against each other rather than against a description
of each other.

## The tests

- `test/logic.test.mjs` - the rules, as pure functions: asking twice, asking
  somebody who already asked you, accepting something nobody sent, messaging a
  stranger.
- `test/worker.test.mjs` - the whole worker over real SQLite with the real
  schema, including a poll that wakes up when a request arrives while it is
  waiting.
