package com.rajatxo.micup

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import kotlin.math.sqrt

/**
 * Step 3 — Echo test. Captures mic input and plays it back through the
 * speaker immediately. You should hear yourself with a slight delay.
 *
 * Proves:
 * 1. Audio OUTPUT works (AudioTrack can play to speaker)
 * 2. Capture → playback pipeline works end-to-end on a single device
 * 3. Latency is acceptable for real-time voice chat
 *
 * IMPORTANT: USE HEADPHONES! Without headphones, the mic picks up the
 * speaker output → feedback howl. We show a warning before starting.
 *
 * This is the LAST single-device test. Step 4 starts connecting 2 phones.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            MaterialTheme {
                EchoTestScreen()
            }
        }
    }
}

@Composable
private fun EchoTestScreen() {
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

    // Echo state: false = stopped, true = echoing
    var isEchoing by remember { mutableStateOf(false) }
    var showHeadphoneWarning by remember { mutableStateOf(false) }

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
                text = "Step 3 of 10 — Echo Test",
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
                showHeadphoneWarning -> {
                    HeadphoneWarningCard(
                        onProceed = {
                            showHeadphoneWarning = false
                            isEchoing = true
                        },
                        onCancel = { showHeadphoneWarning = false }
                    )
                }
                else -> {
                    EchoCard(
                        appContext = appContext,
                        isEchoing = isEchoing,
                        onToggle = { shouldEcho ->
                            if (shouldEcho) {
                                // Show headphone warning before starting
                                showHeadphoneWarning = true
                            } else {
                                isEchoing = false
                            }
                        }
                    )
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

@Composable
private fun HeadphoneWarningCard(
    onProceed: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x22F4B400))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "⚠️ Use headphones",
            color = Color(0xFFF4B400),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Without headphones, you'll get a loud feedback howl.\n\nPlug in earphones/headphones, then tap Start.",
            color = Color(0xFFCCCCCC),
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x1AFFFFFF))
                    .clickable { onCancel() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Cancel", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            Box(
                modifier = Modifier
                    .weight(1.4f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFF6B6B))
                    .clickable { onProceed() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Start echo", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EchoCard(
    appContext: android.content.Context,
    isEchoing: Boolean,
    onToggle: (Boolean) -> Unit
) {
    var volume by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("Tap Start to begin echo test") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var sampleCount by remember { mutableLongStateOf(0L) }

    DisposableEffect(isEchoing) {
        if (!isEchoing) {
            // Not echoing — nothing to do. onDispose is a no-op.
            onDispose { /* no-op */ }
        } else {
            var audioRecord: AudioRecord? = null
            var audioTrack: AudioTrack? = null
            var isRunning = true

            val thread = Thread {
                try {
                    val sampleRate = 48_000
                    val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
                    val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO
                    val audioFormat = AudioFormat.ENCODING_PCM_16BIT

                    val minBufIn = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioFormat)
                    val minBufOut = AudioTrack.getMinBufferSize(sampleRate, channelConfigOut, audioFormat)
                    // 40ms buffer — small enough for low latency, big enough to avoid glitches.
                    val bufferSize = maxOf(minBufIn, minBufOut, 1920 * 2)
                    status = "Buffer: $bufferSize bytes"

                    status = "Creating AudioRecord..."
                    @Suppress("MissingPermission")
                    val record = AudioRecord(
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        sampleRate,
                        channelConfigIn,
                        audioFormat,
                        bufferSize
                    )
                    if (record.state != AudioRecord.STATE_INITIALIZED) {
                        errorMessage = "AudioRecord init failed (state=${record.state})"
                        status = "FAILED"
                        return@Thread
                    }

                    status = "Creating AudioTrack..."
                    val track = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(audioFormat)
                                .setSampleRate(sampleRate)
                                .setChannelMask(channelConfigOut)
                                .build()
                        )
                        .setBufferSizeInBytes(bufferSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                    if (track.state != AudioTrack.STATE_INITIALIZED) {
                        errorMessage = "AudioTrack init failed (state=${track.state})"
                        status = "FAILED"
                        return@Thread
                    }

                    audioRecord = record
                    audioTrack = track

                    status = "Starting..."
                    record.startRecording()
                    track.play()

                    if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                        errorMessage = "AudioRecord didn't start"
                        status = "FAILED"
                        return@Thread
                    }
                    if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        errorMessage = "AudioTrack didn't start"
                        status = "FAILED"
                        return@Thread
                    }

                    status = "ECHOING — speak now!"

                    val buffer = ShortArray(bufferSize / 2)
                    while (isRunning) {
                        val read = record.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            // Write to speaker — this is the echo.
                            track.write(buffer, 0, read)

                            // Update volume meter.
                            sampleCount += 1
                            var sum = 0.0
                            for (i in 0 until read) {
                                val s = buffer[i].toDouble()
                                sum += s * s
                            }
                            val rms = sqrt(sum / read)
                            val normalized = (rms / 32768.0).toFloat()
                            volume = (normalized * 5f).coerceIn(0f, 1f)
                        }
                    }
                } catch (t: Throwable) {
                    errorMessage = "${t::class.java.simpleName}: ${t.message}"
                    status = "FAILED"
                } finally {
                    try { audioRecord?.stop() } catch (_: Throwable) {}
                    try { audioRecord?.release() } catch (_: Throwable) {}
                    try { audioTrack?.stop() } catch (_: Throwable) {}
                    try { audioTrack?.release() } catch (_: Throwable) {}
                }
            }
            thread.start()

            onDispose {
                isRunning = false
                try { thread.join(500) } catch (_: Throwable) {}
            }
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
            color = if (isEchoing) Color(0xFF4ADE80) else Color.White,
            fontSize = 15.sp,
            fontWeight = if (isEchoing) FontWeight.Bold else FontWeight.Normal
        )

        Spacer(Modifier.height(24.dp))

        // Volume bar (only visible while echoing)
        if (isEchoing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x14FFFFFF))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((volume * 80f).dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (volume > 0.7f) Color(0xFF4ADE80)
                            else if (volume > 0.3f) Color(0xFFFF6B6B)
                            else Color(0x33FF6B6B)
                        )
                        .align(Alignment.BottomCenter)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "${(volume * 100).toInt()}% · ${sampleCount} samples",
                color = Color(0xFF888888),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(20.dp))
        }

        // Start/Stop button
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(if (isEchoing) Color(0xFFFF5252) else Color(0xFFFF6B6B))
                .clickable { onToggle(!isEchoing) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isEchoing) "STOP" else "START",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = if (isEchoing) "You should hear yourself with a slight delay"
                   else "Plug in headphones, then tap START",
            color = Color(0xFF888888),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}
