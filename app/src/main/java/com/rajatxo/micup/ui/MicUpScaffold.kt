package com.rajatxo.micup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rajatxo.micup.ui.screens.HomeScreen
import com.rajatxo.micup.ui.screens.RoomScreen
import com.rajatxo.micup.ui.screens.SettingsScreen

/**
 * The single root composable for the whole app.
 *
 * Implements Coral X layout laws:
 *  - Law 1: Provides [LocalPlayerAwareWindowInsets] for every screen.
 *  - Law 7: Wraps everything in BoxWithConstraints so screens can read
 *           `maxHeight` instead of `screenHeightDp` (which breaks in
 *           multi-window / split-screen / foldables).
 */
@Composable
fun MicUpScaffold() {
    val view = LocalView.current

    // Resolve insets ONCE here at the root.
    val insets: WindowInsetsCompat = remember(view) {
        WindowInsetsCompat.toWindowInsetsCompat(view.rootWindowInsets, view)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalPlayerAwareWindowInsets provides insets) {
            val navController = rememberNavController()
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.fillMaxSize().background(Color.Black)
            ) {
                composable("home") {
                    HomeScreen(
                        onNavigateToRoom = { code ->
                            navController.navigate("room/$code")
                        },
                        onNavigateToSettings = {
                            navController.navigate("settings")
                        }
                    )
                }
                composable("room/{code}") { backStackEntry ->
                    val code = backStackEntry.arguments?.getString("code").orEmpty()
                    RoomScreen(
                        roomCode = code,
                        onLeave = { navController.popBackStack("home", inclusive = false) }
                    )
                }
                composable("settings") {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
