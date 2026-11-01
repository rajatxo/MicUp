package com.rajatxo.micup

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.rajatxo.micup.ui.MicUpScaffold
import com.rajatxo.micup.ui.theme.Coral
import com.rajatxo.micup.ui.theme.MicUpTheme
import com.rajatxo.micup.ui.theme.TextPrimary
import com.rajatxo.micup.ui.theme.TextSecondary
import java.io.File

/**
 * Single Activity. Holds the Compose tree.
 *
 * Coral X layout laws applied here:
 *  - Law 2: Manual edge-to-edge via WindowCompat.setDecorFitsSystemWindows(false).
 *  - Law 6: System gesture exclusion zone on the left edge.
 *  - Law 9: Status bar icon appearance flips per app theme.
 *  - Law 10: BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE.
 *
 * CRASH RECOVERY: if the app crashed on a previous launch, the crash log
 * is read from filesDir and shown in a CrashLogScreen. The user can share
 * the log with the developer or dismiss it to try again.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Law 2: manual edge-to-edge.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Keep the screen on while a voice room is active.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setSystemGestureExclusion()

        setContent {
            MicUpTheme(darkTheme = true) {
                SideEffect { configureSystemBars(true) }

                var crashLog by remember { mutableStateOf(readIssueLog()) }

                if (crashLog != null) {
                    CrashLogScreen(
                        crashLog = crashLog!!,
                        onShare = { shareCrashLog(crashLog!!) },
                        onDismiss = {
                            clearIssueLog()
                            crashLog = null
                        }
                    )
                } else {
                    MicUpScaffold()
                    // Mark this launch as successful — resets the crash counter.
                    LaunchedEffect(Unit) { /* future: could track crash count here */ }
                }
            }
        }
    }

    // ---------- Crash log helpers ----------

    private fun readIssueLog(): String? {
        return try {
            val file = File(filesDir, MicUpApp.ISSUE_LOG_FILENAME)
            if (!file.exists()) return null
            val log = file.readText()
            // Only show crash logs from the CURRENT version. If the log is
            // from a previous version (e.g., user updated without dismissing),
            // delete it silently — no point showing stale crashes.
            val currentVersion = try {
                packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
            } catch (_: Throwable) { "unknown" }
            if (!log.contains("App version: $currentVersion")) {
                file.delete()
                return null
            }
            log
        } catch (_: Throwable) { null }
    }

    private fun clearIssueLog() {
        try { File(filesDir, MicUpApp.ISSUE_LOG_FILENAME).delete() } catch (_: Throwable) {}
    }
    private fun shareCrashLog(log: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "MicUp crash log")
                putExtra(Intent.EXTRA_TEXT, log)
            }
            startActivity(Intent.createChooser(intent, "Share crash log"))
        } catch (_: Throwable) {}
    }

    // ---------- System bar + gesture setup ----------

    /** Law 9: flip system bar icon appearance per theme. */
    private fun configureSystemBars(isDark: Boolean) {
        try {
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
                // Law 10: the ONLY system-bars behavior that doesn't steal gestures.
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } catch (_: Throwable) { /* non-fatal */ }
    }

    /** Law 6: exclude the left 35% of the screen from the system back-gesture. */
    private fun setSystemGestureExclusion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.decorView.post {
                try {
                    val width = window.decorView.width
                    val height = window.decorView.height
                    val exclusionWidth = (width * 0.35f).toInt()
                    window.setSystemGestureExclusionRects(
                        listOf(android.graphics.Rect(0, 0, exclusionWidth, height))
                    )
                } catch (_: Throwable) { /* non-fatal */ }
            }
        }
    }
}

// ---------- Crash Log Screen ----------

/**
 * Full-screen crash log viewer. Shows when the app crashed on a previous
 * launch. The user can share the log (via Telegram, email, etc.) or
 * dismiss it to try launching the app again.
 */
@Composable
private fun CrashLogScreen(
    crashLog: String,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        Text(
            text = "MicUp crashed",
            style = MaterialTheme.typography.headlineMedium,
            color = Color(0xFFFF5252),
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "The app crashed last time. Please share this log with the developer on Telegram (@rajat_xo) so we can fix it.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(20.dp))

        // Share button — coral, prominent
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Coral)
                .clickable { onShare() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Share crash log",
                color = Color.Black,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(12.dp))

        // Dismiss button — subtle, try again
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x1AFFFFFF))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Dismiss & try again",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(Modifier.height(24.dp))

        // Crash log text — scrollable, monospace
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = crashLog,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = Color(0xFFB5B5B5)
                )
            )
        }
    }
}
