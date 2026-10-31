package com.rajatxo.micup.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rajatxo.micup.audio.RoomEvent
import com.rajatxo.micup.audio.RoomState
import com.rajatxo.micup.audio.RoomViewModel
import com.rajatxo.micup.audio.RoomViewModelFactory
import com.rajatxo.micup.ui.LocalPlayerAwareWindowInsets
import com.rajatxo.micup.ui.components.GlassCard
import com.rajatxo.micup.ui.theme.Coral
import com.rajatxo.micup.ui.theme.CoralBright
import com.rajatxo.micup.ui.theme.GlassStroke
import com.rajatxo.micup.ui.theme.Muted
import com.rajatxo.micup.ui.theme.Online
import com.rajatxo.micup.ui.theme.TextPrimary
import com.rajatxo.micup.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * The very first screen — MicUp wordmark, Create / Join room, settings.
 *
 * Layout: pure black background with a coral radial glow at the top.
 * Everything respects the safe insets via [LocalPlayerAwareWindowInsets].
 */
@Composable
fun HomeScreen(
    onNavigateToRoom: (String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val ctx = LocalContext.current
    val vm: RoomViewModel = viewModel(factory = RoomViewModelFactory(ctx.applicationContext as android.app.Application))
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()

    var joinMode by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    // Mic permission state — required to actually capture audio (and show
    // the green mic dot in the status bar).
    var hasMicPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                ctx, android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val micPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (!granted) {
            error = "Microphone permission is required for voice chat"
        }
    }

    // Request mic permission on first launch.
    LaunchedEffect(Unit) {
        if (!hasMicPermission) {
            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    // When we successfully join, navigate to the room screen.
    LaunchedEffect(state) {
        val s = state
        if (s is RoomState.Joined) {
            onNavigateToRoom(s.roomCode)
            vm.resetNavigation()
        }
        if (s is RoomState.Failed) error = s.message
    }

    // Connecting overlay — shows a spinner when state is Connecting.
    val isConnecting = state is RoomState.Connecting

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .imePadding()
    ) {
        // Coral glow at the top — gives the pure-black surface depth without
        // breaking the AMOLED battery efficiency rule.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Coral.copy(alpha = 0.22f), Color.Transparent),
                        radius = 600f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            // Top bar: settings icon on the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0x1AFFFFFF))
                        .clickable { onNavigateToSettings() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = TextPrimary
                    )
                }
            }

            Spacer(Modifier.height(48.dp))

            // Wordmark
            Text(
                text = "MicUp",
                style = MaterialTheme.typography.displayLarge,
                color = TextPrimary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Text(
                text = "Voice rooms that never duck your game",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(72.dp))

            if (!joinMode) {
                PrimaryButton(
                    text = "Create Room",
                    accent = true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    error = null
                    scope.launch { vm.createRoom() }
                }
                Spacer(Modifier.height(14.dp))
                PrimaryButton(
                    text = "Join Room",
                    accent = false,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    joinMode = true
                }
            } else {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 28
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "Enter room code",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0x14FFFFFF))
                                .border(1.dp, GlassStroke, RoundedCornerShape(16.dp))
                                .padding(horizontal = 18.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (code.isEmpty()) {
                                Text(
                                    text = "ABC234",
                                    color = TextSecondary.copy(alpha = 0.4f),
                                    style = TextStyle(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 6.sp
                                    )
                                )
                            }
                            BasicTextField(
                                value = code,
                                onValueChange = { v -> code = v.take(6).filter { it.isLetterOrDigit() }.uppercase() },
                                singleLine = true,
                                cursorBrush = SolidColor(Coral),
                                textStyle = TextStyle(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 6.sp,
                                    color = TextPrimary
                                ),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Ascii,
                                    capitalization = KeyboardCapitalization.Characters
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SecondaryButton(
                                text = "Back",
                                modifier = Modifier.weight(1f)
                            ) { joinMode = false }
                            PrimaryButton(
                                text = "Join",
                                accent = true,
                                modifier = Modifier.weight(1.4f)
                            ) {
                                if (code.length == 6) {
                                    error = null
                                    scope.launch { vm.joinRoom(code) }
                                } else {
                                    error = "Code must be 6 characters"
                                }
                            }
                        }
                    }
                }
            }

            error?.let {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = it,
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.weight(1f))

            // Footer: made in India + status pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Online)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Made with love in India",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }
        }

        // Connecting overlay — shows a coral spinner on top of everything
        // while we're connecting to the signaling server. Gives instant
        // feedback that the tap was registered, no delay felt.
        if (isConnecting) {
            val connectingState = state as RoomState.Connecting
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = Coral,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = if (connectingState.isHost) "Creating room..." else "Joining room...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    accent: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (accent) Coral else Color(0x1AFFFFFF))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (accent) Color.Black else TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x0FFFFFFF))
            .border(1.dp, GlassStroke, RoundedCornerShape(20.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = TextSecondary,
            style = MaterialTheme.typography.titleMedium
        )
    }
}
