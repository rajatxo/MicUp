package com.rajatxo.micup.audio

import android.content.Context
import android.util.Log
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig

/**
 * Agora voice engine — EXACT copy of the official Agora API-Examples
 * initialization pattern.
 *
 * Source: https://github.com/AgoraIO/API-Examples/blob/main/Android/
 *   APIExample/app/src/main/java/io/agora/api/example/examples/basic/
 *   JoinChannelVideo.java
 *
 * We use AUDIO_SCENARIO_DEFAULT (not GAME_STREAMING) to match the
 * official example exactly. Once create() succeeds, we can tune audio.
 */
class AgoraVoiceEngine private constructor(private val appContext: Context) {

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
                // Store the applicationContext ONCE — don't call
                // applicationContext again later (that was causing NPE).
                instance ?: AgoraVoiceEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    fun init(handler: IRtcEngineEventHandler): RtcEngine? {
        if (rtcEngine != null) return rtcEngine
        lastError = null

        return try {
            val appId = com.rajatxo.micup.BuildConfig.AGORA_APP_ID
            Log.i(TAG, "init() — App ID: ${appId.take(8)}... (${appId.length} chars)")
            Log.i(TAG, "init() — appContext: $appContext")
            Log.i(TAG, "init() — appContext class: ${appContext.javaClass.name}")

            if (appId.isBlank() || appId.length != 32) {
                lastError = "App ID format invalid (got ${appId.length} chars, expected 32)"
                Log.e(TAG, lastError!!)
                return null
            }

            // EXACT copy of official Agora example pattern.
            val config = RtcEngineConfig()
            config.mContext = appContext
            config.mAppId = appId
            config.mChannelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING
            config.mEventHandler = handler
            // Use DEFAULT (not GAME_STREAMING) to match official example
            // exactly. We'll switch to GAME_STREAMING once create() works.
            config.mAudioScenario = Constants.AUDIO_SCENARIO_DEFAULT

            Log.i(TAG, "Config built — calling RtcEngine.create()...")
            val engine = RtcEngine.create(config)
            Log.i(TAG, "RtcEngine.create() returned: $engine")

            if (engine == null) {
                lastError = "RtcEngine.create() returned null. SDK v${getSdkVersion()}."
                Log.e(TAG, lastError!!)
                return null
            }

            // Now that create() succeeded, upgrade to high-quality audio.
            // Using DEFAULT scenario for now to ensure stability.
            engine.setAudioProfile(
                Constants.AUDIO_PROFILE_MUSIC_HIGH_QUALITY,
                Constants.AUDIO_SCENARIO_DEFAULT
            )
            engine.enableAudio()

            rtcEngine = engine
            Log.i(TAG, "✓ Agora RtcEngine ready")
            rtcEngine
        } catch (e: Exception) {
            e.printStackTrace()
            lastError = "${e::class.java.simpleName}: ${e.message}"
            Log.e(TAG, "Agora init failed: $lastError", e)
            try {
                (appContext as? com.rajatxo.micup.MicUpApp)
                    ?.writeIssueLog("Agora init failed: $lastError", e)
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
