import { test, before } from "node:test";
import assert from "node:assert/strict";
import { makeDb } from "./d1.mjs";

import worker from "../src/index.js";

const ALICE = { name: "Alice_MC", id: "11111111111111111111111111111111" };
const BOB = { name: "Bob_MC", id: "22222222222222222222222222222222" };
const CARL = { name: "Carl_MC", id: "33333333333333333333333333333333" };

let env;

/** Mojang, as far as the worker can tell. */
function fakeMojang(players) {
  globalThis.fetch = async (url) => {
    const text = String(url);

    if (text.includes("hasJoined")) {
      const name = new URL(text).searchParams.get("username");
      const found = players.find((p) => p.name === name);
      return found
        ? new Response(JSON.stringify({ id: found.id, name: found.name }), { status: 200 })
        // 204 carries no body, and undici refuses to build one that does
        : new Response(null, { status: 204 });
    }

    if (text.includes("users/profiles/minecraft/")) {
      const name = decodeURIComponent(text.split("/").pop());
      const found = players.find((p) => p.name.toLowerCase() === name.toLowerCase());
      return found
        ? new Response(JSON.stringify({ id: found.id, name: found.name }), { status: 200 })
        : new Response(null, { status: 404 });
    }
    throw new Error("unexpected call to " + text);
  };
}

const call = (path, { token, body, method } = {}) => {
  const headers = { "content-type": "application/json" };
  if (token) headers.Authorization = "Bearer " + token;
  return worker.fetch(
    new Request("https://friends.test" + path, {
      method: method || (body ? "POST" : "GET"),
      headers,
      body: body ? JSON.stringify(body) : undefined,
    }),
    env
  );
};

const body = async (response) => await response.json();

async function signIn(player) {
  const response = await worker.fetch(
    new Request("https://friends.test/session", {
      method: "POST",
      headers: { "X-Space-Name": player.name, "X-Space-Server": "abc123" },
    }),
    env
  );
  const answer = await response.json();
  assert.ok(answer.ok, "signed in: " + JSON.stringify(answer));
  return answer;
}

before(() => {
  env = { DB: makeDb(new URL("../schema.sql", import.meta.url).pathname) };
  fakeMojang([ALICE, BOB, CARL]);
});

test("signing in needs Mojang to confirm the handshake", async () => {
  const good = await signIn(ALICE);
  assert.match(good.uuid, /^11111111-1111-1111-1111-111111111111$/, "dashed uuid");
  assert.ok(good.token.length >= 32);

  const bad = await worker.fetch(
    new Request("https://friends.test/session", {
      method: "POST",
      headers: { "X-Space-Name": "Nobody_Here", "X-Space-Server": "abc123" },
    }),
    env
  );
  assert.equal(bad.status, 403);
});

test("nothing works without a token", async () => {
  const answer = await call("/friends");
  assert.equal(answer.status, 401);
});

test("a made up token is not a token", async () => {
  const answer = await call("/friends", { token: "0".repeat(64) });
  assert.equal(answer.status, 401);
});

test("the whole way: ask, appear on both sides, accept, write", async () => {
  const alice = await signIn(ALICE);
  const bob = await signIn(BOB);

  // --- Alice asks Bob by name ---
  const asked = await body(await call("/friends/request",
    { token: alice.token, body: { name: BOB.name } }));
  assert.ok(asked.ok, JSON.stringify(asked));

  const aliceList = await body(await call("/friends", { token: alice.token }));
  assert.equal(aliceList.outgoing.length, 1);
  assert.equal(aliceList.outgoing[0].name, BOB.name);
  assert.equal(aliceList.friends.length, 0);

  const bobList = await body(await call("/friends", { token: bob.token }));
  assert.equal(bobList.incoming.length, 1);
  assert.equal(bobList.incoming[0].name, ALICE.name);

  // --- Bob accepts ---
  const accepted = await body(await call("/friends/accept",
    { token: bob.token, body: { uuid: alice.uuid } }));
  assert.ok(accepted.ok);

  for (const [who, other] of [[alice, BOB], [bob, ALICE]]) {
    const list = await body(await call("/friends", { token: who.token }));
    assert.equal(list.friends.length, 1, "both see one friend");
    assert.equal(list.friends[0].name, other.name);
    assert.equal(list.incoming.length + list.outgoing.length, 0, "and nothing pending");
  }

  // --- and a message ---
  const sent = await body(await call("/messages",
    { token: alice.token, body: { to: bob.uuid, text: "hallo!" } }));
  assert.ok(sent.ok);

  const got = await body(await call("/poll?since=0&version=-1", { token: bob.token }));
  assert.equal(got.messages.length, 1);
  assert.equal(got.messages[0].text, "hallo!");
  assert.equal(got.messages[0].fromName, ALICE.name);
  assert.equal(got.messages[0].mine, false);

  // The sender sees their own line too, so both sides show the same thread
  const mine = await body(await call("/poll?since=0&version=-1", { token: alice.token }));
  assert.equal(mine.messages.length, 1);
  assert.equal(mine.messages[0].mine, true);
});

test("a stranger cannot write to you", async () => {
  const carl = await signIn(CARL);
  const bob = await signIn(BOB);

  const refused = await call("/messages",
    { token: carl.token, body: { to: bob.uuid, text: "hey" } });
  assert.equal(refused.status, 400);
  assert.match((await refused.json()).note, /only write to friends/);
});

test("nor can somebody who only sent a request", async () => {
  const carl = await signIn(CARL);
  const bob = await signIn(BOB);

  await call("/friends/request", { token: carl.token, body: { name: BOB.name } });
  const refused = await call("/messages",
    { token: carl.token, body: { to: bob.uuid, text: "hey" } });
  assert.equal(refused.status, 400);
});

test("accepting something nobody asked for is refused", async () => {
  const carl = await signIn(CARL);
  const alice = await signIn(ALICE);

  const answer = await call("/friends/accept",
    { token: alice.token, body: { uuid: carl.uuid } });
  assert.equal(answer.status, 400);
});

test("a name nobody has is said plainly", async () => {
  const alice = await signIn(ALICE);
  const answer = await call("/friends/request",
    { token: alice.token, body: { name: "Ghost_Person" } });
  assert.match((await answer.json()).note, /no player by that name/);
});

test("removing a friend stops the messages", async () => {
  const alice = await signIn(ALICE);
  const bob = await signIn(BOB);

  await call("/friends/remove", { token: bob.token, body: { uuid: alice.uuid } });

  const list = await body(await call("/friends", { token: alice.token }));
  assert.equal(list.friends.length, 0);

  const refused = await call("/messages",
    { token: alice.token, body: { to: bob.uuid, text: "still there?" } });
  assert.equal(refused.status, 400);
});

test("a poll with nothing to say comes back when its time is up, not before", async () => {
  const carl = await signIn(CARL);
  const started = Date.now();
  const answer = await body(await call("/poll?since=999999&version=-1", { token: carl.token }));
  const waited = Date.now() - started;

  assert.equal(answer.messages.length, 0);
  assert.ok(waited > 500, `it held the request open (waited ${waited}ms)`);
});

test("a change to the friend list wakes a waiting poll", async () => {
  const alice = await signIn(ALICE);
  const bob = await signIn(BOB);

  // Where Alice's feed stands right now
  const now = await body(await call("/poll?since=999999&version=-1", { token: alice.token }));

  // Bob asks while Alice is waiting
  const waiting = call(`/poll?since=999999&version=${now.version}`, { token: alice.token });
  setTimeout(() => {
    call("/friends/request", { token: bob.token, body: { name: ALICE.name } });
  }, 300);

  const started = Date.now();
  const woke = await body(await waiting);
  const waited = Date.now() - started;

  assert.notEqual(woke.version, now.version, "the version moved, so the screen knows to reload");
  assert.ok(waited < 5000, `it woke up rather than waiting out the hold (${waited}ms)`);
});

test("formatting codes cannot be smuggled into a message", async () => {
  const alice = await signIn(ALICE);
  const bob = await signIn(BOB);

  await call("/friends/request", { token: alice.token, body: { name: BOB.name } });
  await call("/friends/accept", { token: bob.token, body: { uuid: alice.uuid } });

  const mark = await body(await call("/poll?since=0&version=-1", { token: bob.token }));
  await call("/messages",
    { token: alice.token, body: { to: bob.uuid, text: "§kobfuscated\nsecond line" } });

  const got = await body(await call(`/poll?since=${mark.cursor}&version=-1`, { token: bob.token }));
  assert.equal(got.messages.at(-1).text, "kobfuscatedsecond line");
});
