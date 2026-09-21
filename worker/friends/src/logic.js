/*
 * Who is friends with whom, as plain functions over plain data.
 *
 * Nothing in this file knows about Cloudflare, D1 or HTTP. That is the point:
 * the rules are the part that is easy to get subtly wrong - two people asking
 * each other at the same moment, a request that arrives twice, a message to
 * somebody who unfriended you between the two calls - and rules that are pure
 * functions can be checked on a laptop in a millisecond.
 *
 * A link is stored once, not twice. The pair is sorted so that whoever asks,
 * the same row is found: friendship is symmetric and storing it twice is how
 * you end up friends in one direction.
 */

export const MAX_MESSAGE = 256;
export const MAX_NAME = 16;

/** The canonical order of a pair, so one row serves both directions. */
export function pairKey(a, b) {
  return a < b ? [a, b] : [b, a];
}

/** The link between two people, or null. */
export function findLink(links, a, b) {
  const [lo, hi] = pairKey(a, b);
  return links.find((l) => l.lo === lo && l.hi === hi) || null;
}

/**
 * State from one person's point of view.
 *
 * "incoming" and "outgoing" rather than one "pending", because the interface
 * has to show them in different places and a caller working that out from the
 * requester field is a caller that can get it backwards.
 */
export function stateFor(links, me, other) {
  const link = findLink(links, me, other);
  if (!link) return "none";
  if (link.state === "accepted") return "friends";
  return link.requester === me ? "outgoing" : "incoming";
}

/**
 * One person asks another.
 *
 * The case worth naming: if they have already asked you, asking back is an
 * acceptance. Anything else would leave two requests pointing at each other
 * and nobody friends.
 */
export function request(links, me, other) {
  if (me === other) {
    return { links, ok: false, note: "you cannot add yourself" };
  }

  const state = stateFor(links, me, other);
  if (state === "friends") return { links, ok: false, note: "already friends" };
  if (state === "outgoing") return { links, ok: false, note: "already asked" };

  if (state === "incoming") {
    return { ...accept(links, me, other), note: "they had already asked - you are friends now" };
  }

  const [lo, hi] = pairKey(me, other);
  return {
    links: [...links, { lo, hi, state: "pending", requester: me, since: 0 }],
    ok: true,
    note: "asked",
  };
}

/** Accepting is only possible when the other side asked. */
export function accept(links, me, other) {
  if (stateFor(links, me, other) !== "incoming") {
    return { links, ok: false, note: "nothing to accept" };
  }

  const [lo, hi] = pairKey(me, other);
  return {
    links: links.map((l) =>
      l.lo === lo && l.hi === hi ? { ...l, state: "accepted" } : l
    ),
    ok: true,
    note: "friends",
  };
}

/** Declining a request and removing a friend are the same row being dropped. */
export function remove(links, me, other) {
  const [lo, hi] = pairKey(me, other);
  const without = links.filter((l) => !(l.lo === lo && l.hi === hi));
  return {
    links: without,
    ok: without.length !== links.length,
    note: without.length !== links.length ? "removed" : "nothing to remove",
  };
}

/** Everything one person needs to draw their list. */
export function listFor(links, me) {
  const friends = [];
  const incoming = [];
  const outgoing = [];

  for (const link of links) {
    if (link.lo !== me && link.hi !== me) continue;
    const other = link.lo === me ? link.hi : link.lo;

    if (link.state === "accepted") friends.push(other);
    else if (link.requester === me) outgoing.push(other);
    else incoming.push(other);
  }
  return { friends, incoming, outgoing };
}

/**
 * Whether a message may be sent.
 *
 * Checked on the way in rather than on the way out. A message that is stored
 * and then withheld is still a message somebody can be made to store, and the
 * only thing worse than a stranger's message is a stranger's message that
 * counts against your quota.
 */
export function canMessage(links, me, other) {
  return stateFor(links, me, other) === "friends";
}

/** What a message is allowed to carry. */
export function cleanMessage(text) {
  if (typeof text !== "string") return "";
  // Control characters out, including the section sign the game reads as
  // formatting - a friend should not be able to colour or hide your chat
  const stripped = text.replace(/[\u0000-\u001f\u007f§]/g, "").trim();
  return stripped.slice(0, MAX_MESSAGE);
}

/** Whether a name could be a Minecraft name at all, before asking Mojang. */
export function looksLikeName(name) {
  return typeof name === "string" && /^[A-Za-z0-9_]{3,16}$/.test(name);
}
