/*
 * Runs the worker on a local port, over Node's own SQLite and a stand-in for
 * Mojang.
 *
 * Two uses: the cross-language test drives the real Java client against it,
 * and anybody working on this can point a client at it without deploying.
 * Nothing here ships - Cloudflare provides all of it in production.
 */
import { createServer } from "node:http";
import worker from "../src/index.js";
import { makeDb } from "./d1.mjs";

const PLAYERS = [
  { name: "Alice_MC", id: "11111111111111111111111111111111" },
  { name: "Bob_MC", id: "22222222222222222222222222222222" },
];

// Mojang says yes to any handshake from a name on the list
globalThis.fetch = async (url) => {
  const text = String(url);
  const named = (name) => PLAYERS.find(
    (p) => p.name.toLowerCase() === String(name).toLowerCase());

  if (text.includes("hasJoined")) {
    const found = named(new URL(text).searchParams.get("username"));
    return found
      ? new Response(JSON.stringify({ id: found.id, name: found.name }), { status: 200 })
      : new Response(null, { status: 204 });
  }
  if (text.includes("users/profiles/minecraft/")) {
    const found = named(decodeURIComponent(text.split("/").pop()));
    return found
      ? new Response(JSON.stringify({ id: found.id, name: found.name }), { status: 200 })
      : new Response(null, { status: 404 });
  }
  return new Response(null, { status: 404 });
};

const env = { DB: makeDb(new URL("../schema.sql", import.meta.url).pathname) };

const server = createServer(async (incoming, outgoing) => {
  const chunks = [];
  for await (const chunk of incoming) chunks.push(chunk);

  const body = chunks.length ? Buffer.concat(chunks) : undefined;
  const request = new Request("http://local" + incoming.url, {
    method: incoming.method,
    headers: incoming.headers,
    body: incoming.method === "GET" || incoming.method === "HEAD" ? undefined : body,
  });

  try {
    const answer = await worker.fetch(request, env);
    outgoing.writeHead(answer.status, { "content-type": "application/json" });
    outgoing.end(await answer.text());
  } catch (error) {
    outgoing.writeHead(500);
    outgoing.end(JSON.stringify({ ok: false, note: String(error) }));
  }
});

const port = Number(process.argv[2] || 8799);
server.listen(port, "127.0.0.1", () => console.log("friends worker on " + port));
