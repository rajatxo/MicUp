/**
 * MicUp Signaling Server — Cloudflare Worker
 *
 * A free, stateless WebSocket relay that MicUp clients use to exchange
 * WebRTC SDP offers/answers and ICE candidates.
 *
 * ## Deploy in 2 minutes (free)
 *
 * 1. Go to https://dash.cloudflare.com → Workers & Pages → Create
 * 2. Name it `micup-signal`, paste this entire file into the editor
 * 3. Click Deploy
 * 4. Copy the URL (looks like `wss://micup-signal.<your-subdomain>.workers.dev`)
 * 5. Open MicUp → Settings → Signaling Server, paste the URL
 *    (or set it as the default in `RoomClient.kt`)
 *
 * Free tier: 100k requests/day, 10ms CPU per request. Plenty for
 * a friend-group voice chat (each client uses ~1 req/sec while
 * signaling, then drops to P2P).
 *
 * ## How it works
 *
 * Each room is a separate WebSocket group keyed by the `room` query
 * param. Messages broadcast to every other socket in the same room.
 * Stateless — no DB, no auth, no presence. When the last socket in
 * a room closes, the room is GC'd automatically.
 */

const rooms = new Map();

export default {
  async fetch(request, env) {
    const upgrade = request.headers.get("Upgrade");
    if (upgrade !== "websocket") {
      return new Response(jsonHelp(), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }
    const url = new URL(request.url);
    const room = (url.searchParams.get("room") || "default").toUpperCase();
    const peer = url.searchParams.get("peer") || crypto.randomUUID();
    return handleWebSocket(request, room, peer);
  },
};

function handleWebSocket(request, room, peer) {
  const pair = new WebSocketPair();
  const [client, server] = Object.values(pair);

  let roomSockets = rooms.get(room);
  if (!roomSockets) {
    roomSockets = new Set();
    rooms.set(room, roomSockets);
  }

  // Track this socket so we can broadcast to others.
  server.peerId = peer;
  roomSockets.add(server);

  server.accept();

  // Tell the new peer who else is already in the room — they'll
  // initiate offers to each of them.
  for (const other of roomSockets) {
    if (other !== server && other.readyState === 1) {
      // Send the new peer's Hello to existing peers.
      other.send(JSON.stringify({
        type: "Hello",
        id: peer,
        name: peer,
        isHost: false
      }));
    }
  }

  server.addEventListener("message", (event) => {
    // Broadcast to every other socket in the same room.
    for (const other of roomSockets) {
      if (other !== server && other.readyState === 1) {
        other.send(event.data);
      }
    }
  });

  server.addEventListener("close", () => {
    roomSockets.delete(server);
    if (roomSockets.size === 0) rooms.delete(room);
    // Tell remaining peers this one left.
    for (const other of roomSockets) {
      if (other.readyState === 1) {
        other.send(JSON.stringify({ type: "Bye", id: peer }));
      }
    }
  });

  server.addEventListener("error", () => {
    roomSockets.delete(server);
    if (roomSockets.size === 0) rooms.delete(room);
  });

  return new Response(null, { status: 101, webSocket: client });
}

function jsonHelp() {
  return JSON.stringify({
    name: "MicUp Signaling Server",
    status: "ok",
    usage: "Connect via WebSocket with ?room=ROOMCODE&peer=PEERID",
    protocol: "Broadcasts every message to all other sockets in the same room",
    github: "https://github.com/rajatxo/MicUp"
  }, null, 2);
}
