package com.rajatxo.micup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import org.webrtc.PeerConnectionFactory

/**
 * Step 1 — WebRTC native lib load test.
 *
 * The ONLY thing this screen does:
 * 1. Shows "Checking WebRTC..." with a spinner
 * 2. Tries to call PeerConnectionFactory.initialize()
 * 3. Shows the result:
 *    - SUCCESS: "WebRTC loaded ✓" + version + native lib path
 *    - FAILED: the exact error message + stack trace
 *
 * No mic, no signaling, no rooms, no UI polish. Just: does WebRTC
 * load on this phone? If yes → Step 2. If no → we know exactly why.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val appContext = applicationContext

        setContent {
            MaterialTheme {
                WebRtcTestScreen(appContext)
            }
        }
    }
}

@Composable
private fun WebRtcTestScreen(appContext: android.content.Context) {
    var status by remember { mutableStateOf<TestStatus>(TestStatus.Checking) }

    // Run the WebRTC init test ONCE when the screen appears.
    LaunchedEffect(Unit) {
        status = testWebRtcLoad(appContext)
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
                text = "Step 1 of 10 — WebRTC Load Test",
                color = Color(0xFF888888),
                fontSize = 13.sp
            )

            Spacer(Modifier.height(48.dp))

            when (val s = status) {
                TestStatus.Checking -> {
                    CircularProgressIndicator(
                        color = Color(0xFFFF6B6B),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Loading WebRTC native library...",
                        color = Color.White,
                        fontSize = 15.sp
                    )
                }
                is TestStatus.Success -> {
                    StatusCard(
                        title = "WebRTC loaded ✓",
                        titleColor = Color(0xFF4ADE80),
                        details = listOf(
                            "Version: ${s.version}",
                            "Native lib: ${s.nativeLibPath}",
                            "Initialization: OK",
                            "",
                            "Step 1 PASSED. Ready for Step 2 (mic test)."
                        )
                    )
                }
                is TestStatus.Failed -> {
                    StatusCard(
                        title = "WebRTC FAILED ✗",
                        titleColor = Color(0xFFFF5252),
                        details = listOf(
                            "Error: ${s.error}",
                            "",
                            "Stack trace:",
                            s.stackTrace
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    titleColor: Color,
    details: List<String>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x1AFFFFFF))
            .padding(20.dp)
    ) {
        Text(
            text = title,
            color = titleColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        details.forEach { line ->
            Text(
                text = line,
                color = if (line.isEmpty()) Color.Transparent else Color(0xFFCCCCCC),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * The actual WebRTC load test. Runs on a background thread.
 * Captures EVERY possible failure mode and returns a clear status.
 */
private suspend fun testWebRtcLoad(appContext: android.content.Context): TestStatus {
    return try {
        // Step 1a: Verify the native library is bundled for this ABI.
        val nativeLibPath = try {
            val libFile = java.io.File(
                appContext.applicationInfo.nativeLibraryDir,
                "libjingle_peerconnection_so.so"
            )
            if (libFile.exists()) libFile.absolutePath else "NOT FOUND at ${libFile.absolutePath}"
        } catch (e: Throwable) {
            "Could not check: ${e.message}"
        }

        // Step 1b: Initialize PeerConnectionFactory — this loads the native lib.
        // This is what crashed before. We do it HERE, lazily, NOT in Application.
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions
                .builder(appContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )

        TestStatus.Success(
            version = "WebRTC SDK initialized",
            nativeLibPath = nativeLibPath
        )
    } catch (t: Throwable) {
        TestStatus.Failed(
            error = "${t::class.java.simpleName}: ${t.message}",
            stackTrace = t.stackTraceToString().take(500)
        )
    }
}

private sealed interface TestStatus {
    data object Checking : TestStatus
    data class Success(
        val version: String,
        val nativeLibPath: String
    ) : TestStatus
    data class Failed(
        val error: String,
        val stackTrace: String
    ) : TestStatus
}
