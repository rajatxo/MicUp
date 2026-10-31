package com.rajatxo.micup

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.rajatxo.micup.ui.MicUpScaffold
import com.rajatxo.micup.ui.theme.MicUpTheme

/**
 * Single Activity. Holds the Compose tree.
 *
 * Coral X layout laws applied here:
 *  - Law 2: Manual edge-to-edge via WindowCompat.setDecorFitsSystemWindows(false).
 *           We do NOT call enableEdgeToEdge() — that paints its own bar colors we can't control.
 *  - Law 6: System gesture exclusion zone on the left edge so the app's drag gestures
 *           don't fight the system back-gesture.
 *  - Law 9: Status bar icon appearance flips per app theme (light icons on dark theme).
 *  - Law 10: BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE so immersive mode never steals gestures.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash must install before super.onCreate.
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Law 2: manual edge-to-edge. NOT enableEdgeToEdge().
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Keep the screen on while a voice room is active — gameplay sessions can run for hours.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setSystemGestureExclusion()

        setContent {
            // MicUp is always dark (pure black AMOLED) — but we still respect the system
            // value to flip status bar icon appearance correctly.
            val dark = true
            MicUpTheme(darkTheme = dark) {
                SideEffect {
                    configureSystemBars(dark)
                }
                MicUpScaffold()
            }
        }
    }

    /** Law 9: flip system bar icon appearance per theme. */
    private fun configureSystemBars(isDark: Boolean) {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
            // Law 10: the ONLY system-bars behavior that doesn't steal gestures from the app.
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /** Law 6: exclude the left 35% of the screen from the system back-gesture. */
    private fun setSystemGestureExclusion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.decorView.post {
                val width = window.decorView.width
                val height = window.decorView.height
                val exclusionWidth = (width * 0.35f).toInt()
                window.setSystemGestureExclusionRects(
                    listOf(android.graphics.Rect(0, 0, exclusionWidth, height))
                )
            }
        }
    }
}
