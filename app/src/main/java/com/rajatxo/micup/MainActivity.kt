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
import org.webrtc.SessionDescription

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

@Composable
private fun VoiceChatScreen() {
    val context = LocalContext.current
    val appContext = context.applicationContext

    // Permissions
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

    // State
    var roomCode by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Enter a 5-char room code") }
    var isConnected by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var iceState by remember { mutableStateOf("") }
    var isMuted by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Peer ID — random per session
    val myPeerId = remember {
        (1..8).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
    }
    var remotePeerId by remember { mutableStateOf<String?>(null) }

    // Holders for cleanup
    var signaling by remember { mutableStateOf<SignalingClient?>(null) }
    var webRtc by remember { mutableStateOf<WebRtcClient?>(null) }
    var hasCreatedOffer by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
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
            // Header
            Text("MicUp", color = Color(0xFFFF6B6B), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Step 6+7 — Two Phone Voice Test", color = Color(0xFF888888), fontSize = 12.sp)

            Spacer(Modifier.height(32.dp))

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
                // Room code input + Connect button
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
                            hasCreatedOffer = false

                            // Create WebRTC client
                            val rtc = WebRtcClient(appContext, object : WebRtcClient.Listener {
                                override fun onIceConnectionStateChange(state: PeerConnection.IceConnectionState) {
                                    iceState = state.name
                                    status = when (state) {
                                        PeerConnection.IceConnectionState.CHECKING -> "Connecting..."
                                        PeerConnection.IceConnectionState.CONNECTED -> "✓ CONNECTED — speak now!"
                                        PeerConnection.IceConnectionState.COMPLETED -> "✓ CONNECTED — speak now!"
                                        PeerConnection.IceConnectionState.FAILED -> "Connection failed"
                                        PeerConnection.IceConnectionState.DISCONNECTED -> "Disconnected"
                                        else -> state.name
                                    }
                                }
                                override fun onIceCandidate(candidate: IceCandidate) {
                                    signaling?.send(SignalingMessage.Ice(
                                        myPeerId,
                                        candidate.sdp,
                                        candidate.sdpMid,
                                        candidate.sdpMLineIndex
                                    ))
                                }
                                override fun onOfferReady(sdp: String) {
                                    signaling?.send(SignalingMessage.Offer(myPeerId, sdp))
                                }
                                override fun onAnswerReady(sdp: String) {
                                    signaling?.send(SignalingMessage.Answer(myPeerId, sdp))
                                }
                                override fun onError(message: String) {
                                    errorMessage = message
                                    status = "FAILED"
                                    isConnecting = false
                                }
                            })

                            if (!rtc.init()) {
                                errorMessage = "WebRTC init failed"
                                status = "FAILED"
                                isConnecting = false
                                return@clickable
                            }
                            webRtc = rtc

                            // Create signaling client
                            val sig = SignalingClient(SIGNALING_URL, object : SignalingClient.Listener {
                                override fun onConnected() {
                                    status = "Waiting for peer..."
                                    isConnected = true
                                    isConnecting = false
                                    // Announce ourselves
                                    signaling?.send(SignalingMessage.Hello(myPeerId, "Player"))
                                }
                                override fun onMessage(message: SignalingMessage) {
                                    when (message) {
                                        is SignalingMessage.Hello -> {
                                            remotePeerId = message.from
                                            // Tie-break: lower peer ID creates the offer
                                            if (myPeerId < message.from && !hasCreatedOffer) {
                                                hasCreatedOffer = true
                                                status = "Creating offer..."
                                                webRtc?.createOffer()
                                            }
                                        }
                                        is SignalingMessage.Offer -> {
                                            remotePeerId = message.from
                                            status = "Received offer, creating answer..."
                                            webRtc?.setRemoteDescription(SessionDescription.Type.OFFER, message.sdp)
                                            webRtc?.createAnswer()
                                        }
                                        is SignalingMessage.Answer -> {
                                            status = "Received answer, connecting..."
                                            webRtc?.setRemoteDescription(SessionDescription.Type.ANSWER, message.sdp)
                                        }
                                        is SignalingMessage.Ice -> {
                                            val candidate = IceCandidate(message.sdpMid, message.sdpMLineIndex, message.sdp)
                                            webRtc?.addIceCandidate(candidate)
                                        }
                                        is SignalingMessage.Bye -> {
                                            status = "Peer left"
                                            iceState = "DISCONNECTED"
                                        }
                                    }
                                }
                                override fun onDisconnected(reason: String) {
                                    // Don't immediately fail — show the reason
                                    // and let the user retry
                                    if (iceState != "CONNECTED" && iceState != "COMPLETED") {
                                        status = "Disconnected: $reason\nTap Retry to reconnect"
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
                    "Both phones must enter the SAME room code.\nShare the code with your friend.",
                    color = Color(0xFF888888), fontSize = 12.sp, textAlign = TextAlign.Center
                )
            } else {
                // Connected / connecting view
                Text(status, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)

                if (iceState.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("ICE: $iceState", color = Color(0xFF888888), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }

                errorMessage?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = Color(0xFFFF5252), fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x22FF5252))
                            .padding(12.dp))
                }

                Spacer(Modifier.height(32.dp))

                // ICE state visual indicator
                val stateColor = when (iceState) {
                    "CONNECTED", "COMPLETED" -> Color(0xFF4ADE80)
                    "CHECKING" -> Color(0xFFF4B400)
                    "FAILED", "DISCONNECTED" -> Color(0xFFFF5252)
                    else -> Color(0xFF888888)
                }
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(stateColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(stateColor)
                    )
                }

                Spacer(Modifier.height(32.dp))

                // Retry button (shown when disconnected/failed)
                if (status.startsWith("Disconnected") || status == "FAILED") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFF6B6B))
                            .clickable {
                                // Full reconnect — release old, start fresh
                                try { webRtc?.release() } catch (_: Throwable) {}
                                try { signaling?.disconnect() } catch (_: Throwable) {}
                                webRtc = null
                                signaling = null
                                isConnected = false
                                isConnecting = false
                                iceState = ""
                                errorMessage = null
                                hasCreatedOffer = false
                                status = "Enter a 5-char room code"
                            }
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Retry", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Mute + Leave buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Mute
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

                    // Leave
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0x22FF5252))
                                .clickable {
                                    webRtc?.release()
                                    signaling?.disconnect()
                                    webRtc = null
                                    signaling = null
                                    isConnected = false
                                    isConnecting = false
                                    iceState = ""
                                    status = "Enter a 5-char room code"
                                    remotePeerId = null
                                    hasCreatedOffer = false
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
