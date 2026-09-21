import { test } from "node:test";
import assert from "node:assert/strict";

import {
  accept, canMessage, cleanMessage, findLink, listFor, looksLikeName,
  pairKey, remove, request, stateFor,
} from "../src/logic.js";

const ALICE = "0000-a";
const BOB = "0000-b";
const CARL = "0000-c";

test("a pair is stored once, whoever asks", () => {
  assert.deepEqual(pairKey(ALICE, BOB), pairKey(BOB, ALICE));

  const { links } = request([], BOB, ALICE);
  assert.equal(links.length, 1);
  assert.ok(findLink(links, ALICE, BOB), "found from the other side too");
});

test("asking, then being seen from both sides", () => {
  const { links, ok } = request([], ALICE, BOB);
  assert.ok(ok);
  assert.equal(stateFor(links, ALICE, BOB), "outgoing");
  assert.equal(stateFor(links, BOB, ALICE), "incoming");
});

test("asking twice is refused, and says so", () => {
  const first = request([], ALICE, BOB);
  const second = request(first.links, ALICE, BOB);
  assert.equal(second.ok, false);
  assert.equal(second.note, "already asked");
  assert.equal(second.links.length, 1, "and nothing was added");
});

test("asking back is accepting - nobody is left waiting on nobody", () => {
  const first = request([], ALICE, BOB);
  const second = request(first.links, BOB, ALICE);
  assert.ok(second.ok);
  assert.equal(stateFor(second.links, ALICE, BOB), "friends");
  assert.equal(second.links.length, 1);
});

test("you cannot add yourself", () => {
  const { ok, note } = request([], ALICE, ALICE);
  assert.equal(ok, false);
  assert.match(note, /yourself/);
});

test("accepting only works when the other side asked", () => {
  assert.equal(accept([], ALICE, BOB).ok, false, "nothing to accept");

  const asked = request([], ALICE, BOB);
  assert.equal(accept(asked.links, ALICE, BOB).ok, false, "your own request is not yours to accept");
  assert.ok(accept(asked.links, BOB, ALICE).ok, "theirs is");
});

test("already friends is not a request", () => {
  let links = request([], ALICE, BOB).links;
  links = accept(links, BOB, ALICE).links;
  const again = request(links, ALICE, BOB);
  assert.equal(again.ok, false);
  assert.equal(again.note, "already friends");
});

test("removing works from either side and only once", () => {
  let links = request([], ALICE, BOB).links;
  links = accept(links, BOB, ALICE).links;

  const gone = remove(links, ALICE, BOB);
  assert.ok(gone.ok);
  assert.equal(gone.links.length, 0);
  assert.equal(remove(gone.links, BOB, ALICE).ok, false);
});

test("the list separates the three kinds", () => {
  let links = request([], ALICE, BOB).links;          // Alice asked Bob
  links = accept(links, BOB, ALICE).links;            // and they are friends
  links = request(links, CARL, ALICE).links;          // Carl asked Alice
  links = request(links, ALICE, "0000-d").links;      // Alice asked D

  const mine = listFor(links, ALICE);
  assert.deepEqual(mine.friends, [BOB]);
  assert.deepEqual(mine.incoming, [CARL]);
  assert.deepEqual(mine.outgoing, ["0000-d"]);
});

test("only friends may message", () => {
  let links = request([], ALICE, BOB).links;
  assert.equal(canMessage(links, ALICE, BOB), false, "a pending request is not a friendship");

  links = accept(links, BOB, ALICE).links;
  assert.ok(canMessage(links, ALICE, BOB));
  assert.ok(canMessage(links, BOB, ALICE));
  assert.equal(canMessage(links, ALICE, CARL), false);

  links = remove(links, BOB, ALICE).links;
  assert.equal(canMessage(links, ALICE, BOB), false, "and not after being removed");
});

test("a message cannot carry formatting or control characters", () => {
  assert.equal(cleanMessage("§cred"), "cred");
  assert.equal(cleanMessage("line\nbreak"), "linebreak");
  assert.equal(cleanMessage("  spaced  "), "spaced");
  assert.equal(cleanMessage("x".repeat(400)).length, 256);
  assert.equal(cleanMessage(null), "");
});

test("a name is checked before Mojang is asked", () => {
  assert.ok(looksLikeName("Finanzinstitut"));
  assert.equal(looksLikeName("no"), false);
  assert.equal(looksLikeName("way too long a name"), false);
  assert.equal(looksLikeName("semi;colon"), false);
  assert.equal(looksLikeName(undefined), false);
});
