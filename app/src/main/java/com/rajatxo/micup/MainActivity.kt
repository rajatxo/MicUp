package com.rajatxo.micup

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.rajatxo.micup.audio.AudioDeviceModuleBuilder
import com.rajatxo.micup.audio.CallParticipant
import com.rajatxo.micup.audio.CallState
import com.rajatxo.micup.audio.VoiceService
import com.rajatxo.micup.data.MicUpPrefs

typealias Participant = CallParticipant

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MaterialTheme {
                App()
            }
        }
    }
}

@Composable
private fun App() {
    // Overlay settings on top of HomeScreen instead of navigating away:
    // leaving HomeScreen's composition would dispose the active call.
    var showSettings by remember { mutableStateOf(false) }
    Box {
        HomeScreen(onOpenSettings = { showSettings = true })
        if (showSettings) {
            SettingsScreen(onBack = { showSettings = false })
        }
    }
}

// ==================== HOME SCREEN ====================

@Composable
private fun HomeScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val prefs = remember { MicUpPrefs(context) }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasMicPermission = granted }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (!hasMicPermission) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (CallState.playerName.value.isBlank()) CallState.playerName.value = prefs.playerName
    }

    // The call lives in VoiceService so it survives swiping the app away.
    val playerName by CallState.playerName.collectAsState()
    val roomCode by CallState.roomCode.collectAsState()
    val status by CallState.status.collectAsState()
    val isConnected by CallState.isConnected.collectAsState()
    val isConnecting by CallState.isConnecting.collectAsState()
    val isMuted by CallState.isMuted.collectAsState()
    val participants by CallState.participants.collectAsState()
    val errorMessage by CallState.errorMessage.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar: title + settings icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("MicUp", color = Color(0xFFFF6B6B), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("Voice rooms that never duck your game", color = Color(0xFF888888), fontSize = 11.sp)
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0x1AFFFFFF))
                        .clickable { onOpenSettings() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(32.dp))

            if (!hasMicPermission) {
                Text("Microphone permission needed", color = Color(0xFFFF5252), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFF6B6B))
                        .clickable { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text("Grant permission", color = Color.Black, fontWeight = FontWeight.SemiBold)
                }
            } else if (!isConnected && !isConnecting) {
                // Name input
                Text("Your Name", color = Color(0xFF888888), fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x14FFFFFF))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (playerName.isEmpty()) {
                        Text("Enter your name", color = Color(0xFF666666), fontSize = 16.sp)
                    }
                    BasicTextField(
                        value = playerName,
                        onValueChange = {
                            CallState.playerName.value = it.take(20)
                            prefs.playerName = it.take(20)
                        },
                        singleLine = true,
                        cursorBrush = SolidColor(Color(0xFFFF6B6B)),
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Room code input
                Text("Room Code", color = Color(0xFF888888), fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x14FFFFFF))
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (roomCode.isEmpty()) {
                        Text("ABCDE", color = Color(0xFF666666), fontSize = 22.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, letterSpacing = 6.sp)
                    }
                    BasicTextField(
                        value = roomCode,
                        onValueChange = {
                            CallState.roomCode.value = it.take(5).filter { c -> c.isLetterOrDigit() }.uppercase()
                        },
                        singleLine = true,
                        cursorBrush = SolidColor(Color(0xFFFF6B6B)),
                        textStyle = TextStyle(color = Color.White, fontSize = 22.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, letterSpacing = 6.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(24.dp))

                val canConnect = roomCode.length == 5 && playerName.isNotBlank()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (canConnect) Color(0xFFFF6B6B) else Color(0x33FF6B6B))
                        .clickable(enabled = canConnect) {
                            // The service owns the call from here on, so the
                            // room survives the app being swiped away.
                            VoiceService.join(appContext, roomCode, playerName)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Connect", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "Share the room code with your friends.\nMax 5 people per room (you + 4 friends) · ~60 MB/hour",
                    color = Color(0xFF888888), fontSize = 11.sp, textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x22FF6B6B))
                            .clickable {
                                Toast.makeText(context, "Playing test tone — turn media volume up", Toast.LENGTH_SHORT).show()
                                AudioDeviceModuleBuilder.playTestTone(appContext)
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            "🔊 Test speaker",
                            color = Color(0xFFFF6B6B), fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x22FF6B6B))
                            .clickable {
                                Toast.makeText(context, "Recording 2s — speak, then it plays back", Toast.LENGTH_SHORT).show()
                                AudioDeviceModuleBuilder.playMicTest(appContext)
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            "🎤 Test mic",
                            color = Color(0xFFFF6B6B), fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                errorMessage?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = Color(0xFFFF5252), fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x22FF5252)).padding(8.dp))
                }
            } else if (isConnecting) {
                // Connecting view
                Spacer(Modifier.height(40.dp))
                Text(status, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(Color(0x22F4B400)), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(Color(0xFFF4B400)))
                }
            } else {
                // Connected view
                Text(status, color = Color(0xFF4ADE80), fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))

                Text("FRIENDS (${participants.size + 1})", color = Color(0xFF888888), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { ParticipantRow(playerName.ifBlank { "You" }, "connected", true) }
                    items(participants) { p ->
                        ParticipantRow(p.name, p.iceState, false)
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(if (isMuted) Color(0x22FF5252) else Color(0xFFFF6B6B))
                                .clickable {
                                    val next = !isMuted
                                    CallState.isMuted.value = next
                                    VoiceService.setMuted(appContext, next)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (isMuted) "🔇" else "🎤", fontSize = 24.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(if (isMuted) "Muted" else "Live", color = Color(0xFF888888), fontSize = 11.sp)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0x22FF5252))
                                .clickable {
                                    // Drops only this device; friends stay in the room.
                                    VoiceService.end(appContext)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✕", color = Color(0xFFFF5252), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Leave", color = Color(0xFF888888), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// ==================== SETTINGS SCREEN ====================

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Surface(
        // Consume taps so they don't fall through to the call screen underneath.
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { },
        color = Color.Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp)
        ) {
            // Top bar with back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0x1AFFFFFF))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text("Settings", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(32.dp))

            // About card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x1AFFFFFF))
                    .padding(24.dp)
            ) {
                Text("MicUp", color = Color(0xFFFF6B6B), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Version ${BuildConfig.VERSION_NAME}", color = Color(0xFF888888), fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
                Text(
                    "Voice rooms that never duck your game sound. " +
                    "Built for gamers who want crisp squad comms " +
                    "alongside full-volume gameplay audio.",
                    color = Color(0xFFCCCCCC), fontSize = 13.sp, lineHeight = 18.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            // Developer card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x1AFFFFFF))
                    .padding(24.dp)
            ) {
                Text("Developer", color = Color(0xFF888888), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.developer_telegram)))
                            context.startActivity(intent)
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF6B6B).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Rajat", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text("@rajat_xo on Telegram", color = Color(0xFF888888), fontSize = 12.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("→", color = Color(0xFF888888), fontSize = 18.sp)
                }
            }

            Spacer(Modifier.weight(1f))

            // Footer
            Text("Made with love in India", color = Color(0xFF666666), fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}

// ==================== SHARED COMPONENTS ====================

@Composable
private fun ParticipantRow(name: String, iceState: String, isSelf: Boolean) {
    val stateColor = when (iceState.lowercase()) {
        "connected", "completed" -> Color(0xFF4ADE80)
        "checking" -> Color(0xFFF4B400)
        "failed", "disconnected" -> Color(0xFFFF5252)
        else -> Color(0xFF888888)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x1AFFFFFF))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(stateColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                name.firstOrNull()?.uppercase() ?: "?",
                color = stateColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (isSelf) "$name (You)" else name,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                when {
                    isSelf -> "Connected"
                    iceState.lowercase() == "connected" || iceState.lowercase() == "completed" -> "Connected"
                    iceState.lowercase() == "checking" -> "Connecting..."
                    iceState.lowercase() == "failed" -> "Failed"
                    else -> "Waiting..."
                },
                color = stateColor,
                fontSize = 11.sp
            )
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(stateColor)
        )
    }
}
