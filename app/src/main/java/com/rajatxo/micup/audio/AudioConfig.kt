package com.rajatxo.micup.audio

import android.content.Context
import android.media.AudioAttributes
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnectionFactory

/**
 * The heart of MicUp — the no-ducking audio configuration.
 *
 * ## Why this matters
 *
 * When an Android app activates MODE_IN_COMMUNICATION and uses
 * USAGE_VOICE_COMMUNICATION, the OS auto-ducks every other audio stream
 * (gun sounds, footsteps, music) down to ~20% volume. This is the bug
 * Rajat hits in Free Fire and Discord.
 *
 * ## What we do instead
 *
 * We stay in MODE_NORMAL (the default media mode) and tag our audio
 * with USAGE_GAME. The OS then treats our voice chat as a game sound
 * effect — no ducking triggered. Your squad's voice AND the game's
 * audio both play at full volume, side by side.
 *
 * ## Echo cancellation
 *
 * In MODE_IN_COMMUNICATION, Android gives us hardware AEC. We lose that
 * in MODE_NORMAL, so we use WebRTC's software AEC3 (the same algorithm
 * Discord uses), plus NS (noise suppression) and AGC (auto gain) tuned
 * for gaming environments. Quality is comparable, ducking is gone.
 *
 * ## Implementation note
 *
 * The AudioDeviceModule is built via [AudioDeviceModuleBuilder] — a Java
 * helper. Kotlin's compiler had a metadata-resolution issue with
 * JavaAudioDeviceModule inside stream-webrtc-android 1.3.9 (the audio
 * subpackage's .kotlin_module wasn't being indexed correctly). Java
 * resolves the class via the standard classpath, sidestepping the issue.
 */
object AudioConfig {

    /** Sample rate — 48 kHz matches Opus, the codec we use. */
    const val SAMPLE_RATE_HZ = 48_000

    /**
     * Audio attributes for the WebRTC AudioTrack (what we play back).
     * USAGE_GAME is the magic — this prevents the system from ducking
     * other apps when our voice plays.
     */
    val playbackAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /**
     * Build the WebRTC AudioDeviceModule with our no-ducking config.
     * Delegates to the Java helper.
     */
    fun buildAudioDeviceModule(context: Context): org.webrtc.audio.AudioDeviceModule =
        AudioDeviceModuleBuilder.build(context)

    /**
     * Force the AudioManager into MODE_NORMAL.
     *
     * This is a defensive call — we never call setMode(MODE_IN_COMMUNICATION)
     * anywhere, but some Android skins default weirdly. This guarantees
     * the OS treats us as a media app, not a call app.
     */
    fun enforceNormalMode(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        am.mode = android.media.AudioManager.MODE_NORMAL
    }

    /**
     * Audio constraints passed to the WebRTC PeerConnection when adding
     * the audio track. These enable the software AEC/NS/AGC pipeline.
     */
    fun audioConstraints(): MediaConstraints = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("googTypingNoiseDetection", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("echoCancellation", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("noiseSuppression", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("autoGainControl", "true"))
    }
}
