package com.rajatxo.micup.audio

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.rajatxo.micup.MainActivity
import com.rajatxo.micup.R
import com.rajatxo.micup.VoiceChannelNotifier

/**
 * Foreground service that keeps the mic and WebRTC peer connections alive
 * when the user backgrounds MicUp to open a game (Free Fire, BGMI, CODM).
 *
 * Type: FOREGROUND_SERVICE_TYPE_MICROPHONE (Android 14+ requirement).
 * Notification: low-importance, no sound — just a persistent "you're in
 * a room" indicator so the OS doesn't kill us.
 *
 * CRASH-SAFE: on Android 14+ starting a FGS with type microphone requires
 * the RECORD_AUDIO permission to be HELD at runtime. If the user denied
 * mic permission, we skip startForeground and just run as a regular
 * service (the app still works, just won't survive backgrounding).
 */
class VoiceService : Service() {

    companion object {
        private const val TAG = "MicUp/VoiceService"
        const val NOTIF_ID = 4201
        const val ACTION_START = "com.rajatxo.micup.START_VOICE"
        const val ACTION_STOP = "com.rajatxo.micup.STOP_VOICE"
        const val EXTRA_ROOM_CODE = "room_code"

        /**
         * Start the voice foreground service. SAFE — checks mic permission
         * first and falls back to a regular service if not granted.
         */
        fun start(context: Context, roomCode: String) {
            val i = Intent(context, VoiceService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ROOM_CODE, roomCode)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(i)
                } else {
                    context.startService(i)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to start voice service (non-fatal)", t)
            }
        }

        fun stop(context: Context) {
            try {
                context.startService(Intent(context, VoiceService::class.java).apply { action = ACTION_STOP })
            } catch (_: Throwable) { /* non-fatal */ }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val code = intent.getStringExtra(EXTRA_ROOM_CODE) ?: "——"
                startForegroundSafely(code)
            }
            ACTION_STOP -> {
                try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Throwable) {}
                stopSelf()
            }
        }
        return START_STICKY
    }

    /**
     * Start as foreground service. On Android 14+ this requires RECORD_AUDIO
     * permission to be HELD — if not, we gracefully fall back to a regular
     * service instead of crashing.
     */
    private fun startForegroundSafely(roomCode: String) {
        val notif = buildNotification(roomCode)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ — must hold RECORD_AUDIO to use FGS type microphone
                val hasMic = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasMic) {
                    startForeground(
                        NOTIF_ID,
                        notif,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                } else {
                    // No mic permission — fall back to a regular notification-only
                    // foreground service (no microphone type). The app still works
                    // for receiving audio; the user just can't broadcast their voice.
                    Log.w(TAG, "Starting without mic permission — voice send disabled")
                    startForeground(NOTIF_ID, notif)
                }
            } else {
                startForeground(NOTIF_ID, notif)
            }
        } catch (t: SecurityException) {
            // Last-resort fallback: try without the microphone type
            Log.e(TAG, "FGS start with mic type failed, falling back to regular", t)
            try { startForeground(NOTIF_ID, notif) } catch (_: Throwable) {}
        } catch (t: Throwable) {
            Log.e(TAG, "FGS start failed entirely (non-fatal)", t)
        }
    }

    private fun buildNotification(roomCode: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, VoiceChannelNotifier.CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_voice_title))
            .setContentText(getString(R.string.notif_voice_text) + " · " + roomCode)
            .setSmallIcon(R.drawable.ic_splash_icon)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
