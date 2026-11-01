package com.rajatxo.micup.audio

import android.content.Context
import android.util.Log
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig

/**
 * Agora voice engine — replaces our broken WebRTC stack entirely.
 *
 * ## Why Agora
 *
 * 1. **No more random crashes.** Agora handles Android 14 foreground service
 *    permissions, native lib loading, and audio routing correctly on every
 *    device.
 *
 * 2. **Telegram-quality audio.** AUDIO_PROFILE_MUSIC_HIGH_QUALITY sets Opus
 *    to 128kbps stereo — same as Telegram voice chat.
 *
 * 3. **No ducking.** AUDIO_SCENARIO_GAME_STREAMING tells Agora to route
 *    audio as a game sound effect, NOT a phone call.
 *
 * 4. **Always works on mobile networks.** Agora's SFU handles NAT traversal
 *    server-side — no STUN/TURN config needed.
 */
class AgoraVoiceEngine private constructor(private val context: Context) {

    private var rtcEngine: RtcEngine? = null

    /** The last error message from init() — used for debugging. */
    @Volatile
    var lastError: String? = null
        private set

    companion object {
        private const val TAG = "MicUp/Agora"

        @Volatile
        private var instance: AgoraVoiceEngine? = null

        fun getInstance(context: Context): AgoraVoiceEngine {
            return instance ?: synchronized(this) {
                instance ?: AgoraVoiceEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Initialize the Agora RtcEngine. Called once per app session.
     *
     * CRITICAL audio settings:
     *  - AUDIO_PROFILE_MUSIC_HIGH_QUALITY: Opus 128kbps stereo (Telegram quality)
     *  - AUDIO_SCENARIO_GAME_STREAMING: OS treats us as a game sound → no ducking
     *
     * Returns the RtcEngine instance, or null on failure. On failure,
     * [lastError] contains the actual exception message for debugging.
     */
    fun init(handler: IRtcEngineEventHandler): RtcEngine? {
        if (rtcEngine != null) return rtcEngine
        lastError = null
        return try {
            val appId = com.rajatxo.micup.BuildConfig.AGORA_APP_ID
            Log.i(TAG, "=== Agora init step 1: App ID check ===")
            Log.i(TAG, "App ID: ${appId.take(8)}... (total ${appId.length} chars)")

            if (appId.isBlank() || appId.length != 32) {
                lastError = "App ID format invalid (got ${appId.length} chars, expected 32). " +
                    "Value: '$appId'"
                Log.e(TAG, lastError!!)
                return null
            }
            Log.i(TAG, "✓ App ID format OK")

            Log.i(TAG, "=== Agora init step 2: Check native lib loaded ===")
            try {
                System.loadLibrary("agora-rtc-sdk")
                Log.i(TAG, "✓ agora-rtc-sdk native lib loaded")
            } catch (e: UnsatisfiedLinkError) {
                lastError = "Native lib 'agora-rtc-sdk' failed to load: ${e.message}. " +
                    "This means the .so file for your phone's CPU architecture is missing."
                Log.e(TAG, lastError!!, e)
                return null
            }

            Log.i(TAG, "=== Agora init step 3: Create RtcEngineConfig ===")
            val config = RtcEngineConfig().apply {
                mContext = context
                mAppId = appId
                mEventHandler = handler
            }
            Log.i(TAG, "✓ Config created — mContext=$context, mAppId=${appId.take(8)}...")

            Log.i(TAG, "=== Agora init step 4: RtcEngine.create(config) ===")
            val engine = RtcEngine.create(config)
            Log.i(TAG, "RtcEngine.create() returned: $engine")

            if (engine == null) {
                lastError = "RtcEngine.create() returned null. " +
                    "Agora SDK v${getSdkVersion()} rejected the App ID. " +
                    "Possible causes: invalid App ID, network issue, or SDK bug. " +
                    "Try: 1) Verify App ID in console.agora.io  2) Check internet connection  3) Restart app"
                Log.e(TAG, lastError!!)
                return null
            }
            Log.i(TAG, "✓ RtcEngine created successfully")

            Log.i(TAG, "=== Agora init step 5: setAudioProfile ===")
            engine.setAudioProfile(
                Constants.AUDIO_PROFILE_MUSIC_HIGH_QUALITY,
                Constants.AUDIO_SCENARIO_GAME_STREAMING
            )
            Log.i(TAG, "✓ Audio profile set")

            Log.i(TAG, "=== Agora init step 6: enableAudio ===")
            engine.enableAudio()
            Log.i(TAG, "✓ Audio enabled")

            rtcEngine = engine
            Log.i(TAG, "=== Agora init COMPLETE ===")
            rtcEngine
        } catch (t: Throwable) {
            val errorMsg = "${t::class.java.simpleName}: ${t.message}"
            lastError = errorMsg
            Log.e(TAG, "Agora init failed at exception: $errorMsg", t)
            try {
                val app = context.applicationContext as? com.rajatxo.micup.MicUpApp
                app?.writeIssueLog("Agora init failed: $errorMsg", t)
            } catch (_: Throwable) {}
            null
        }
    }

    /** Get the Agora SDK version string for debugging. */
    private fun getSdkVersion(): String {
        return try {
            RtcEngine.getSdkVersion()
        } catch (_: Throwable) {
            "unknown"
        }
    }

    /**
     * Join an Agora channel (voice room).
     *
     * @param channelName the room code (e.g., "ABC234")
     * @param uid 0 = let Agora assign a random UID
     * @return 0 on success, error code otherwise
     */
    fun joinChannel(channelName: String, uid: Int = 0): Int {
        val engine = rtcEngine ?: run {
            Log.e(TAG, "joinChannel called before init()")
            return -1
        }
        return engine.joinChannel(null, channelName, "", uid)
    }

    /** Leave the current channel. */
    fun leaveChannel(): Int {
        return rtcEngine?.leaveChannel() ?: -1
    }

    /** Mute/unmute the local mic. */
    fun muteLocalAudioStream(muted: Boolean): Int {
        return rtcEngine?.muteLocalAudioStream(muted) ?: -1
    }

    /** Release the engine. Safe to call multiple times. */
    fun release() {
        try { rtcEngine?.leaveChannel() } catch (_: Throwable) {}
        try { RtcEngine.destroy() } catch (_: Throwable) {}
        rtcEngine = null
        instance = null
        Log.i(TAG, "Agora RtcEngine released")
    }
}
