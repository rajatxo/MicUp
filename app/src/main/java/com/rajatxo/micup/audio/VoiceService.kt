package com.rajatxo.micup.audio

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
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
 */
class VoiceService : Service() {

    companion object {
        const val NOTIF_ID = 4201
        const val ACTION_START = "com.rajatxo.micup.START_VOICE"
        const val ACTION_STOP = "com.rajatxo.micup.STOP_VOICE"
        const val EXTRA_ROOM_CODE = "room_code"

        fun start(context: Context, roomCode: String) {
            val i = Intent(context, VoiceService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ROOM_CODE, roomCode)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.startService(Intent(context, VoiceService::class.java).apply { action = ACTION_STOP })
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val code = intent.getStringExtra(EXTRA_ROOM_CODE) ?: "——"
                val notif = buildNotification(code)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIF_ID, notif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                } else {
                    startForeground(NOTIF_ID, notif)
                }
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
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
