package com.rajatxo.micup

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

/**
 * MicUp application entry point.
 *
 * CRASH-SAFE: every initialization is wrapped in try/catch so the app
 * NEVER crashes at startup. A global UncaughtExceptionHandler catches
 * any other crash and writes the full stack trace to a file the user
 * can share with the developer.
 *
 * Note: Agora's RtcEngine is initialized lazily in AgoraVoiceEngine.init()
 * when the user first creates/joins a room — not here. This keeps app
 * startup fast and lets us surface init errors to the user via the
 * Failed state instead of crashing the app.
 */
class MicUpApp : Application() {

    companion object {
        private const val TAG = "MicUp"
        const val ISSUE_LOG_FILENAME = "last_issue.log"
    }

    override fun onCreate() {
        super.onCreate()

        // Install the global crash handler FIRST.
        installCrashHandler()

        // Notification channel for the voice-room foreground service.
        createNotificationChannel()
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeIssueLog("Uncaught exception on thread: ${thread.name}", throwable)
            } catch (_: Throwable) { /* can't do anything */ }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun writeIssueLog(header: String, throwable: Throwable) {
        try {
            val sw = StringWriter()
            sw.append("═══ MicUp Crash Log ═══\n")
            sw.append("Time: ${Date()}\n")
            sw.append("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n")
            sw.append("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.BRAND} ${android.os.Build.MODEL}\n")
            sw.append("App version: ${packageManager.getPackageInfo(packageName, 0)?.versionName ?: "?"}\n")
            sw.append("Issue: $header\n")
            sw.append("──────────────────────\n\n")
            throwable.printStackTrace(PrintWriter(sw))
            File(filesDir, ISSUE_LOG_FILENAME).writeText(sw.toString())
        } catch (_: Throwable) { /* can't do anything */ }
    }

    private fun createNotificationChannel() {
        try {
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
        } catch (t: Throwable) {
            Log.e(TAG, "Notif channel creation failed (non-fatal)", t)
        }
    }
}

object VoiceChannelNotifier {
    const val CHANNEL_ID = "micup_voice_room"
}
