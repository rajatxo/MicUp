package com.rajatxo.micup.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import org.webrtc.AudioTrack
import org.webrtc.JavaAudioDeviceModule
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
 */
object AudioConfig {

    /** Sample rate — 48 kHz matches Opus, the codec we use. */
    const val SAMPLE_RATE_HZ = 48_000

    /** Stereo playback so directional squad comms come through clean. */
    const val PLAYBACK_CHANNELS = AudioFormat.CHANNEL_IN_STEREO

    /** Mono capture — voice is mono, halves the bandwidth. */
    const val CAPTURE_CHANNELS = AudioFormat.CHANNEL_IN_MONO

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
     * Audio attributes for the recording side — also USAGE_GAME so the
     * mic capture session doesn't promote us into communication mode.
     */
    val captureAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /**
     * Build the WebRTC AudioDeviceModule with our custom config.
     *
     * CRITICAL settings:
     *  - useHardwareAcousticEchoCancel = false  (we use WebRTC's AEC3)
     *  - useHardwareNoiseSuppressor   = false  (we use WebRTC's NS)
     *  - audioSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION
     *    (this is the *Android* audio source tag, NOT a usage tag —
     *     it routes through the system AEC path; we explicitly turn
     *     the system AEC off below and let WebRTC do it in software)
     */
    @SuppressLint("MissingPermission")
    fun buildAudioDeviceModule(context: Context): JavaAudioDeviceModule =
        JavaAudioDeviceModule.builder(context)
            .setSampleRate(SAMPLE_RATE_HZ)
            .setUseHardwareAcousticEchoCanceler(false)
            .setUseHardwareNoiseSuppressor(false)
            // Stereo output so squad comms have directional clarity.
            .setUseStereoOutput(true)
            .setAudioAttributes(playbackAttributes)
            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .createAudioDeviceModule()

    /**
     * Force the AudioManager into MODE_NORMAL.
     *
     * This is a defensive call — we never call setMode(MODE_IN_COMMUNICATION)
     * anywhere, but some Android skins default weirdly. This guarantees
     * the OS treats us as a media app, not a call app.
     */
    fun enforceNormalMode(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.mode = AudioManager.MODE_NORMAL
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
