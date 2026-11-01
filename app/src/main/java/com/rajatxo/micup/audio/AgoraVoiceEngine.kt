package com.rajatxo.micup.audio

import android.content.Context
import android.util.Log
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig

/**
 * Agora voice engine — initialization matches the OFFICIAL Agora
 * API-Examples repo exactly:
 * https://github.com/AgoraIO/API-Examples/blob/main/Android/APIExample/
 *   app/src/main/java/io/agora/api/example/examples/basic/JoinChannelVideo.java
 *
 * Key differences from our previous (broken) version:
 * 1. Uses full-sdk (not voice-sdk) — matches official example
 * 2. Sets mChannelProfile = CHANNEL_PROFILE_LIVE_BROADCASTING on config
 * 3. Sets mAudioScenario on config (not via setAudioProfile after)
 * 4. Uses context.applicationContext explicitly
 * 5. Catches Exception (not Throwable) — matches official pattern
 *
 * The previous version was missing mChannelProfile, which caused
 * RtcEngine.create() to return null on some devices.
 */
class AgoraVoiceEngine private constructor(private val context: Context) {

    private var rtcEngine: RtcEngine? = null

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
     * Initialize Agora RtcEngine — copied from the official Agora example.
     *
     * The official pattern:
     *   1. Create RtcEngineConfig
     *   2. Set mContext = applicationContext
     *   3. Set mAppId
     *   4. Set mChannelProfile = CHANNEL_PROFILE_LIVE_BROADCASTING
     *   5. Set mEventHandler
     *   6. Set mAudioScenario
     *   7. RtcEngine.create(config)
     *   8. Catch Exception, printStackTrace
     */
    fun init(handler: IRtcEngineEventHandler): RtcEngine? {
        if (rtcEngine != null) return rtcEngine
        lastError = null

        return try {
            val appId = com.rajatxo.micup.BuildConfig.AGORA_APP_ID
            Log.i(TAG, "init() — App ID: ${appId.take(8)}... (${appId.length} chars)")

            if (appId.isBlank() || appId.length != 32) {
                lastError = "App ID format invalid (got ${appId.length} chars, expected 32)"
                Log.e(TAG, lastError!!)
                return null
            }

            // Build the config EXACTLY like the official Agora example.
            val config = RtcEngineConfig().apply {
                mContext = context.applicationContext
                mAppId = appId
                // CRITICAL: must set mChannelProfile — without this,
                // RtcEngine.create() returns null on some devices.
                mChannelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING
                mEventHandler = handler
                // AUDIO_SCENARIO_GAME_STREAMING → no ducking of game audio.
                mAudioScenario = Constants.AUDIO_SCENARIO_GAME_STREAMING
            }

            Log.i(TAG, "Calling RtcEngine.create(config)...")
            val engine = RtcEngine.create(config)
            Log.i(TAG, "RtcEngine.create() returned: $engine")

            if (engine == null) {
                lastError = "RtcEngine.create() returned null. " +
                    "SDK v${getSdkVersion()}. " +
                    "Check: 1) App ID is valid in console.agora.io " +
                    "2) Internet is working " +
                    "3) Agora project is activated"
                Log.e(TAG, lastError!!)
                return null
            }

            // Set high-quality audio profile (128kbps Opus stereo).
            // This is the no-ducking magic — AUDIO_SCENARIO_GAME_STREAMING
            // was already set on the config, this adds MUSIC_HIGH_QUALITY.
            engine.setAudioProfile(
                Constants.AUDIO_PROFILE_MUSIC_HIGH_QUALITY,
                Constants.AUDIO_SCENARIO_GAME_STREAMING
            )

            engine.enableAudio()

            rtcEngine = engine
            Log.i(TAG, "✓ Agora RtcEngine ready — MUSIC_HIGH_QUALITY + GAME_STREAMING")
            rtcEngine
        } catch (e: Exception) {
            // Match the official example's error handling pattern.
            e.printStackTrace()
            lastError = "${e::class.java.simpleName}: ${e.message}"
            Log.e(TAG, "Agora init failed: $lastError", e)
            try {
                val app = context.applicationContext as? com.rajatxo.micup.MicUpApp
                app?.writeIssueLog("Agora init failed: $lastError", e)
            } catch (_: Throwable) {}
            null
        }
    }

    private fun getSdkVersion(): String {
        return try { RtcEngine.getSdkVersion() } catch (_: Throwable) { "unknown" }
    }

    fun joinChannel(channelName: String, uid: Int = 0): Int {
        val engine = rtcEngine ?: run {
            Log.e(TAG, "joinChannel called before init()")
            return -1
        }
        return engine.joinChannel(null, channelName, "", uid)
    }

    fun leaveChannel(): Int {
        return rtcEngine?.leaveChannel() ?: -1
    }

    fun muteLocalAudioStream(muted: Boolean): Int {
        return rtcEngine?.muteLocalAudioStream(muted) ?: -1
    }

    fun release() {
        try { rtcEngine?.leaveChannel() } catch (_: Throwable) {}
        try { RtcEngine.destroy() } catch (_: Throwable) {}
        rtcEngine = null
        instance = null
        Log.i(TAG, "Agora RtcEngine released")
    }
}
