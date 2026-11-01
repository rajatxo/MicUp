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
            val config = RtcEngineConfig().apply {
                mContext = context
                mAppId = com.rajatxo.micup.BuildConfig.AGORA_APP_ID
                mEventHandler = handler
            }
            val engine = RtcEngine.create(config)

            // THE MAGIC — set high-quality audio + game streaming scenario.
            // AEC3, NS, and AGC are all built into this preset.
            engine.setAudioProfile(
                Constants.AUDIO_PROFILE_MUSIC_HIGH_QUALITY,
                Constants.AUDIO_SCENARIO_GAME_STREAMING
            )

            // Enable audio module.
            engine.enableAudio()

            rtcEngine = engine
            Log.i(TAG, "Agora RtcEngine ready — MUSIC_HIGH_QUALITY + GAME_STREAMING")
            rtcEngine
        } catch (t: Throwable) {
            // Capture the FULL error for debugging.
            val errorMsg = "${t::class.java.simpleName}: ${t.message}"
            lastError = errorMsg
            Log.e(TAG, "Agora init failed: $errorMsg", t)
            // Also write to the crash log so the user can share it.
            try {
                val app = context.applicationContext as? com.rajatxo.micup.MicUpApp
                app?.writeIssueLog("Agora init failed: $errorMsg", t)
            } catch (_: Throwable) {}
            null
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
