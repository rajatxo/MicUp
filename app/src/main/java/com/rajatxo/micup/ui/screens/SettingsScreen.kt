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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rajatxo.micup.R
import com.rajatxo.micup.ui.components.GlassCard
import com.rajatxo.micup.ui.theme.Coral
import com.rajatxo.micup.ui.theme.GlassStroke
import com.rajatxo.micup.ui.theme.Online
import com.rajatxo.micup.ui.theme.TextPrimary
import com.rajatxo.micup.ui.theme.TextSecondary

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
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
                    imageVector = androidx.compose.material.icons.Icons.Filled.ArrowBack,
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
                    icon = androidx.compose.material.icons.Icons.Filled.Person,
                    label = "Developer — @rajat_xo",
                    onClick = {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ctx.getString(R.string.developer_telegram))))
                    }
                )
                Divider()
                LinkRow(
                    icon = androidx.compose.material.icons.Icons.Filled.Group,
                    label = "Support Group",
                    onClick = {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ctx.getString(R.string.support_group))))
                    }
                )
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
            imageVector = androidx.compose.material.icons.Icons.Filled.ChevronRight,
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
