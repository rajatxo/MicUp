package com.rajatxo.micup.audio

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * WebSocket client for the signaling server.
 *
 * Wraps OkHttp WebSocket and parses JSON messages into typed
 * SignalingMessage objects. Handles reconnection and cleanup.
 */
class SignalingClient(
    private val serverUrl: String,
    private val myPeerId: String,
    private val listener: Listener
) {
    interface Listener {
        fun onConnected()
        fun onMessage(message: SignalingMessage)
        fun onDisconnected(reason: String)
        fun onError(error: String)
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null

    fun connect(roomCode: String, peerId: String) {
        val url = "$serverUrl?room=$roomCode&peer=$peerId"
        Log.i(TAG, "Connecting to $url")

        val request = Request.Builder().url(url).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected")
                listener.onConnected()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val type = json.optString("type")
                    // Handle both "from" (our client) and "id" (server auto-announce)
                    val from = json.optString("from", json.optString("id", ""))
                    // "to" field — for multi-peer routing. If present, only
                    // process if it matches our peer ID. If absent, broadcast
                    // (old behavior, for backward compat).
                    val to = json.optString("to", "")
                    // Safety net: if an old server still broadcasts direct
                    // messages, ignore anything addressed to another peer.
                    if (to.isNotEmpty() && to != myPeerId) return
                    val message = when (type) {
                        "hello", "Hello" -> SignalingMessage.Hello(from, json.optString("name", from))
                        "offer", "Offer" -> SignalingMessage.Offer(from, to, json.optString("sdp", json.optString("description", "")))
                        "answer", "Answer" -> SignalingMessage.Answer(from, to, json.optString("sdp", json.optString("description", "")))
                        "ice", "Ice", "ice-candidate" -> {
                            val cand = json.optJSONObject("candidate")
                                ?: json.optJSONObject("iceCandidate")
                            SignalingMessage.Ice(
                                from,
                                to,
                                cand?.optString("sdp", cand.optString("candidate", "")) ?: "",
                                cand?.optString("sdpMid", cand.optString("sdpMid", null)),
                                cand?.optInt("sdpMLineIndex", 0) ?: 0
                            )
                        }
                        "peers", "Peers" -> {
                            val arr = json.optJSONArray("peers")
                            val peers = mutableListOf<SignalingMessage.PeerInfo>()
                            if (arr != null) {
                                for (i in 0 until arr.length()) {
                                    val p = arr.optJSONObject(i) ?: continue
                                    val id = p.optString("id", "")
                                    if (id.isNotEmpty()) {
                                        peers.add(SignalingMessage.PeerInfo(id, p.optString("name", "Friend")))
                                    }
                                }
                            }
                            SignalingMessage.Peers(peers)
                        }
                        "error", "Error" -> SignalingMessage.Error(json.optString("message", "Unknown error"))
                        "bye", "Bye" -> SignalingMessage.Bye(from)
                        else -> null
                }
                    if (message != null) {
                        listener.onMessage(message)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse message: $text", e)
                }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(code, reason)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $code $reason")
                if (code == 4001) {
                    // Server rejected the join because the room already has 5 people.
                    listener.onError("Room is full (max 5 people)")
                } else {
                    listener.onDisconnected("Closed: $reason")
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                // Don't immediately call onError — the WebSocket might have
                // closed normally but OkHttp reports it as a failure.
                // Let the listener decide what to do.
                val msg = t.message ?: "Unknown WebSocket error"
                if (msg.contains("closed") || msg.contains("1000")) {
                    // Normal close — treat as disconnection, not error
                    listener.onDisconnected("Connection closed")
                } else {
                    listener.onError(msg)
                }
            }
        })
    }

    fun send(message: SignalingMessage): Boolean {
        val json = message.toJson().toString()
        Log.i(TAG, "Sending: ${json.take(100)}")
        return socket?.send(json) ?: false
    }

    fun disconnect() {
        try { socket?.close(1000, "client leaving") } catch (_: Throwable) {}
        try { client.dispatcher.executorService.shutdown() } catch (_: Throwable) {}
        try { client.connectionPool.evictAll() } catch (_: Throwable) {}
        socket = null
    }

    companion object {
        private const val TAG = "MicUp/Signaling"
    }
}

/** Typed signaling messages exchanged between peers. */
sealed class SignalingMessage {
    abstract fun toJson(): JSONObject

    data class Hello(val from: String, val name: String) : SignalingMessage() {
        override fun toJson() = JSONObject().put("type", "hello").put("from", from).put("name", name)
    }

    data class Offer(val from: String, val to: String, val sdp: String) : SignalingMessage() {
        override fun toJson() = JSONObject().put("type", "offer").put("from", from).put("to", to).put("sdp", sdp)
    }

    data class Answer(val from: String, val to: String, val sdp: String) : SignalingMessage() {
        override fun toJson() = JSONObject().put("type", "answer").put("from", from).put("to", to).put("sdp", sdp)
    }

    data class Ice(
        val from: String,
        val to: String,
        val sdp: String,
        val sdpMid: String?,
        val sdpMLineIndex: Int
    ) : SignalingMessage() {
        override fun toJson(): JSONObject {
            val candidate = JSONObject()
                .put("sdp", sdp)
                .put("sdpMid", sdpMid ?: JSONObject.NULL)
                .put("sdpMLineIndex", sdpMLineIndex)
            return JSONObject()
                .put("type", "ice")
                .put("from", from)
                .put("to", to)
                .put("candidate", candidate)
        }
    }

    data class Bye(val from: String) : SignalingMessage() {
        override fun toJson() = JSONObject().put("type", "bye").put("from", from)
    }

    data class PeerInfo(val id: String, val name: String)

    /** Sent by the server to a new joiner so it learns who is already here. */
    data class Peers(val peers: List<PeerInfo>) : SignalingMessage() {
        override fun toJson() = JSONObject().put("type", "peers")
    }

    data class Error(val message: String) : SignalingMessage() {
        override fun toJson() = JSONObject().put("type", "error").put("message", message)
    }
}
