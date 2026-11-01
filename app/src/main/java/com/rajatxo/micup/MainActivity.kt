package com.rajatxo.micup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Step 4 — Signaling server connection test.
 *
 * Goal: prove our app can connect to the Cloudflare Worker signaling
 * server via WebSocket and exchange messages.
 *
 * What this screen does:
 * 1. Auto-generates a random room code (e.g., "TEST01")
 * 2. Connects to wss://micup-signal.chxsexo.workers.dev/?room=TEST01&peer=<random>
 * 3. Shows connection status: Connecting → Connected ✓ or Error
 * 4. Has a text input + Send button
 * 5. Displays messages received from the server
 *
 * The server broadcasts every message to all other sockets in the same
 * room. So if you send "hello" from one phone, it appears on the other
 * phone's screen (if both are at the same room).
 *
 * Test: open the app on 2 phones with the same room code → send a
 * message from one → it appears on the other.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            MaterialTheme {
                SignalingTestScreen()
            }
        }
    }
}

private const val SIGNALING_URL = "wss://micup-signal.chxsexo.workers.dev"

@Composable
private fun SignalingTestScreen() {
    // Generate a random 5-char room code on first launch.
    val roomCode = remember {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        (1..5).map { alphabet.random() }.joinToString("")
    }
    // Random peer ID so the server can tell us apart from other clients.
    val peerId = remember {
        (1..8).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
    }

    var connectionStatus by remember { mutableStateOf("Connecting...") }
    var isConnected by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val messages = remember { mutableStateListOf<String>() }
    var inputText by remember { mutableStateOf("") }

    // Hold the WebSocket in a remember so the Send button can use it.
    // We use a mutableStateOf so the reference can be set inside the
    // DisposableEffect and read by the click handler.
    var socketRef by remember { mutableStateOf<WebSocket?>(null) }

    // Build the WebSocket URL.
    val wsUrl = "$SIGNALING_URL?room=$roomCode&peer=$peerId"

    DisposableEffect(Unit) {
        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder().url(wsUrl).build()

        messages.add("→ Connecting to signaling server")
        messages.add("→ Room: $roomCode · Peer: $peerId")

        val socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                connectionStatus = "Connected ✓"
                isConnected = true
                errorMessage = null
                messages.add("✓ WebSocket open")
            }

            override fun onMessage(ws: WebSocket, text: String) {
                messages.add("← $text")
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(code, reason)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                connectionStatus = "Closed (code $code)"
                isConnected = false
                messages.add("✗ Closed: $code $reason")
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                connectionStatus = "FAILED ✗"
                isConnected = false
                errorMessage = "${t::class.java.simpleName}: ${t.message}"
                messages.add("✗ ERROR: ${t.message}")
            }
        })

        socketRef = socket

        onDispose {
            try { socket.close(1000, "client leaving") } catch (_: Throwable) {}
            try { client.dispatcher.executorService.shutdown() } catch (_: Throwable) {}
            try { client.connectionPool.evictAll() } catch (_: Throwable) {}
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .padding(16.dp)
        ) {
            // Header
            Text(
                text = "MicUp",
                color = Color(0xFFFF6B6B),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Step 4 of 10 — Signaling Test",
                color = Color(0xFF888888),
                fontSize = 12.sp
            )

            Spacer(Modifier.height(16.dp))

            // Room code + peer ID card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x1AFFFFFF))
                    .padding(16.dp)
            ) {
                Row {
                    Text("Room: ", color = Color(0xFF888888), fontSize = 12.sp)
                    Text(roomCode, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                Row {
                    Text("Peer: ", color = Color(0xFF888888), fontSize = 12.sp)
                    Text(peerId, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                when {
                                    isConnected -> Color(0xFF4ADE80)
                                    errorMessage != null -> Color(0xFFFF5252)
                                    else -> Color(0xFFF4B400)
                                }
                            )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(connectionStatus, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            errorMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    color = Color(0xFFFF5252),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x22FF5252))
                        .padding(12.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Messages log
            Text("Messages:", color = Color(0xFF888888), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x0FFFFFFF))
                    .padding(12.dp)
            ) {
                if (messages.isEmpty()) {
                    Text("(no messages yet)", color = Color(0xFF666666), fontSize = 12.sp, modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(messages) { msg ->
                            Text(
                                text = msg,
                                color = when {
                                    msg.startsWith("→") -> Color(0xFFB5B5B5)
                                    msg.startsWith("←") -> Color(0xFF4ADE80)
                                    msg.startsWith("✓") -> Color(0xFF4ADE80)
                                    msg.startsWith("✗") -> Color(0xFFFF5252)
                                    else -> Color.White
                                },
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Input + send button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x14FFFFFF))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (inputText.isEmpty()) {
                        Text("Type a message...", color = Color(0xFF666666), fontSize = 13.sp)
                    }
                    BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        singleLine = true,
                        cursorBrush = SolidColor(Color(0xFFFF6B6B)),
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                val canSend = isConnected && inputText.isNotBlank()
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (canSend) Color(0xFFFF6B6B) else Color(0x33FF6B6B))
                        .clickable(enabled = canSend) {
                            val msg = inputText.trim()
                            val payload = """{"type":"chat","from":"$peerId","text":"$msg"}"""
                            val sent = socketRef?.send(payload) ?: false
                            if (sent) {
                                messages.add("→ SENT: $msg")
                            } else {
                                messages.add("✗ Failed to send (socket not open)")
                            }
                            inputText = ""
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Send",
                        color = if (canSend) Color.Black else Color(0xFF666666),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
