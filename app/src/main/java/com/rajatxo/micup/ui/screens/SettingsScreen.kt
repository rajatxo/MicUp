package com.rajatxo.micup.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.micup.R
import com.rajatxo.micup.data.MicUpPrefs
import com.rajatxo.micup.ui.components.GlassCard
import com.rajatxo.micup.ui.theme.Coral
import com.rajatxo.micup.ui.theme.GlassStroke
import com.rajatxo.micup.ui.theme.Online
import com.rajatxo.micup.ui.theme.TextPrimary
import com.rajatxo.micup.ui.theme.TextSecondary

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { MicUpPrefs(ctx) }
    var signalingUrl by remember { mutableStateOf(prefs.signalingUrl) }
    var displayName by remember { mutableStateOf(prefs.displayName) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0x1AFFFFFF))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
        }

        // About card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "MicUp",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Version 1.0.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Voice rooms that never duck your game sound. " +
                        "Built for gamers who want crisp squad comms " +
                        "alongside full-volume gameplay audio — no Discord ducking, " +
                        "no Parallel Space needed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Contact links
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24
        ) {
            Column {
                LinkRow(
                    icon = Icons.Filled.Person,
                    label = "Developer — @rajat_xo",
                    onClick = {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ctx.getString(R.string.developer_telegram))))
                    }
                )
                Divider()
                LinkRow(
                    icon = Icons.Filled.Group,
                    label = "Support Group",
                    onClick = {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ctx.getString(R.string.support_group))))
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Signaling server config — required for voice rooms to actually connect.
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Signaling Server",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "WebSocket URL of your Cloudflare Worker. " +
                        "Deploy the worker.js from the repo, then paste the URL here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x14FFFFFF))
                        .border(1.dp, GlassStroke, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (signalingUrl.isEmpty()) {
                        Text(
                            text = "wss://your-worker.workers.dev",
                            color = TextSecondary.copy(alpha = 0.4f),
                            style = TextStyle(fontSize = 14.sp)
                        )
                    }
                    BasicTextField(
                        value = signalingUrl,
                        onValueChange = {
                            signalingUrl = it
                            prefs.signalingUrl = it
                        },
                        singleLine = true,
                        cursorBrush = SolidColor(Coral),
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            color = TextPrimary,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Display name (shown to squad)",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x14FFFFFF))
                        .border(1.dp, GlassStroke, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (displayName.isEmpty()) {
                        Text(
                            text = "Player",
                            color = TextSecondary.copy(alpha = 0.4f),
                            style = TextStyle(fontSize = 14.sp)
                        )
                    }
                    BasicTextField(
                        value = displayName,
                        onValueChange = {
                            displayName = it
                            prefs.displayName = it
                        },
                        singleLine = true,
                        cursorBrush = SolidColor(Coral),
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            color = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Tech card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "How it works",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "MicUp uses WebRTC (Opus 48kHz, software AEC3/NS/AGC) " +
                        "with USAGE_GAME audio attributes. The OS treats your " +
                        "voice chat as a game sound — so it never ducks your " +
                        "gameplay audio. Discord and Free Fire's in-game voice " +
                        "both trigger MODE_IN_COMMUNICATION which is why they " +
                        "kill your game sound. MicUp doesn't.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Footer
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
}

@Composable
private fun LinkRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Coral,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary
        )
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = TextSecondary
        )
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(GlassStroke.copy(alpha = 0.5f))
    )
}
