package com.rajatxo.micup.audio

import android.content.Context
import android.util.Log
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * Owns the WebRTC PeerConnectionFactory and the local audio track.
 *
 * Created once per voice-room session and torn down when the user leaves.
 *
 * The key trick: we ask WebRTC for an audio track that uses our custom
 * [AudioConfig.playbackAttributes] (USAGE_GAME) — so the OS doesn't
 * duck the game's audio when our voice plays.
 *
 * MicUp is an AUDIO-ONLY app — no video encoder/decoder factories are
 * built, which keeps the binary smaller and avoids pulling in EGL.
 */
class VoiceEngine(private val context: Context) {

    private var factory: PeerConnectionFactory? = null
    private var audioDeviceModule: JavaAudioDeviceModule? = null
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    /** Initialised lazily so the first call doesn't block app start. */
    fun init(): Boolean {
        if (factory != null) return true
        return try {
            // Force normal audio mode BEFORE we touch the mic.
            AudioConfig.enforceNormalMode(context)

            audioDeviceModule = AudioConfig.buildAudioDeviceModule(context)

            factory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(audioDeviceModule!!)
                .createPeerConnectionFactory()

            // Build the local mic source.
            val constraints = AudioConfig.audioConstraints()
            localAudioSource = factory!!.createAudioSource(constraints)
            localAudioTrack = factory!!.createAudioTrack(
                AUDIO_TRACK_ID,
                localAudioSource
            ).apply {
                // Volume 1.0 = unity gain. We do NOT want any software attenuation.
                setVolume(1.0)
                setEnabled(true)
            }

            Log.i(TAG, "VoiceEngine initialised — USAGE_GAME, MODE_NORMAL, software AEC3")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "VoiceEngine init failed", t)
            false
        }
    }

    /** The local audio track to attach to outbound PeerConnections. */
    fun localAudioTrack(): AudioTrack? = localAudioTrack

    /** Build a new PeerConnection with our preferred ICE servers. */
    fun createPeerConnection(
        iceServers: List<PeerConnection.IceServer>,
        observer: PeerConnection.Observer
    ): PeerConnection? {
        val f = factory ?: return null
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtpMuxPolicy.REQUIRE
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            keyType = PeerConnection.KeyType.ECDSA
        }
        return f.createPeerConnection(config, observer)
    }

    /** Tear down everything. Safe to call multiple times. */
    fun release() {
        localAudioTrack?.dispose()
        localAudioSource?.dispose()
        factory?.dispose()
        audioDeviceModule?.release()
        localAudioTrack = null
        localAudioSource = null
        factory = null
        audioDeviceModule = null
        AudioConfig.enforceNormalMode(context)
        Log.i(TAG, "VoiceEngine released")
    }

    companion object {
        private const val TAG = "MicUp/VoiceEngine"
        private const val AUDIO_TRACK_ID = "micup_local_audio"

        /**
         * Free STUN servers (Google's public ones). Good enough for NAT
         * traversal on most home/mobile networks. TURN would be needed
         * for symmetric NATs — out of scope for v1.
         */
        val defaultIceServers: List<PeerConnection.IceServer> = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302").createIceServer()
        )
    }
}
