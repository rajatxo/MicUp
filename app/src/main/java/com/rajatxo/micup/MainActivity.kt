package com.rajatxo.micup

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
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
import com.rajatxo.micup.data.MicUpPrefs
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection

private const val SIGNALING_URL = "wss://micup-signal.chxsexo.workers.dev"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MaterialTheme {
                App()
            }
        }
    }
}

@Composable
private fun App() {
    var screen by remember { mutableStateOf("home") }
    when (screen) {
        "home" -> HomeScreen(onOpenSettings = { screen = "settings" })
        "settings" -> SettingsScreen(onBack = { screen = "home" })
    }
}

// ==================== HOME SCREEN ====================

@Composable
private fun HomeScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val prefs = remember { MicUpPrefs(context) }

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

    var playerName by remember { mutableStateOf(prefs.playerName) }
    var roomCode by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Enter a room code to start") }
    var isConnected by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val myPeerId = remember {
        (1..8).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
    }

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
            // Top bar: title + settings icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("MicUp", color = Color(0xFFFF6B6B), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("Voice rooms that never duck your game", color = Color(0xFF888888), fontSize = 11.sp)
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0x1AFFFFFF))
                        .clickable { onOpenSettings() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(32.dp))

            if (!hasMicPermission) {
                Text("Microphone permission needed", color = Color(0xFFFF5252), fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
                // Name input
                Text("Your Name", color = Color(0xFF888888), fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x14FFFFFF))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (playerName.isEmpty()) {
                        Text("Enter your name", color = Color(0xFF666666), fontSize = 16.sp)
                    }
                    BasicTextField(
                        value = playerName,
                        onValueChange = {
                            playerName = it.take(20)
                            prefs.playerName = it.take(20)
                        },
                        singleLine = true,
                        cursorBrush = SolidColor(Color(0xFFFF6B6B)),
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Room code input
                Text("Room Code", color = Color(0xFF888888), fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
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
                        textStyle = TextStyle(color = Color.White, fontSize = 22.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, letterSpacing = 6.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(24.dp))

                val canConnect = roomCode.length == 5 && playerName.isNotBlank()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (canConnect) Color(0xFFFF6B6B) else Color(0x33FF6B6B))
                        .clickable(enabled = canConnect) {
                            isConnecting = true
                            errorMessage = null
                            status = "Connecting..."
                            participants.value = emptyList()

                            val rtc = WebRtcClient(appContext, myPeerId, object : WebRtcClient.Listener {
                                override fun onPeerIceStateChanged(peerId: String, state: PeerConnection.IceConnectionState) {
                                    val stateName = state.name
                                    participants.value = participants.value.map {
                                        if (it.peerId == peerId) it.copy(iceState = stateName) else it
                                    }
                                    val connectedCount = participants.value.count { it.iceState == "CONNECTED" || it.iceState == "COMPLETED" }
                                    status = when (state) {
                                        PeerConnection.IceConnectionState.CHECKING -> "Connecting to friend..."
                                        PeerConnection.IceConnectionState.CONNECTED -> {
                                            if (connectedCount >= 1) com.rajatxo.micup.audio.VoiceService.start(appContext)
                                            "✓ Connected"
                                        }
                                        PeerConnection.IceConnectionState.COMPLETED -> {
                                            if (connectedCount >= 1) com.rajatxo.micup.audio.VoiceService.start(appContext)
                                            "✓ Connected"
                                        }
                                        PeerConnection.IceConnectionState.FAILED -> "Connection failed"
                                        PeerConnection.IceConnectionState.DISCONNECTED -> "Friend disconnected"
                                        else -> stateName
                                    }
                                }
                                override fun onIceCandidateForPeer(peerId: String, candidate: IceCandidate) {
                                    signaling?.send(SignalingMessage.Ice(myPeerId, peerId, candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex))
                                }
                                override fun onOfferReadyForPeer(peerId: String, sdp: String) {
                                    signaling?.send(SignalingMessage.Offer(myPeerId, peerId, sdp))
                                }
                                override fun onAnswerReadyForPeer(peerId: String, sdp: String) {
                                    signaling?.send(SignalingMessage.Answer(myPeerId, peerId, sdp))
                                }
                                override fun onPeerError(peerId: String, message: String) {
                                    errorMessage = message
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
                                    status = "Waiting for friends..."
                                    isConnected = true
                                    isConnecting = false
                                    signaling?.send(SignalingMessage.Hello(myPeerId, playerName))
                                }
                                override fun onMessage(message: SignalingMessage) {
                                    when (message) {
                                        is SignalingMessage.Hello -> {
                                            if (message.from != myPeerId && participants.value.none { it.peerId == message.from }) {
                                                val name = if (message.name.isNotBlank() && message.name != message.from) message.name else "Friend"
                                                participants.value = participants.value + Participant(message.from, name)
                                                val isNew = webRtc?.addPeer(message.from) ?: false
                                                if (isNew) webRtc?.maybeCreateOffer(message.from)
                                            }
                                        }
                                        is SignalingMessage.Offer -> {
                                            if (participants.value.none { it.peerId == message.from }) {
                                                val name = "Friend"
                                                participants.value = participants.value + Participant(message.from, name)
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
                    "Share the room code with your friends.\nMax 4 friends per room · ~60 MB/hour",
                    color = Color(0xFF888888), fontSize = 11.sp, textAlign = TextAlign.Center
                )

                errorMessage?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = Color(0xFFFF5252), fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x22FF5252)).padding(8.dp))
                }
            } else if (isConnecting) {
                // Connecting view
                Spacer(Modifier.height(40.dp))
                Text(status, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(Color(0x22F4B400)), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(Color(0xFFF4B400)))
                }
            } else {
                // Connected view
                Text(status, color = Color(0xFF4ADE80), fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))

                Text("FRIENDS (${participants.value.size + 1})", color = Color(0xFF888888), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { ParticipantRow(playerName.ifBlank { "You" }, "connected", true) }
                    items(participants.value) { p ->
                        ParticipantRow(p.name, p.iceState, false)
                    }
                }

                Spacer(Modifier.height(16.dp))

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
                                    status = "Enter a room code to start"
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

// ==================== SETTINGS SCREEN ====================

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp)
        ) {
            // Top bar with back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0x1AFFFFFF))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text("Settings", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(32.dp))

            // About card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x1AFFFFFF))
                    .padding(24.dp)
            ) {
                Text("MicUp", color = Color(0xFFFF6B6B), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Version 3.7.1", color = Color(0xFF888888), fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
                Text(
                    "Voice rooms that never duck your game sound. " +
                    "Built for gamers who want crisp squad comms " +
                    "alongside full-volume gameplay audio.",
                    color = Color(0xFFCCCCCC), fontSize = 13.sp, lineHeight = 18.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            // Developer card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x1AFFFFFF))
                    .padding(24.dp)
            ) {
                Text("Developer", color = Color(0xFF888888), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.developer_telegram)))
                            context.startActivity(intent)
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF6B6B).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Rajat", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text("@rajat_xo on Telegram", color = Color(0xFF888888), fontSize = 12.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("→", color = Color(0xFF888888), fontSize = 18.sp)
                }
            }

            Spacer(Modifier.weight(1f))

            // Footer
            Text("Made with love in India", color = Color(0xFF666666), fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}

// ==================== SHARED COMPONENTS ====================

data class Participant(
    val peerId: String,
    val name: String,
    val iceState: String = "new"
)

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
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x1AFFFFFF))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
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
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (isSelf) "$name (You)" else name,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                when {
                    isSelf -> "Connected"
                    iceState.lowercase() == "connected" || iceState.lowercase() == "completed" -> "Connected"
                    iceState.lowercase() == "checking" -> "Connecting..."
                    iceState.lowercase() == "failed" -> "Failed"
                    else -> "Waiting..."
                },
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
