package com.rajatxo.micup.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rajatxo.micup.audio.RoomState
import com.rajatxo.micup.audio.RoomViewModel
import com.rajatxo.micup.audio.RoomViewModelFactory
import com.rajatxo.micup.audio.VoiceService
import com.rajatxo.micup.ui.components.GlassCard
import com.rajatxo.micup.ui.theme.Coral
import com.rajatxo.micup.ui.theme.CoralBright
import com.rajatxo.micup.ui.theme.GlassStroke
import com.rajatxo.micup.ui.theme.Muted
import com.rajatxo.micup.ui.theme.Online
import com.rajatxo.micup.ui.theme.Talking
import com.rajatxo.micup.ui.theme.TextPrimary
import com.rajatxo.micup.ui.theme.TextSecondary

/**
 * In-room screen. Shows the room code (with copy), the participant list,
 * and the bottom action bar (mute / leave).
 *
 * The foreground service starts here so the mic survives the user
 * backgrounding MicUp to open Free Fire / BGMI / CODM.
 */
@Composable
fun RoomScreen(
    roomCode: String,
    onLeave: () -> Unit
) {
    val ctx = LocalContext.current
    val vm: RoomViewModel = viewModel(factory = RoomViewModelFactory(ctx.applicationContext as android.app.Application))
    val state by vm.state.collectAsState()
    val participants by vm.participants.collectAsState()
    val isMuted by vm.isMuted.collectAsState()

    // Guard against multiple rapid Leave taps. Once true, all subsequent
    // taps are ignored. Fixes the "7-8 taps to leave" issue.
    var hasLeft by remember { mutableStateOf(false) }

    // Start the foreground service the moment we enter the room.
    LaunchedEffect(roomCode) {
        VoiceService.start(ctx, roomCode)
    }

    // When the screen is disposed (user navigates away OR leaves), stop
    // the foreground service and release resources. This guarantees the
    // mic is released even if the Leave button wasn't tapped (e.g., user
    // pressed the system back button).
    DisposableEffect(roomCode) {
        onDispose {
            try { VoiceService.stop(ctx) } catch (_: Throwable) {}
            vm.leave()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Coral glow at the top.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Coral.copy(alpha = 0.28f), Color.Transparent),
                        radius = 700f
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
            Spacer(Modifier.height(20.dp))

            // Room code card
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                strong = true,
                cornerRadius = 28
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "ROOM CODE",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        letterSpacing = 3.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                val cb = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cb.setPrimaryClip(ClipData.newPlainText("MicUp Room", roomCode))
                                Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = roomCode,
                            color = TextPrimary,
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 8.sp
                            )
                        )
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Online)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Tap code to copy · Share with squad",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // Section header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SQUAD",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "${participants.size + 1} connected",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            Spacer(Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Local participant — always at the top.
                item {
                    ParticipantCell(
                        name = "You",
                        isLocal = true,
                        isMuted = isMuted,
                        isTalking = !isMuted
                    )
                }
                items(participants, key = { it.id }) { p ->
                    ParticipantCell(
                        name = p.name,
                        isLocal = false,
                        isMuted = false,
                        isTalking = p.talking && p.connected
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Bottom action bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(if (isMuted) Color(0x22FF5252) else Coral)
                            .border(
                                width = if (isMuted) 1.dp else 0.dp,
                                color = if (isMuted) Muted else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { vm.toggleMute() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isMuted)
                                Icons.Filled.MicOff
                            else
                                Icons.Filled.Mic,
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            tint = if (isMuted) Muted else Color.Black,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (isMuted) "Muted" else "Live",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isMuted) Muted else TextSecondary
                    )
                }

                // Leave button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0x22FF5252))
                            .border(1.dp, Muted, CircleShape)
                            .clickable {
                                // Guard against multiple rapid taps — only the first
                                // tap actually triggers leave. Subsequent taps are
                                // ignored. This fixes the "7-8 taps to leave" issue.
                                if (hasLeft) return@clickable
                                hasLeft = true
                                // Stop the foreground service FIRST so the mic green
                                // dot disappears immediately + Android releases the mic.
                                VoiceService.stop(ctx)
                                // Then tell the ViewModel to leave (cleanup runs in
                                // the background, doesn't block the UI).
                                vm.leave()
                                // Finally navigate back to home.
                                onLeave()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CallEnd,
                            contentDescription = "Leave",
                            tint = Muted,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Leave",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted
                    )
                }
            }
        }
    }
}

@Composable
private fun ParticipantCell(
    name: String,
    isLocal: Boolean,
    isMuted: Boolean,
    isTalking: Boolean
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar — coral ring when talking, dim when muted
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isTalking && !isMuted) Coral.copy(alpha = 0.25f)
                        else Color(0x14FFFFFF)
                    )
                    .border(
                        width = if (isTalking && !isMuted) 2.dp else 0.dp,
                        color = if (isTalking && !isMuted) Coral else Color.Transparent,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.firstOrNull()?.uppercase() ?: "?",
                    color = if (isTalking && !isMuted) CoralBright else TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isLocal) "$name (You)" else name,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = when {
                        isMuted -> "Muted"
                        isTalking -> "Talking"
                        else -> "Connected"
                    },
                    color = when {
                        isMuted -> Muted
                        isTalking -> CoralBright
                        else -> TextSecondary
                    },
                    style = MaterialTheme.typography.labelSmall
                )
            }
            // Mic icon on the right
            Icon(
                imageVector = if (isMuted)
                    Icons.Filled.MicOff
                else
                    Icons.Filled.Mic,
                contentDescription = null,
                tint = if (isMuted) Muted else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
