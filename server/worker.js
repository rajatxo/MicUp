/**
 * MicUp Signaling Server — Cloudflare Worker
 *
 * SIMPLE BROADCAST MODEL:
 * - Each WebSocket connection joins a room (from URL ?room=CODE)
 * - When any client sends a message, it's broadcast to ALL other
 *   clients in the same room
 * - No state, no parsing, no auto-announce — just dumb forwarding
 *
 * The client handles all the logic (announcing, parsing, etc.).
 * The server is as simple as possible to avoid bugs.
 */

const rooms = new Map();

export default {
  async fetch(request) {
    const upgrade = request.headers.get("Upgrade");
    if (upgrade !== "websocket") {
      return new Response(JSON.stringify({
        name: "MicUp Signaling Server",
        status: "ok",
        usage: "Connect via WebSocket with ?room=ROOMCODE&peer=PEERID",
        protocol: "Broadcasts every message to all other sockets in the same room"
      }, null, 2), {
        status: 200,
        headers: { "Content-Type": "application/json" }
      });
    }

    const url = new URL(request.url);
    const room = (url.searchParams.get("room") || "default").toUpperCase();
    const peer = url.searchParams.get("peer") || crypto.randomUUID();

    // Get or create the room's socket set.
    let sockets = rooms.get(room);
    if (!sockets) {
      sockets = new Set();
      rooms.set(room, sockets);
    }

    // Create the WebSocket pair.
    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);

    // Tag this socket with its peer ID + room.
    server.peerId = peer;
    server.room = room;

    // Add to room.
    sockets.add(server);
    server.accept();

    console.log(`Peer ${peer} joined room ${room} (now ${sockets.size} sockets)`);

    // When this server socket receives a message, broadcast to ALL OTHER
    // sockets in the same room.
    server.addEventListener("message", (event) => {
      console.log(`Broadcast from ${peer}: ${event.data.substring(0, 100)}`);
      for (const other of sockets) {
        if (other !== server && other.readyState === 1) {
          try {
            other.send(event.data);
          } catch (e) {
            console.error("Broadcast error:", e);
          }
        }
      }
    });

    // When this socket closes, remove it from the room.
    server.addEventListener("close", () => {
      sockets.delete(server);
      console.log(`Peer ${peer} left room ${room} (${sockets.size} remaining)`);
      if (sockets.size === 0) {
        rooms.delete(room);
      }
    });

    server.addEventListener("error", (e) => {
      console.error(`Socket error from ${peer}:`, e);
      sockets.delete(server);
      if (sockets.size === 0) {
        rooms.delete(room);
      }
    });

    return new Response(null, { status: 101, webSocket: client });
  }
};
