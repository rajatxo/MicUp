package com.rajatxo.micup

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import org.webrtc.PeerConnectionFactory
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

/**
 * MicUp application entry point.
 *
 * CRASH-SAFE: every initialization is wrapped in try/catch so the app
 * NEVER crashes at startup. If WebRTC fails to load its native lib
 * (common on some OEM ROMs like Realme UI / ColorOS), the app still
 * launches — voice rooms just won't work, and the error is logged to
 * a file the user can share with the developer.
 *
 * A global UncaughtExceptionHandler catches any other crash and writes
 * the full stack trace to `last_issue.log`. MainActivity shows this
 * log on the next launch so the user can share it.
 */
class MicUpApp : Application() {

    companion object {
        private const val TAG = "MicUp"
        const val ISSUE_LOG_FILENAME = "last_issue.log"

        @Volatile
        var webrtcInitialized = false
            private set
    }

    override fun onCreate() {
        super.onCreate()

        // Install the global crash handler FIRST — so we catch any crash
        // that happens during the rest of onCreate.
        installCrashHandler()

        // WebRTC init — the #1 crash suspect. Native lib loading can fail
        // on devices with restricted lib paths (some Realme / OPPO / Vivo
        // ROMs). If it fails, we log it but don't crash — the app still
        // launches, and the user sees the error in the crash log screen.
        initWebRTC()

        // Notification channel for the voice-room foreground service.
        createNotificationChannel()
    }

    private fun initWebRTC() {
        try {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions
                    .builder(this)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()
            )
            webrtcInitialized = true
            Log.i(TAG, "WebRTC native lib loaded OK")
        } catch (t: Throwable) {
            Log.e(TAG, "WebRTC init failed — voice rooms won't work, but app will launch", t)
            writeIssueLog("WebRTC initialization failed (non-fatal)", t)
        }
    }

    /**
     * Catches any uncaught exception, writes the full stack trace to
     * last_issue.log, then delegates to the previous handler (which
     * terminates the process). On the next launch, MainActivity reads
     * the log and shows it to the user so they can share it.
     */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeIssueLog("Uncaught exception on thread: ${thread.name}", throwable)
            } catch (_: Throwable) { /* can't do anything */ }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Writes a crash/issue log to the app's internal files dir.
     * Includes device info (manufacturer, model, Android version) so
     * we can diagnose device-specific issues.
     */
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
