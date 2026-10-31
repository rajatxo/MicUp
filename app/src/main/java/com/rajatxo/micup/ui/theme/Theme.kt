package com.rajatxo.micup.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MicUpDarkColors = darkColorScheme(
    primary = Coral,
    onPrimary = androidx.compose.ui.graphics.Color.Black,
    primaryContainer = CoralDim,
    onPrimaryContainer = androidx.compose.ui.graphics.Color.White,
    secondary = Gold,
    onSecondary = androidx.compose.ui.graphics.Color.Black,
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
 *
 * NOTE: we do NOT touch the window here. All window setup (edge-to-edge,
 * transparent bars, system bar appearance) is done in MainActivity.
 * Previously this theme had a SideEffect that cast view.context to Activity,
 * which can crash on some OEM ROMs where the view context is wrapped.
 */
@Composable
fun MicUpTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MicUpDarkColors,
        typography = MicUpTypography,
        content = content
    )
}
