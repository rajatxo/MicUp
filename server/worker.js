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

// Five people total per room (you + 4 friends). A WebRTC mesh does not
// need an SFU at this size, and the free Cloudflare plan handles it.
const MAX_PEERS = 5;

// ===== Worker entry point — routes requests to the right Durable Object =====
export default {
  async fetch(request, env) {
    const upgrade = request.headers.get("Upgrade");
    if (upgrade !== "websocket") {
      return new Response(JSON.stringify({
        name: "MicUp Signaling Server",
        status: "ok",
        usage: "Connect via WebSocket with ?room=ROOMCODE&peer=PEERID",
        maxPeers: MAX_PEERS,
        protocol: "Durable Object routing — direct messages go to one peer"
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
    // peerId -> display name, so new joiners see real names.
    this.peerNames = new Map();
  }

  async fetch(request) {
    const url = new URL(request.url);
    const peer = url.searchParams.get("peer") || crypto.randomUUID();

    // Room cap: 5 people total.
    if (this.sessions.size >= MAX_PEERS) {
      const [client, server] = Object.values(new WebSocketPair());
      server.accept();
      server.send(JSON.stringify({
        type: "error",
        message: "Room is full (max 5 people)"
      }));
      server.close(4001, "room full");
      return new Response(null, { status: 101, webSocket: client });
    }

    // Create the WebSocket pair.
    const [client, server] = Object.values(new WebSocketPair());

    // Tag this socket with its peer ID.
    server.peerId = peer;

    // Add to sessions BEFORE accepting.
    this.sessions.add(server);

    // Accept the WebSocket.
    server.accept();

    // Tell the new joiner who is already here. Without this, the new peer
    // never learns about existing peers (only existing peers hear its
    // Hello), and the lower-ID side waits forever for an offer.
    const existing = [];
    for (const other of this.sessions) {
      if (other !== server) {
        existing.push({
          id: other.peerId,
          name: this.peerNames.get(other.peerId) || "Friend"
        });
      }
    }
    if (existing.length > 0) {
      server.send(JSON.stringify({ type: "peers", peers: existing }));
    }

    // --- ROUTE on message ---
    server.addEventListener("message", (event) => {
      let msg = null;
      try {
        msg = JSON.parse(event.data);
      } catch (e) {
        // Ignore malformed messages.
      }

      if (msg && msg.type === "hello") {
        this.peerNames.set(server.peerId, msg.name || "Friend");
      }

      // Direct messages (offer / answer / ice) carry a "to" field and go
      // only to that peer. Everything else (hello / bye) is broadcast.
      if (msg && msg.to) {
        const target = [...this.sessions].find((s) => s.peerId === msg.to);
        if (target && target.readyState === 1) {
          try {
            target.send(event.data);
          } catch (e) {
            // ignore broken socket
          }
        }
        return;
      }

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
    const cleanup = () => {
      // Only the first close/error event should broadcast a bye.
      if (!this.sessions.delete(server)) return;
      this.peerNames.delete(server.peerId);

      const bye = JSON.stringify({ type: "bye", from: server.peerId });
      for (const other of this.sessions) {
        if (other.readyState === 1) {
          try {
            other.send(bye);
          } catch (e) {
            // ignore broken socket
          }
        }
      }
    };

    server.addEventListener("close", cleanup);
    server.addEventListener("error", cleanup);

    // Return the client socket — completes the upgrade.
    return new Response(null, {
      status: 101,
      webSocket: client
    });
  }
}
