package com.rajatxo.micup

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
import androidx.compose.runtime.mutableLongStateOf
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
import kotlin.math.sqrt

/**
 * Step 2 (revised) — Mic capture test using Android's NATIVE AudioRecord.
 *
 * PREVIOUS APPROACH FAILED: WebRTC's AudioDeviceModule is lazy — it
 * doesn't start capturing until a PeerConnection pulls from it. So
 * the SamplesReadyCallback never fired and the bar stayed at 0%.
 *
 * NEW APPROACH: Use Android's native AudioRecord API directly. This
 * proves the mic hardware + permission work at all, bypassing WebRTC
 * entirely. If this works → mic is fine, we move to Step 3. If this
 * fails → Realme is blocking mic access at the OS level.
 *
 * Visual: a coral volume bar that grows/shrinks as you speak, with
 * a live sample counter so we can verify capture is actually happening.
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
                text = "Step 2 of 10 — Mic Capture Test (native)",
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
                    NativeVolumeMeterCard(appContext)
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
 * Volume meter using Android's NATIVE AudioRecord.
 *
 * This bypasses WebRTC entirely. If this works → mic hardware is fine
 * and we move to Step 3 (echo test). If this fails → OS-level mic block.
 *
 * Uses 16-bit PCM at 48kHz mono — standard voice chat config.
 */
@Composable
private fun NativeVolumeMeterCard(appContext: android.content.Context) {
    var volume by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("Starting mic...") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var sampleCount by remember { mutableLongStateOf(0L) }
    var lastSampleAgeMs by remember { mutableLongStateOf(-1L) }

    DisposableEffect(Unit) {
        var audioRecord: AudioRecord? = null
        var isRunning = true

        val thread = Thread {
            try {
                val sampleRate = 48_000
                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT

                val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                val bufferSize = maxOf(minBuf * 2, 1920)
                status = "Buffer size: $bufferSize bytes"

                status = "Creating AudioRecord..."
                @Suppress("MissingPermission")
                val record = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )

                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    errorMessage = "AudioRecord failed to initialize (state=${record.state})"
                    status = "FAILED"
                    return@Thread
                }

                status = "Starting recording..."
                record.startRecording()
                audioRecord = record

                if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    errorMessage = "AudioRecord didn't start (state=${record.recordingState})"
                    status = "FAILED"
                    return@Thread
                }

                status = "Speak into your mic!"

                val buffer = ShortArray(bufferSize / 2)
                while (isRunning) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        sampleCount += 1
                        lastSampleAgeMs = 0

                        var sum = 0.0
                        for (i in 0 until read) {
                            val s = buffer[i].toDouble()
                            sum += s * s
                        }
                        val rms = sqrt(sum / read)
                        val normalized = (rms / 32768.0).toFloat()
                        val boosted = (normalized * 5f).coerceIn(0f, 1f)
                        volume = boosted
                    }
                }
            } catch (t: Throwable) {
                errorMessage = "${t::class.java.simpleName}: ${t.message}"
                status = "FAILED"
            }
        }
        thread.start()

        // Background ticker to update "last sample age" display.
        val ticker = Thread {
            while (isRunning) {
                try { Thread.sleep(200) } catch (_: Throwable) {}
                if (lastSampleAgeMs >= 0) {
                    lastSampleAgeMs += 200
                }
            }
        }
        ticker.start()

        onDispose {
            isRunning = false
            try { audioRecord?.stop() } catch (_: Throwable) {}
            try { audioRecord?.release() } catch (_: Throwable) {}
            audioRecord = null
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x14FFFFFF))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((volume * 120f).dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (volume > 0.7f) Color(0xFF4ADE80)
                            else if (volume > 0.3f) Color(0xFFFF6B6B)
                            else Color(0x33FF6B6B)
                        )
                        .align(Alignment.BottomCenter)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "${(volume * 100).toInt()}%",
                color = Color(0xFFCCCCCC),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(8.dp))

            val ageText = when {
                lastSampleAgeMs < 0 -> "never"
                lastSampleAgeMs == 0L -> "now"
                lastSampleAgeMs < 1000 -> "${lastSampleAgeMs}ms ago"
                else -> "${lastSampleAgeMs / 1000}s ago"
            }
            Text(
                text = "samples: $sampleCount · last: $ageText",
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
