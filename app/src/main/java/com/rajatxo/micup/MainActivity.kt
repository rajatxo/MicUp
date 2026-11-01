package com.rajatxo.micup

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.rajatxo.micup.audio.SignalingClient
import com.rajatxo.micup.audio.SignalingMessage
import com.rajatxo.micup.audio.WebRtcClient
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection

private const val SIGNALING_URL = "wss://micup-signal.chxsexo.workers.dev"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MaterialTheme {
                VoiceChatScreen()
            }
        }
    }
}

data class Participant(
    val peerId: String,
    val name: String,
    val iceState: String = "new"
)

@Composable
private fun VoiceChatScreen() {
    val context = LocalContext.current
    val appContext = context.applicationContext

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasMicPermission = granted }
    LaunchedEffect(Unit) {
        if (!hasMicPermission) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    var roomCode by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Enter a 5-char room code") }
    var isConnected by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val myPeerId = remember {
        (1..8).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
    }

    // Participant list (excluding self)
    val participants = remember { mutableStateOf<List<Participant>>(emptyList()) }

    var signaling by remember { mutableStateOf<SignalingClient?>(null) }
    var webRtc by remember { mutableStateOf<WebRtcClient?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try { com.rajatxo.micup.audio.VoiceService.stop(appContext) } catch (_: Throwable) {}
            try { webRtc?.release() } catch (_: Throwable) {}
            try { signaling?.disconnect() } catch (_: Throwable) {}
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("MicUp", color = Color(0xFFFF6B6B), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Step 10 — Multi-Peer Voice", color = Color(0xFF888888), fontSize = 12.sp)

            Spacer(Modifier.height(24.dp))

            if (!hasMicPermission) {
                Text("Need mic permission", color = Color(0xFFFF5252), fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFF6B6B))
                        .clickable { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text("Grant permission", color = Color.Black, fontWeight = FontWeight.SemiBold)
                }
            } else if (!isConnected && !isConnecting) {
                Text("Room Code", color = Color(0xFF888888), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x14FFFFFF))
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (roomCode.isEmpty()) {
                        Text("ABCDE", color = Color(0xFF666666), fontSize = 22.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, letterSpacing = 6.sp)
                    }
                    BasicTextField(
                        value = roomCode,
                        onValueChange = { roomCode = it.take(5).filter { c -> c.isLetterOrDigit() }.uppercase() },
                        singleLine = true,
                        cursorBrush = SolidColor(Color(0xFFFF6B6B)),
                        textStyle = TextStyle(
                            color = Color.White, fontSize = 22.sp,
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold,
                            letterSpacing = 6.sp
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            capitalization = KeyboardCapitalization.Characters
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(16.dp))

                val canConnect = roomCode.length == 5
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (canConnect) Color(0xFFFF6B6B) else Color(0x33FF6B6B))
                        .clickable(enabled = canConnect) {
                            isConnecting = true
                            errorMessage = null
                            status = "Connecting to signaling..."
                            participants.value = emptyList()

                            val rtc = WebRtcClient(appContext, myPeerId, object : WebRtcClient.Listener {
                                override fun onPeerIceStateChanged(peerId: String, state: PeerConnection.IceConnectionState) {
                                    val stateName = state.name
                                    participants.value = participants.value.map {
                                        if (it.peerId == peerId) it.copy(iceState = stateName) else it
                                    }
                                    val connectedCount = participants.value.count { it.iceState == "CONNECTED" || it.iceState == "COMPLETED" }
                                    status = when (state) {
                                        PeerConnection.IceConnectionState.CHECKING -> "Connecting to ${peerId.take(4)}..."
                                        PeerConnection.IceConnectionState.CONNECTED -> {
                                            if (connectedCount >= 1) com.rajatxo.micup.audio.VoiceService.start(appContext)
                                            "✓ Connected (${connectedCount + 1} in room)"
                                        }
                                        PeerConnection.IceConnectionState.COMPLETED -> {
                                            if (connectedCount >= 1) com.rajatxo.micup.audio.VoiceService.start(appContext)
                                            "✓ Connected (${connectedCount + 1} in room)"
                                        }
                                        PeerConnection.IceConnectionState.FAILED -> "Connection to ${peerId.take(4)} failed"
                                        PeerConnection.IceConnectionState.DISCONNECTED -> "${peerId.take(4)} disconnected"
                                        else -> stateName
                                    }
                                }
                                override fun onIceCandidateForPeer(peerId: String, candidate: IceCandidate) {
                                    signaling?.send(SignalingMessage.Ice(
                                        myPeerId, peerId,
                                        candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex
                                    ))
                                }
                                override fun onOfferReadyForPeer(peerId: String, sdp: String) {
                                    signaling?.send(SignalingMessage.Offer(myPeerId, peerId, sdp))
                                }
                                override fun onAnswerReadyForPeer(peerId: String, sdp: String) {
                                    signaling?.send(SignalingMessage.Answer(myPeerId, peerId, sdp))
                                }
                                override fun onPeerError(peerId: String, message: String) {
                                    errorMessage = "[$peerId] $message"
                                }
                            })

                            if (!rtc.init()) {
                                errorMessage = "WebRTC init failed"
                                status = "FAILED"
                                isConnecting = false
                                return@clickable
                            }
                            webRtc = rtc

                            val sig = SignalingClient(SIGNALING_URL, object : SignalingClient.Listener {
                                override fun onConnected() {
                                    status = "Waiting for peers..."
                                    isConnected = true
                                    isConnecting = false
                                    signaling?.send(SignalingMessage.Hello(myPeerId, "Player"))
                                }
                                override fun onMessage(message: SignalingMessage) {
                                    when (message) {
                                        is SignalingMessage.Hello -> {
                                            // New peer announced — add to participant list + create peer connection
                                            if (message.from != myPeerId && participants.value.none { it.peerId == message.from }) {
                                                participants.value = participants.value + Participant(message.from, message.name)
                                                val isNew = webRtc?.addPeer(message.from) ?: false
                                                if (isNew) {
                                                    webRtc?.maybeCreateOffer(message.from)
                                                }
                                            }
                                        }
                                        is SignalingMessage.Offer -> {
                                            // Make sure we have a peer connection for this peer
                                            if (participants.value.none { it.peerId == message.from }) {
                                                participants.value = participants.value + Participant(message.from, message.from.take(6))
                                                webRtc?.addPeer(message.from)
                                            }
                                            webRtc?.handleRemoteOffer(message.from, message.sdp)
                                        }
                                        is SignalingMessage.Answer -> {
                                            webRtc?.handleRemoteAnswer(message.from, message.sdp)
                                        }
                                        is SignalingMessage.Ice -> {
                                            val candidate = IceCandidate(message.sdpMid, message.sdpMLineIndex, message.sdp)
                                            webRtc?.addIceCandidate(message.from, candidate)
                                        }
                                        is SignalingMessage.Bye -> {
                                            webRtc?.removePeer(message.from)
                                            participants.value = participants.value.filterNot { it.peerId == message.from }
                                        }
                                    }
                                }
                                override fun onDisconnected(reason: String) {
                                    if (participants.value.none { it.iceState == "CONNECTED" || it.iceState == "COMPLETED" }) {
                                        status = "Disconnected: $reason"
                                        isConnected = false
                                        isConnecting = false
                                    }
                                }
                                override fun onError(error: String) {
                                    errorMessage = error
                                    status = "FAILED"
                                    isConnecting = false
                                }
                            })
                            sig.connect(roomCode, myPeerId)
                            signaling = sig
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Connect", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "All players enter the SAME room code.\nMax 4 players per room.\n~60 MB/hour per phone.",
                    color = Color(0xFF888888), fontSize = 11.sp, textAlign = TextAlign.Center
                )
            } else {
                // Connected view
                Text(status, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)

                errorMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Color(0xFFFF5252), fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x22FF5252))
                            .padding(8.dp))
                }

                Spacer(Modifier.height(20.dp))

                // Participant list
                Text("SQUAD (${participants.value.size + 1})", color = Color(0xFF888888), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Self
                    item {
                        ParticipantRow("You", "connected", true)
                    }
                    // Remote participants
                    items(participants.value) { p ->
                        ParticipantRow(p.name.ifEmpty { p.peerId.take(6) }, p.iceState, false)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Mute + Leave buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(if (isMuted) Color(0x22FF5252) else Color(0xFFFF6B6B))
                                .clickable {
                                    isMuted = !isMuted
                                    webRtc?.setMuted(isMuted)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (isMuted) "🔇" else "🎤", fontSize = 24.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(if (isMuted) "Muted" else "Live", color = Color(0xFF888888), fontSize = 11.sp)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0x22FF5252))
                                .clickable {
                                    com.rajatxo.micup.audio.VoiceService.stop(appContext)
                                    webRtc?.release()
                                    signaling?.disconnect()
                                    webRtc = null
                                    signaling = null
                                    isConnected = false
                                    isConnecting = false
                                    participants.value = emptyList()
                                    status = "Enter a 5-char room code"
                                    isMuted = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✕", color = Color(0xFFFF5252), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Leave", color = Color(0xFF888888), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ParticipantRow(name: String, iceState: String, isSelf: Boolean) {
    val stateColor = when (iceState.lowercase()) {
        "connected", "completed" -> Color(0xFF4ADE80)
        "checking" -> Color(0xFFF4B400)
        "failed", "disconnected" -> Color(0xFFFF5252)
        else -> Color(0xFF888888)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x1AFFFFFF))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(stateColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                name.firstOrNull()?.uppercase() ?: "?",
                color = stateColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (isSelf) "$name (You)" else name,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                if (isSelf) "Connected" else iceState,
                color = stateColor,
                fontSize = 11.sp
            )
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(stateColor)
        )
    }
}
