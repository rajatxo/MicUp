/**
 * MicUp Signaling Server — Cloudflare Worker + Durable Object
 *
 * WHY DURABLE OBJECTS:
 * Plain Workers are stateless — the `rooms` Map doesn't persist between
 * requests because each request can hit a different isolate. So two
 * WebSocket connections to the "same" room might land in different
 * isolates that don't share state, and messages never get broadcast.
 *
 * Durable Objects solve this: each room is a single Durable Object
 * instance with persistent state. All WebSockets for a room connect
 * to the SAME object, so broadcasting works.
 *
 * Free tier: 100k Durable Object requests/day — plenty for a friend group.
 */

// ===== Worker entry point — routes requests to the right Durable Object =====
export default {
  async fetch(request, env) {
    const upgrade = request.headers.get("Upgrade");
    if (upgrade !== "websocket") {
      return new Response(JSON.stringify({
        name: "MicUp Signaling Server",
        status: "ok",
        usage: "Connect via WebSocket with ?room=ROOMCODE&peer=PEERID",
        protocol: "Durable Object broadcast — all sockets in same room see each other"
      }, null, 2), {
        status: 200,
        headers: { "Content-Type": "application/json" }
      });
    }

    const url = new URL(request.url);
    const room = (url.searchParams.get("room") || "default").toUpperCase();

    // Get the Durable Object stub for this room.
    // idFromName() ensures every request for the same room code
    // hits the SAME Durable Object instance.
    const id = env.ROOMS.idFromName(room);
    const stub = env.ROOMS.get(id);

    // Forward the WebSocket upgrade request to the Durable Object.
    return stub.fetch(request);
  }
};

// ===== Durable Object — one instance per room =====
export class RoomObject {
  constructor(state, env) {
    this.state = state;
    this.env = env;
    // Set of all WebSockets connected to this room.
    this.sessions = new Set();
  }

  async fetch(request) {
    const url = new URL(request.url);
    const peer = url.searchParams.get("peer") || crypto.randomUUID();

    // Create the WebSocket pair.
    const [client, server] = Object.values(new WebSocketPair());

    // Tag this socket with its peer ID.
    server.peerId = peer;

    // Add to sessions BEFORE accepting.
    this.sessions.add(server);

    // Accept the WebSocket.
    server.accept();

    // --- BROADCAST on message ---
    server.addEventListener("message", (event) => {
      // Forward to all OTHER sockets in this room.
      for (const other of this.sessions) {
        if (other !== server && other.readyState === 1) {
          try {
            other.send(event.data);
          } catch (e) {
            // ignore broken socket
          }
        }
      }
    });

    // --- CLEANUP on close ---
    server.addEventListener("close", () => {
      this.sessions.delete(server);
    });

    server.addEventListener("error", () => {
      this.sessions.delete(server);
    });

    // Return the client socket — completes the upgrade.
    return new Response(null, {
      status: 101,
      webSocket: client
    });
  }
}
