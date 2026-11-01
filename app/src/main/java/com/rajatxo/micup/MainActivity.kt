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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import org.webrtc.PeerConnectionFactory
import kotlin.math.sqrt

/**
 * Step 2 — Mic capture test with live volume meter.
 *
 * Proves:
 * 1. RECORD_AUDIO permission is granted
 * 2. WebRTC AudioDeviceModule captures from the mic
 * 3. We can see the volume changing in real time
 * 4. No crash when leaving the screen
 *
 * Visual: a coral volume bar that grows/shrinks as you speak.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            MaterialTheme {
                MicTestScreen()
            }
        }
    }
}

@Composable
private fun MicTestScreen() {
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

    // Request mic permission on first launch.
    LaunchedEffect(Unit) {
        if (!hasMicPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "MicUp",
                color = Color(0xFFFF6B6B),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Step 2 of 10 — Mic Capture Test",
                color = Color(0xFF888888),
                fontSize = 13.sp
            )

            Spacer(Modifier.height(48.dp))

            when {
                !hasMicPermission -> {
                    PermissionCard(onRequest = {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    })
                }
                else -> {
                    VolumeMeterCard(appContext)
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x22FF5252))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Microphone permission needed",
            color = Color(0xFFFF5252),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Tap below to grant — required for voice chat",
            color = Color(0xFFCCCCCC),
            fontSize = 13.sp
        )
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFF6B6B))
                .clickable { onRequest() }
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Grant permission",
                color = Color.Black,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * The volume meter — captures audio from the mic via WebRTC and shows
 * the current volume as a growing/shrinking bar.
 *
 * CRITICAL cleanup: the AudioDeviceModule is released in DisposableEffect
 * when the composable leaves the screen. This prevents mic leaks and
 * crashes on screen exit.
 */
@Composable
private fun VolumeMeterCard(appContext: android.content.Context) {
    // 0.0 = silent, 1.0 = loud
    var volume by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("Initializing WebRTC...") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Holds the AudioDeviceModule so we can release it on dispose.
    // Typed as Any? to avoid Kotlin's metadata-resolution issue with
    // JavaAudioDeviceModule — we use the Java helper for all operations.
    var adm by remember { mutableStateOf<Any?>(null) }

    // Count of samples received — proves the callback is firing.
    var sampleCount by remember { mutableStateOf(0L) }
    var lastSampleTime by remember { mutableStateOf(0L) }

    DisposableEffect(Unit) {
        // Build a separate thread for WebRTC work so we don't block the UI.
        val thread = Thread {
            try {
                status = "Initializing WebRTC..."
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions
                        .builder(appContext)
                        .setEnableInternalTracer(false)
                        .createInitializationOptions()
                )

                status = "Building AudioDeviceModule..."
                // Build the ADM via the Java helper — Kotlin can't resolve
                // JavaAudioDeviceModule directly due to a metadata issue
                // with stream-webrtc-android 1.3.9.
                val admInstance = AudioDeviceModuleBuilder.build(appContext) { audioSamples ->
                    // Increment counter + record timestamp — proves callback fires.
                    sampleCount += 1
                    lastSampleTime = System.currentTimeMillis()

                    // audioSamples.data is a byte[] of 16-bit PCM audio.
                    val data = audioSamples.data
                    if (data.size > 1) {
                        var sum = 0.0
                        var i = 0
                        while (i + 1 < data.size) {
                            val b1 = data[i].toInt() and 0xFF
                            val b2 = data[i + 1].toInt() and 0xFF
                            val sample = (b2 shl 8) or b1
                            val signed = if (sample > 32767) sample - 65536 else sample
                            sum += signed.toDouble() * signed.toDouble()
                            i += 4
                        }
                        val count = (data.size / 4)
                        if (count > 0) {
                            val rms = sqrt(sum / count)
                            val normalized = (rms / 32768.0).toFloat()
                            val boosted = (normalized * 4f).coerceIn(0f, 1f)
                            volume = boosted
                        }
                    }
                }

                adm = admInstance

                status = "Building PeerConnectionFactory..."
                val factory = PeerConnectionFactory.builder()
                    .setAudioDeviceModule(admInstance)
                    .createPeerConnectionFactory()

                // CRITICAL: PeerConnectionFactory alone doesn't start mic capture.
                // WebRTC only pulls from the mic when an AudioSource + AudioTrack
                // is created AND enabled. Without this, SamplesReadyCallback
                // never fires — which is exactly what the user is seeing (bar
                // doesn't move).
                status = "Creating audio source + track..."
                val constraints = org.webrtc.MediaConstraints()
                val audioSource = factory.createAudioSource(constraints)
                val audioTrack = factory.createAudioTrack("ARDAMS", audioSource)
                audioTrack.setEnabled(true)

                status = "Speak into your mic!"
            } catch (t: Throwable) {
                errorMessage = "${t::class.java.simpleName}: ${t.message}"
                status = "FAILED"
            }
        }
        thread.start()

        onDispose {
            // CRITICAL: release the mic when leaving the screen.
            // Without this, the mic stays on and other apps can't use it.
            try { AudioDeviceModuleBuilder.release(adm) } catch (_: Throwable) {}
            adm = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x1AFFFFFF))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (errorMessage != null) {
            Text(
                text = "FAILED ✗",
                color = Color(0xFFFF5252),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = errorMessage!!,
                color = Color(0xFFCCCCCC),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            return@Column
        }

        Text(
            text = status,
            color = Color.White,
            fontSize = 15.sp
        )

        if (status == "Speak into your mic!") {
            Spacer(Modifier.height(24.dp))

            // Volume bar — coral, grows with mic input.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x14FFFFFF))
            ) {
                // The growing bar — height scales with volume.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((volume * 120f).dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (volume > 0.7f) Color(0xFF4ADE80)  // green = loud
                            else if (volume > 0.3f) Color(0xFFFF6B6B)  // coral = normal
                            else Color(0x33FF6B6B)  // dim = quiet
                        )
                        .align(Alignment.BottomCenter)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Volume percentage text.
            Text(
                text = "${(volume * 100).toInt()}%",
                color = Color(0xFFCCCCCC),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(8.dp))

            // DIAGNOSTIC: shows if SamplesReadyCallback is firing.
            // If sampleCount stays 0 → callback never fires (mic not capturing)
            // If sampleCount increases but bar stays 0 → audio is silent
            // If sampleCount increases AND bar moves → working!
            val ageSec = if (lastSampleTime > 0) (System.currentTimeMillis() - lastSampleTime) / 1000 else -1
            Text(
                text = "samples: $sampleCount · last: ${if (ageSec < 0) "never" else "${ageSec}s ago"}",
                color = Color(0xFF888888),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Talk into your phone — the bar should move",
                color = Color(0xFF888888),
                fontSize = 12.sp
            )
        } else if (status != "FAILED") {
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(
                color = Color(0xFFFF6B6B),
                strokeWidth = 3.dp,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}
