package com.rajatxo.micup

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class MicUpApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Create the notification channel for the voice foreground service.
        try {
            val channel = NotificationChannel(
                "micup_voice_room",
                "Voice Room",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when you're in a voice room"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        } catch (_: Throwable) {}
    }
}
