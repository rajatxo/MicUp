package com.rajatxo.micup.audio

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Thin wrapper over OkHttp's WebSocket — connects to the signaling
 * server and forwards every received message to [onMessage].
 *
 * The signaling server is a stateless "message board": every client
 * in a room receives every message broadcast by every other client.
 * MicUp's [RoomClient] then filters messages by sender ID and type.
 */
class SignalingClient(
    private val url: String,
    private val onOpen: () -> Unit,
    private val onMessage: (String) -> Unit,
    private val onClose: () -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "MicUp/Signaling"
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)  // disable read timeout for long-lived socket
        .pingInterval(20, TimeUnit.SECONDS)      // keep-alive
        .build()

    private var socket: WebSocket? = null

    fun connect() {
        val req = Request.Builder().url(url).build()
        socket = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket open")
                onOpen()
            }
            override fun onMessage(ws: WebSocket, text: String) {
                onMessage(text)
            }
            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(code, reason)
            }
            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $code $reason")
                onClose()
            }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure", t)
                onError(t.message ?: "network error")
            }
        })
    }

    fun send(msg: String): Boolean = socket?.send(msg) == true

    fun disconnect() {
        socket?.close(1000, "client leaving")
        socket = null
    }
}
