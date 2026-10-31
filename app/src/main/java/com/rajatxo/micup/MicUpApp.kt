package com.rajatxo.micup

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import org.webrtc.PeerConnectionFactory

/**
 * MicUp application entry point.
 *
 * Initialises the WebRTC PeerConnectionFactory once per process and
 * creates the voice-room notification channel that the foreground
 * service uses to keep the mic alive while you play.
 */
class MicUpApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // WebRTC needs a global init before any PeerConnectionFactory is built.
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions
                .builder(this)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )

        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            VoiceChannelNotifier.CHANNEL_ID,
            getString(R.string.notif_channel_voice),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notif_channel_voice_desc)
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }
}

/** Tiny holder so the service can reference the channel id without a context leak. */
object VoiceChannelNotifier {
    const val CHANNEL_ID = "micup_voice_room"
}
