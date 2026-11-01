/**
 * MicUp Signaling Server — Cloudflare Worker
 *
 * Uses the CORRECT Cloudflare Workers WebSocket pattern:
 * - server.addEventListener('message', ...) DOES work, but only
 *   inside the fetch handler while the request is still open
 * - The trick: we MUST keep a reference and the response must be
 *   returned immediately to "accept" the WebSocket
 * - Then event listeners on the server socket fire normally
 *
 * CRITICAL: This worker uses the "durable" pattern where we
 * immediately return the Response with the client WebSocket, and
 * the server WebSocket's event listeners handle all the logic.
 */

// Global Map of room code → Set of server-side WebScks
const rooms = new Map();

export default {
  async fetch(request) {
    const upgrade = request.headers.get("Upgrade");
    if (upgrade !== "websocket") {
      return new Response(JSON.stringify({
        name: "MicUp Signaling Server",
        status: "ok",
        usage: "Connect via WebSocket with ?room=ROOMCODE&peer=PEERID"
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
    const [client, server] = Object.values(new WebSocketPair());

    // Tag this socket.
    server.peerId = peer;
    server.room = room;

    // Add to room BEFORE accepting so other sockets can see us.
    sockets.add(server);

    // Accept the WebSocket — this must happen before returning.
    server.accept();

    // --- BROADCAST on message ---
    server.addEventListener("message", (event) => {
      // Forward to all OTHER sockets in the same room.
      for (const other of sockets) {
        if (other !== server && other.readyState === 1) {
          try {
            other.send(event.data);
          } catch (e) {
            // ignore — socket is broken
          }
        }
      }
    });

    // --- CLEANUP on close ---
    server.addEventListener("close", () => {
      sockets.delete(server);
      if (sockets.size === 0) {
        rooms.delete(room);
      }
    });

    server.addEventListener("error", () => {
      sockets.delete(server);
      if (sockets.size === 0) {
        rooms.delete(room);
      }
    });

    // Return the client socket — this "completes" the upgrade.
    return new Response(null, {
      status: 101,
      webSocket: client
    });
  }
};
