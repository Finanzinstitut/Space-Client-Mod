/*
 * The friends worker.
 *
 * Standalone on purpose. The badge worker this client already talks to is not
 * in any repository here, so extending it would mean editing code I cannot
 * see. This one is its own deployment with its own database, and the only
 * thing it shares with the other is the handshake, which is Mojang's and not
 * anybody's to own.
 *
 * # Who you are
 *
 * The game proves who it is to Mojang, the worker asks Mojang whether that
 * happened, and only then does a token come back. The access token never
 * leaves the player's machine, and this worker never sees it. The same shape
 * as the badge worker's /np/session, because it is the only shape that lets a
 * client prove a name without handing over a secret.
 *
 * # Instant, and what that costs
 *
 * Messages are delivered by long poll: the client asks for anything newer than
 * what it has, and this holds the request open for up to twenty five seconds
 * rather than answering "nothing" straight away. A message therefore arrives
 * within about a second of being sent, at the cost of roughly three requests a
 * minute per idle client instead of thirty.
 *
 * True push would be a WebSocket on a Durable Object, which is a paid plan.
 * The interval below is the one number to change if that day comes.
 */

import {
  MAX_GROUP, MAX_NAME, accept, canMessage, cleanGroupName, cleanMessage, invitable,
  looksLikeName, pairKey, request, stateFor,
} from "./logic.js";

/** How long a poll waits before giving up and answering "nothing yet". */
const POLL_HOLD_MS = 25_000;

/** How often it looks while waiting. Every look is a query, so not too often. */
const POLL_STEP_MS = 900;

/** How long a session token is good for. */
const TOKEN_SECONDS = 3600;

/** Messages older than this are swept away. */
const KEEP_DAYS = 14;

const json = (body, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });

const fail = (note, status = 400) => json({ ok: false, note }, status);

export default {
  async fetch(request_, env) {
    const url = new URL(request_.url);
    const path = url.pathname.replace(/\/+$/, "") || "/";

    try {
      if (path === "/" ) return json({ ok: true, service: "space-client-friends" });
      if (path === "/session") return await session(request_, env);

      // Everything past here needs a token
      const who = await whoIs(request_, env);
      if (!who) return fail("not signed in", 401);

      switch (path) {
        case "/friends": return await listFriends(env, who);
        case "/friends/request": return await sendRequest(request_, env, who);
        case "/friends/accept": return await acceptRequest(request_, env, who);
        case "/friends/remove": return await removeFriend(request_, env, who);
        case "/messages": return await sendMessage(request_, env, who);
        case "/poll": return await poll(request_, env, who, url);
        case "/groups/create": return await createGroup(request_, env, who);
        case "/groups/invite": return await inviteToGroup(request_, env, who);
        case "/groups/leave": return await leaveGroup(request_, env, who);
        case "/groups/message": return await sendGroupMessage(request_, env, who);
        default: return fail("no such thing here", 404);
      }
    } catch (error) {
      // Said out loud rather than swallowed: a friends list that is quietly
      // empty is indistinguishable from one that is genuinely empty
      return fail("worker error: " + (error && error.message), 500);
    }
  },
};

// ---------------------------------------------------------------- identity

/**
 * Turns a finished Mojang handshake into a token.
 *
 * The client calls Mojang's joinServer with a random id, then calls this with
 * the same id. Mojang is the one that says whether the two match, so this
 * worker never has to hold anything secret.
 */
async function session(request_, env) {
  const name = request_.headers.get("X-Space-Name") || "";
  const serverId = request_.headers.get("X-Space-Server") || "";

  if (!looksLikeName(name)) return fail("that is not a name");
  if (!/^[0-9a-f]{1,64}$/i.test(serverId)) return fail("that is not a handshake");

  const verified = await hasJoined(name, serverId);
  if (!verified) return fail("Mojang did not confirm that handshake", 403);

  const token = crypto.randomUUID().replace(/-/g, "") + crypto.randomUUID().replace(/-/g, "");
  const expires = Date.now() + TOKEN_SECONDS * 1000;

  await env.DB.batch([
    env.DB.prepare(
      "INSERT INTO players (uuid, name, seen) VALUES (?, ?, ?) " +
      "ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, seen = excluded.seen"
    ).bind(verified.uuid, verified.name, Date.now()),
    env.DB.prepare("INSERT INTO sessions (token, uuid, expires) VALUES (?, ?, ?)")
      .bind(token, verified.uuid, expires),
    env.DB.prepare("DELETE FROM sessions WHERE expires < ?").bind(Date.now()),
  ]);

  return json({ ok: true, token, uuid: verified.uuid, name: verified.name,
                expiresIn: TOKEN_SECONDS });
}

async function hasJoined(name, serverId) {
  const url = "https://sessionserver.mojang.com/session/minecraft/hasJoined"
    + `?username=${encodeURIComponent(name)}&serverId=${encodeURIComponent(serverId)}`;

  const answer = await fetch(url);
  if (answer.status !== 200) return null;

  const body = await answer.json();
  if (!body || !body.id || !body.name) return null;
  return { uuid: dashed(body.id), name: body.name };
}

/** Mojang answers without dashes; everything else in the game uses them. */
function dashed(id) {
  if (id.includes("-")) return id;
  return [id.slice(0, 8), id.slice(8, 12), id.slice(12, 16), id.slice(16, 20), id.slice(20)]
    .join("-");
}

async function whoIs(request_, env) {
  const header = request_.headers.get("Authorization") || "";
  const token = header.startsWith("Bearer ") ? header.slice(7) : "";
  if (!token) return null;

  const row = await env.DB
    .prepare("SELECT s.uuid, p.name FROM sessions s JOIN players p ON p.uuid = s.uuid " +
             "WHERE s.token = ? AND s.expires > ?")
    .bind(token, Date.now())
    .first();

  return row ? { uuid: row.uuid, name: row.name } : null;
}

// ---------------------------------------------------------------- friends

/** Everything the list screen draws, in one call. */
async function listFriends(env, who) {
  const { results } = await env.DB
    .prepare("SELECT lo, hi, state, requester FROM links WHERE lo = ? OR hi = ?")
    .bind(who.uuid, who.uuid)
    .all();

  const links = results || [];
  const others = links.map((l) => (l.lo === who.uuid ? l.hi : l.lo));
  const names = await namesOf(env, others);

  const entry = (uuid, state) => ({
    uuid, name: names.get(uuid) || "?", state,
    online: false,
  });

  const friends = [], incoming = [], outgoing = [];
  for (const link of links) {
    const other = link.lo === who.uuid ? link.hi : link.lo;
    if (link.state === "accepted") friends.push(entry(other, "friends"));
    else if (link.requester === who.uuid) outgoing.push(entry(other, "outgoing"));
    else incoming.push(entry(other, "incoming"));
  }

  const groups = await groupsOf(env, who.uuid);
  // Where this person's feed stands, so the client's first poll can wait on
  // it. Without it the first poll knew no version, could not tell that
  // anything changed, and a request accepted in its first twenty five seconds
  // only showed up after them.
  const feed = await env.DB.prepare("SELECT version FROM feeds WHERE uuid = ?").bind(who.uuid).first();
  const version = feed ? feed.version : 0;
  return json({ ok: true, me: who, friends, incoming, outgoing, groups, version });
}

async function namesOf(env, uuids) {
  const found = new Map();
  if (uuids.length === 0) return found;

  const marks = uuids.map(() => "?").join(",");
  const { results } = await env.DB
    .prepare(`SELECT uuid, name FROM players WHERE uuid IN (${marks})`)
    .bind(...uuids)
    .all();

  for (const row of results || []) found.set(row.uuid, row.name);
  return found;
}

async function sendRequest(request_, env, who) {
  const body = await readJson(request_);
  const name = (body.name || "").trim();
  if (!looksLikeName(name)) return fail("that is not a Minecraft name");

  const other = await lookupByName(env, name);
  if (!other) return fail("no player by that name");

  const link = await linkBetween(env, who.uuid, other.uuid);
  const decided = request(link ? [link] : [], who.uuid, other.uuid);
  if (!decided.ok) return fail(decided.note);

  const [lo, hi] = pairKey(who.uuid, other.uuid);
  const fresh = decided.links[0];

  await env.DB.batch([
    env.DB.prepare(
      "INSERT INTO links (lo, hi, state, requester, since) VALUES (?, ?, ?, ?, ?) " +
      "ON CONFLICT(lo, hi) DO UPDATE SET state = excluded.state"
    ).bind(lo, hi, fresh.state, fresh.requester, Date.now()),
    bump(env, other.uuid),
    bump(env, who.uuid),
  ]);

  return json({ ok: true, note: decided.note, friend: { uuid: other.uuid, name: other.name } });
}

async function acceptRequest(request_, env, who) {
  const body = await readJson(request_);
  const other = String(body.uuid || "");
  const link = await linkBetween(env, who.uuid, other);

  const decided = accept(link ? [link] : [], who.uuid, other);
  if (!decided.ok) return fail(decided.note);

  const [lo, hi] = pairKey(who.uuid, other);
  await env.DB.batch([
    env.DB.prepare("UPDATE links SET state = 'accepted' WHERE lo = ? AND hi = ?").bind(lo, hi),
    bump(env, other),
    bump(env, who.uuid),
  ]);

  return json({ ok: true, note: "friends" });
}

async function removeFriend(request_, env, who) {
  const body = await readJson(request_);
  const other = String(body.uuid || "");
  const [lo, hi] = pairKey(who.uuid, other);

  await env.DB.batch([
    env.DB.prepare("DELETE FROM links WHERE lo = ? AND hi = ?").bind(lo, hi),
    bump(env, other),
    bump(env, who.uuid),
  ]);

  return json({ ok: true, note: "removed" });
}

async function linkBetween(env, a, b) {
  const [lo, hi] = pairKey(a, b);
  return await env.DB
    .prepare("SELECT lo, hi, state, requester FROM links WHERE lo = ? AND hi = ?")
    .bind(lo, hi)
    .first();
}

async function lookupByName(env, name) {
  // The local record first: somebody who has used this client is already here,
  // and asking Mojang for a name it just told us is a call for nothing
  const known = await env.DB
    .prepare("SELECT uuid, name FROM players WHERE name = ? COLLATE NOCASE")
    .bind(name)
    .first();
  if (known) return known;

  // Two of Mojang's addresses for the same answer. The first is the classic
  // one and rate limits Cloudflare's shared addresses hard; a refusal there
  // came back as "no player by that name" for names that very much exist.
  const body = await profileByName(name);
  if (!body) return null;

  const found = { uuid: dashed(body.id), name: body.name };
  await env.DB
    .prepare("INSERT INTO players (uuid, name, seen) VALUES (?, ?, 0) " +
             "ON CONFLICT(uuid) DO UPDATE SET name = excluded.name")
    .bind(found.uuid, found.name)
    .run();
  return found;
}

async function profileByName(name) {
  const addresses = [
    "https://api.mojang.com/users/profiles/minecraft/",
    "https://api.minecraftservices.com/minecraft/profile/lookup/name/",
  ];
  for (const base of addresses) {
    try {
      const answer = await fetch(base + encodeURIComponent(name));
      if (answer.status === 200) {
        const body = await answer.json();
        if (body && body.id) return body;
      }
      // 404 and 204 mean nobody has the name; asking the other address would
      // only say the same. Anything else - 429, 403, 5xx - is worth a second try.
      if (answer.status === 404 || answer.status === 204) return null;
    } catch {
      // Next address
    }
  }
  return null;
}

/** Marks somebody's feed as changed, so their poll wakes up. */
function bump(env, uuid) {
  return env.DB
    .prepare("INSERT INTO feeds (uuid, version) VALUES (?, 1) " +
             "ON CONFLICT(uuid) DO UPDATE SET version = version + 1")
    .bind(uuid);
}

// ---------------------------------------------------------------- messages

async function sendMessage(request_, env, who) {
  const body = await readJson(request_);
  const to = String(body.to || "");
  const text = cleanMessage(body.text);

  if (!text) return fail("nothing to send");

  const link = await linkBetween(env, who.uuid, to);
  if (!canMessage(link ? [link] : [], who.uuid, to)) {
    return fail("you can only write to friends");
  }

  const now = Date.now();
  await env.DB.batch([
    env.DB.prepare("INSERT INTO messages (sender, recipient, body, sent) VALUES (?, ?, ?, ?)")
      .bind(who.uuid, to, text, now),
    env.DB.prepare("DELETE FROM messages WHERE sent < ?")
      .bind(now - KEEP_DAYS * 86_400_000),
    bump(env, to),
    bump(env, who.uuid),
  ]);

  return json({ ok: true, sent: now });
}

/**
 * Waits for something to happen, rather than answering "nothing" at once.
 *
 * The client passes the highest message id it has seen. Anything newer comes
 * back immediately; if there is nothing, this looks again every so often until
 * the hold runs out. That is what makes a message land in about a second
 * without asking sixty times a minute.
 */
async function poll(request_, env, who, url) {
  const since = Number(url.searchParams.get("since") || 0) || 0;
  const gsince = Number(url.searchParams.get("gsince") || 0) || 0;
  const knownVersion = Number(url.searchParams.get("version") || -1);
  const until = Date.now() + POLL_HOLD_MS;

  for (;;) {
    const { results } = await env.DB
      .prepare("SELECT id, sender, recipient, body, sent FROM messages " +
               "WHERE (recipient = ? OR sender = ?) AND id > ? ORDER BY id LIMIT 50")
      .bind(who.uuid, who.uuid, since)
      .all();

    const feed = await env.DB
      .prepare("SELECT version FROM feeds WHERE uuid = ?")
      .bind(who.uuid)
      .first();
    const version = feed ? feed.version : 0;

    // Only from groups this person is in, and only what was said since they
    // joined: being added to a group is not a key to its past
    const grouped = await env.DB
      .prepare("SELECT m.id, m.group_id, m.sender, m.body, m.sent FROM group_messages m " +
               "JOIN group_members g ON g.group_id = m.group_id AND g.uuid = ? " +
               "WHERE m.id > ? AND m.sent >= g.joined ORDER BY m.id LIMIT 50")
      .bind(who.uuid, gsince)
      .all();
    const groupLines = grouped.results || [];

    const messages = results || [];
    const changed = knownVersion >= 0 && version !== knownVersion;

    if (messages.length > 0 || groupLines.length > 0 || changed || Date.now() >= until) {
      const names = await namesOf(env, [
        ...new Set([
          ...messages.flatMap((m) => [m.sender, m.recipient]),
          ...groupLines.map((m) => m.sender),
        ]),
      ]);

      return json({
        ok: true,
        version,
        cursor: messages.length ? messages[messages.length - 1].id : since,
        messages: messages.map((m) => ({
          id: m.id,
          from: m.sender,
          fromName: names.get(m.sender) || "?",
          to: m.recipient,
          text: m.body,
          sent: m.sent,
          mine: m.sender === who.uuid,
        })),
        gcursor: groupLines.length ? groupLines[groupLines.length - 1].id : gsince,
        groupMessages: groupLines.map((m) => ({
          id: m.id,
          group: m.group_id,
          from: m.sender,
          fromName: names.get(m.sender) || "?",
          text: m.body,
          sent: m.sent,
          mine: m.sender === who.uuid,
        })),
      });
    }

    await sleep(POLL_STEP_MS);
  }
}

// ---------------------------------------------------------------- groups

/** Every group this person is in, with its members by name. */
async function groupsOf(env, uuid) {
  const { results } = await env.DB
    .prepare("SELECT g.id, g.name, g.owner FROM chat_groups g " +
             "JOIN group_members m ON m.group_id = g.id WHERE m.uuid = ? ORDER BY g.id")
    .bind(uuid)
    .all();
  const groups = results || [];
  if (groups.length === 0) return [];

  const marks = groups.map(() => "?").join(",");
  const members = await env.DB
    .prepare(`SELECT m.group_id, m.uuid, p.name FROM group_members m ` +
             `LEFT JOIN players p ON p.uuid = m.uuid WHERE m.group_id IN (${marks}) ` +
             `ORDER BY m.joined`)
    .bind(...groups.map((g) => g.id))
    .all();

  return groups.map((g) => ({
    id: g.id,
    name: g.name,
    owner: g.owner,
    members: (members.results || [])
      .filter((m) => m.group_id === g.id)
      .map((m) => ({ uuid: m.uuid, name: m.name || "?" })),
  }));
}

async function linksOf(env, uuid) {
  const { results } = await env.DB
    .prepare("SELECT lo, hi, state, requester FROM links WHERE lo = ? OR hi = ?")
    .bind(uuid, uuid)
    .all();
  return results || [];
}

async function membersOf(env, groupId) {
  const { results } = await env.DB
    .prepare("SELECT uuid FROM group_members WHERE group_id = ?")
    .bind(groupId)
    .all();
  return (results || []).map((r) => r.uuid);
}

async function createGroup(request_, env, who) {
  const body = await readJson(request_);
  const name = cleanGroupName(body.name);
  if (!name) return fail("the group needs a name");

  const links = await linksOf(env, who.uuid);
  const people = invitable(links, who.uuid, body.members);
  if (people.length === 0) return fail("pick at least one friend for the group");
  if (people.length + 1 > MAX_GROUP) return fail(`a group holds at most ${MAX_GROUP} people`);

  const now = Date.now();
  const created = await env.DB
    .prepare("INSERT INTO chat_groups (name, owner, created) VALUES (?, ?, ?) RETURNING id")
    .bind(name, who.uuid, now)
    .first();
  const id = created.id;

  await env.DB.batch([
    ...[who.uuid, ...people].map((uuid) =>
      env.DB.prepare("INSERT INTO group_members (group_id, uuid, joined) VALUES (?, ?, ?)")
        .bind(id, uuid, now)),
    ...[who.uuid, ...people].map((uuid) => bump(env, uuid)),
  ]);

  return json({ ok: true, note: "group created", group: { id, name } });
}

async function inviteToGroup(request_, env, who) {
  const body = await readJson(request_);
  const groupId = Number(body.group) || 0;
  const members = await membersOf(env, groupId);
  if (!members.includes(who.uuid)) return fail("you are not in that group", 403);

  const links = await linksOf(env, who.uuid);
  const people = invitable(links, who.uuid, body.members, members);
  if (people.length === 0) return fail("nobody to add - only your friends who are not in it yet");
  if (members.length + people.length > MAX_GROUP) {
    return fail(`a group holds at most ${MAX_GROUP} people`);
  }

  const now = Date.now();
  await env.DB.batch([
    ...people.map((uuid) =>
      env.DB.prepare("INSERT OR IGNORE INTO group_members (group_id, uuid, joined) VALUES (?, ?, ?)")
        .bind(groupId, uuid, now)),
    ...[...members, ...people].map((uuid) => bump(env, uuid)),
  ]);

  return json({ ok: true, note: people.length === 1 ? "added 1 person" : `added ${people.length} people` });
}

async function leaveGroup(request_, env, who) {
  const body = await readJson(request_);
  const groupId = Number(body.group) || 0;
  const members = await membersOf(env, groupId);
  if (!members.includes(who.uuid)) return fail("you are not in that group", 403);

  const rest = members.filter((m) => m !== who.uuid);
  const statements = [
    env.DB.prepare("DELETE FROM group_members WHERE group_id = ? AND uuid = ?").bind(groupId, who.uuid),
    ...members.map((uuid) => bump(env, uuid)),
  ];
  // The last one out takes the group and what was said in it along
  if (rest.length === 0) {
    statements.push(
      env.DB.prepare("DELETE FROM group_messages WHERE group_id = ?").bind(groupId),
      env.DB.prepare("DELETE FROM chat_groups WHERE id = ?").bind(groupId));
  }
  await env.DB.batch(statements);
  return json({ ok: true, note: "left the group" });
}

async function sendGroupMessage(request_, env, who) {
  const body = await readJson(request_);
  const groupId = Number(body.group) || 0;
  const text = cleanMessage(body.text);
  if (!text) return fail("nothing to send");

  const members = await membersOf(env, groupId);
  if (!members.includes(who.uuid)) return fail("you are not in that group", 403);

  const now = Date.now();
  await env.DB.batch([
    env.DB.prepare("INSERT INTO group_messages (group_id, sender, body, sent) VALUES (?, ?, ?, ?)")
      .bind(groupId, who.uuid, text, now),
    env.DB.prepare("DELETE FROM group_messages WHERE sent < ?").bind(now - KEEP_DAYS * 86_400_000),
    ...members.map((uuid) => bump(env, uuid)),
  ]);
  return json({ ok: true, sent: now });
}

const sleep = (ms) => new Promise((done) => setTimeout(done, ms));

async function readJson(request_) {
  try {
    return (await request_.json()) || {};
  } catch {
    return {};
  }
}

export { stateFor, MAX_NAME };
