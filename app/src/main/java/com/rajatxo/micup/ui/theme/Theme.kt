package com.rajatxo.micup.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val MicUpDarkColors = darkColorScheme(
    primary = Coral,
    onPrimary = Color.Black,
    primaryContainer = CoralDim,
    onPrimaryContainer = Color.White,
    secondary = Gold,
    onSecondary = Color.Black,
    background = PureBlack,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    error = Muted,
    outline = GlassStroke,
    outlineVariant = TextTertiary
)

/**
 * MicUp theme. Always dark — pure black for AMOLED battery efficiency,
 * per the Coral X design philosophy.
 */
@Composable
fun MicUpTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = MicUpDarkColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            // Transparent system bars so our edge-to-edge UI shows through.
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MicUpTypography,
        content = content
    )
}
