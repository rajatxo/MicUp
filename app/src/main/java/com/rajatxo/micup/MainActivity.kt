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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.rajatxo.micup.audio.AudioDeviceModuleBuilder
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.MediaConstraints
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

/**
 * Step 5 — WebRTC PeerConnection + SDP offer creation test.
 *
 * Goal: prove WebRTC's PeerConnection API works on this phone.
 *
 * What this screen does:
 * 1. Initializes PeerConnectionFactory (with mic ADM)
 * 2. Creates a PeerConnection (with STUN servers)
 * 3. Creates an AudioSource + AudioTrack (mic)
 * 4. Adds the audio track to the peer connection
 * 5. Creates an SDP offer
 * 6. Displays the SDP offer on screen
 *
 * If we see the SDP offer with "m=audio" + "opus" codec → WebRTC
 * PeerConnection works. Step 6 will use this to connect 2 phones.
 *
 * Single-phone test — no second phone needed yet.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            MaterialTheme {
                SdpOfferTestScreen()
            }
        }
    }
}

@Composable
private fun SdpOfferTestScreen() {
    val context = LocalContext.current
    val appContext = context.applicationContext

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasMicPermission = granted }

    if (!hasMicPermission) {
        LaunchedEffectOnce {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    var status by remember { mutableStateOf("Tap 'Create Offer' to begin") }
    var sdpOffer by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isWorking by remember { mutableStateOf(false) }

    // Hold references for cleanup.
    var factory by remember { mutableStateOf<PeerConnectionFactory?>(null) }
    var peerConnection by remember { mutableStateOf<PeerConnection?>(null) }
    var audioSource by remember { mutableStateOf<AudioSource?>(null) }
    var audioTrack by remember { mutableStateOf<AudioTrack?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try { audioTrack?.dispose() } catch (_: Throwable) {}
            try { audioSource?.dispose() } catch (_: Throwable) {}
            try { peerConnection?.dispose() } catch (_: Throwable) {}
            try { factory?.dispose() } catch (_: Throwable) {}
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "MicUp",
                color = Color(0xFFFF6B6B),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Step 5 of 10 — SDP Offer Test",
                color = Color(0xFF888888),
                fontSize = 12.sp
            )

            Spacer(Modifier.height(24.dp))

            if (!hasMicPermission) {
                Text(
                    "Need mic permission for this test",
                    color = Color(0xFFFF5252),
                    fontSize = 14.sp
                )
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
            } else {
                // Status
                Text(
                    text = status,
                    color = if (errorMessage != null) Color(0xFFFF5252) else Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                errorMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = Color(0xFFFF5252),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Create Offer button
                val canCreate = hasMicPermission && !isWorking
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (canCreate) Color(0xFFFF6B6B) else Color(0x33FF6B6B))
                        .clickable(enabled = canCreate) {
                            isWorking = true
                            errorMessage = null
                            sdpOffer = null
                            status = "Initializing WebRTC..."

                            // Run on background thread — WebRTC init is slow.
                            Thread {
                                try {
                                    // Step 1: Initialize PeerConnectionFactory
                                    if (factory == null) {
                                        status = "Initializing PeerConnectionFactory..."
                                        PeerConnectionFactory.initialize(
                                            PeerConnectionFactory.InitializationOptions
                                                .builder(appContext)
                                                .setEnableInternalTracer(false)
                                                .createInitializationOptions()
                                        )

                                        status = "Building AudioDeviceModule..."
                                        val adm = AudioDeviceModuleBuilder.build(appContext, null)

                                        status = "Creating PeerConnectionFactory..."
                                        factory = PeerConnectionFactory.builder()
                                            .setAudioDeviceModule(adm)
                                            .createPeerConnectionFactory()
                                    }

                                    // Step 2: Create AudioSource + AudioTrack
                                    status = "Creating audio source..."
                                    audioSource = factory!!.createAudioSource(MediaConstraints())
                                    audioTrack = factory!!.createAudioTrack("ARDAMS", audioSource)
                                    audioTrack!!.setEnabled(true)

                                    // Step 3: Create PeerConnection with STUN servers
                                    status = "Creating PeerConnection..."
                                    val iceServers = listOf(
                                        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
                                    )
                                    val config = PeerConnection.RTCConfiguration(iceServers).apply {
                                        sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                                    }

                                    peerConnection = factory!!.createPeerConnection(
                                        config,
                                        object : PeerConnection.Observer {
                                            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
                                            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                                                status = "ICE: $state"
                                            }
                                            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                                            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
                                            override fun onIceCandidate(candidate: org.webrtc.IceCandidate?) {}
                                            override fun onIceCandidatesRemoved(candidates: Array<out org.webrtc.IceCandidate>?) {}
                                            override fun onAddStream(stream: org.webrtc.MediaStream?) {}
                                            override fun onRemoveStream(stream: org.webrtc.MediaStream?) {}
                                            override fun onDataChannel(dc: org.webrtc.DataChannel?) {}
                                            override fun onRenegotiationNeeded() {}
                                            override fun onAddTrack(receiver: org.webrtc.RtpReceiver?, streams: Array<out org.webrtc.MediaStream>?) {}
                                        }
                                    )

                                    if (peerConnection == null) {
                                        errorMessage = "createPeerConnection returned null"
                                        status = "FAILED"
                                        isWorking = false
                                        return@Thread
                                    }

                                    // Step 4: Add audio track to peer connection
                                    status = "Adding audio track..."
                                    peerConnection!!.addTrack(audioTrack)

                                    // Step 5: Create SDP offer
                                    status = "Creating SDP offer..."
                                    peerConnection!!.createOffer(object : SdpObserver {
                                        override fun onCreateSuccess(sdp: SessionDescription?) {
                                            sdpOffer = sdp?.description ?: "(null SDP)"
                                            status = "✓ SDP offer created!"
                                            isWorking = false
                                        }

                                        override fun onSetSuccess() {}
                                        override fun onCreateFailure(error: String?) {
                                            errorMessage = "createOffer failed: $error"
                                            status = "FAILED"
                                            isWorking = false
                                        }

                                        override fun onSetFailure(error: String?) {}
                                    }, MediaConstraints())
                                } catch (t: Throwable) {
                                    errorMessage = "${t::class.java.simpleName}: ${t.message}"
                                    status = "FAILED"
                                    isWorking = false
                                }
                            }.start()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isWorking) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Working...", color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    } else {
                        Text(
                            if (sdpOffer != null) "Create Offer Again" else "Create Offer",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                // SDP offer display
                sdpOffer?.let { sdp ->
                    Spacer(Modifier.height(16.dp))
                    Text("SDP Offer:", color = Color(0xFF888888), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x0FFFFFFF))
                            .padding(12.dp)
                    ) {
                        // Show first 30 lines of SDP
                        val lines = sdp.lines()
                        val displayLines = if (lines.size > 40) lines.take(37) + listOf("... (${lines.size - 37} more lines)") else lines
                        displayLines.forEach { line ->
                            val color = when {
                                line.startsWith("m=audio") -> Color(0xFF4ADE80)
                                line.startsWith("a=rtpmap") && line.contains("opus") -> Color(0xFFFF6B6B)
                                line.startsWith("a=") -> Color(0xFFB5B5B5)
                                else -> Color(0xFFCCCCCC)
                            }
                            Text(
                                text = line,
                                color = color,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Summary
                    val hasAudio = sdp.contains("m=audio")
                    val hasOpus = sdp.contains("opus")
                    val hasSendrecv = sdp.contains("a=sendrecv")
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x1A4ADE80))
                            .padding(12.dp)
                    ) {
                        Text("✓ m=audio line present: $hasAudio", color = if (hasAudio) Color(0xFF4ADE80) else Color(0xFFFF5252), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text("✓ Opus codec: $hasOpus", color = if (hasOpus) Color(0xFF4ADE80) else Color(0xFFFF5252), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text("✓ sendrecv (bidirectional): $hasSendrecv", color = if (hasSendrecv) Color(0xFF4ADE80) else Color(0xFFFF5252), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        if (hasAudio && hasOpus && hasSendrecv) {
                            Spacer(Modifier.height(8.dp))
                            Text("🎉 Step 5 PASSED — ready for Step 6 (2-phone connection)", color = Color(0xFF4ADE80), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/** Helper: run a block once when the composable enters composition. */
@Composable
private fun LaunchedEffectOnce(block: () -> Unit) {
    var hasRun by remember { mutableStateOf(false) }
    if (!hasRun) {
        hasRun = true
        block()
    }
}
