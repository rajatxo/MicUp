package com.rajatxo.micup.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat

/**
 * Law 1 — ONE CompositionLocal for ALL insets.
 *
 * Computed ONCE at the root (inside MicUpScaffold) and consumed EVERYWHERE
 * downstream. This includes: system bars + navigation bar + IME.
 *
 * We model it as a plain [WindowInsetsCompat] wrapper so any screen can
 * call `LocalPlayerAwareWindowInsets.current` and get the safe padding
 * without re-resolving insets per composable (which causes recomposition
 * churn on rotate / IME show).
 */
val LocalPlayerAwareWindowInsets = staticCompositionLocalOf<WindowInsetsCompat> {
    error("No WindowInsets provided — wrap your screen in MicUpScaffold")
}

/** Convert a [WindowInsetsCompat] to a Compose PaddingValues. */
fun WindowInsetsCompat.toPaddingValues(): androidx.compose.foundation.layout.PaddingValues {
    val (l, t, r, b) = this.getInsets(
        WindowInsetsCompat.Type.systemBars() or
            WindowInsetsCompat.Type.displayCutout() or
            WindowInsetsCompat.Type.ime()
    )
    return androidx.compose.foundation.layout.PaddingValues(
        start = l.dp, top = t.dp, end = r.dp, bottom = b.dp
    )
}
