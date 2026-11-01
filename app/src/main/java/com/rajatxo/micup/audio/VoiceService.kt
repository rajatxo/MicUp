package com.rajatxo.micup.audio

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.rajatxo.micup.MainActivity
import com.rajatxo.micup.R

/**
 * Foreground service that keeps the WebRTC voice connection alive
 * when the user backgrounds MicUp to open a game (Free Fire, BGMI, etc.).
 *
 * Type: FOREGROUND_SERVICE_TYPE_MICROPHONE (Android 14+ requirement).
 *
 * Without this, Android kills the WebRTC peer connection when the app
 * goes to the background, and voice stops working.
 *
 * With this, Android keeps the process alive + the mic active + the
 * peer connection running. The user sees a persistent notification
 * "MicUp — Voice Room active" so they know voice is still going.
 */
class VoiceService : Service() {

    companion object {
        const val NOTIF_ID = 4201
        const val ACTION_START = "com.rajatxo.micup.START_VOICE"
        const val ACTION_STOP = "com.rajatxo.micup.STOP_VOICE"

        fun start(context: Context) {
            val i = Intent(context, VoiceService::class.java).apply {
                action = ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(i)
                } else {
                    context.startService(i)
                }
            } catch (_: Throwable) {
                // Some OEM ROMs restrict background starts — try foreground
                try { context.startService(i) } catch (_: Throwable) {}
            }
        }

        fun stop(context: Context) {
            try {
                context.startService(Intent(context, VoiceService::class.java).apply { action = ACTION_STOP })
            } catch (_: Throwable) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val notif = buildNotification()
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        // Android 14+ — must specify FGS type microphone
                        startForeground(
                            NOTIF_ID,
                            notif,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        )
                    } else {
                        startForeground(NOTIF_ID, notif)
                    }
                } catch (_: Throwable) {
                    // If startForeground fails (e.g., no mic permission),
                    // still keep the service running as a regular service.
                }
            }
            ACTION_STOP -> {
                try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Throwable) {}
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, "micup_voice_room")
            .setContentTitle("MicUp — Voice Room")
            .setContentText("Connected · Tap to return")
            .setSmallIcon(R.drawable.ic_splash_icon)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
