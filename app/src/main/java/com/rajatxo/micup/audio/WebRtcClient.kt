package com.rajatxo.micup.audio

import android.content.Context
import android.util.Log
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages a single WebRTC peer connection for voice chat.
 *
 * Handles:
 * - PeerConnectionFactory initialization (once)
 * - Audio capture (mic) and playback (speaker)
 * - SDP offer/answer creation and exchange
 * - ICE candidate generation and exchange
 * - Remote audio track rendering (hearing the other person)
 *
 * Call flow:
 * 1. init() — sets up factory + audio track
 * 2. createOffer() — generates SDP offer (caller)
 * 3. createAnswer() — generates SDP answer (callee)
 * 4. setRemoteDescription() — applies offer/answer from remote
 * 5. addIceCandidate() — adds network path info from remote
 * 6. ICE connects → voice flows
 */
class WebRtcClient(
    private val context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onIceConnectionStateChange(state: PeerConnection.IceConnectionState)
        fun onIceCandidate(candidate: IceCandidate)
        fun onOfferReady(sdp: String)
        fun onAnswerReady(sdp: String)
        fun onError(message: String)
    }

    private var factory: PeerConnectionFactory? = null
    private var adm: JavaAudioDeviceModule? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var peerConnection: PeerConnection? = null

    /** Buffer ICE candidates received before remote SDP is set. */
    private val pendingIceCandidates = mutableListOf<IceCandidate>()
    private var remoteSdpSet = false

    fun init(): Boolean {
        if (factory != null) return true
        return try {
            // Initialize WebRTC native lib.
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions
                    .builder(context)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()
            )

            // Build the AudioDeviceModule with NO-DUCKING audio attributes.
            // USAGE_GAME = OS treats us as a game sound → no ducking.
            adm = AudioDeviceModuleBuilder.build(context, null)

            // CRITICAL NO-DUCKING TRICK:
            // 1. Enable loudspeaker (bottom speaker, not earpiece)
            // 2. Force MODE_NORMAL (NOT MODE_IN_COMMUNICATION)
            // This prevents Android from ducking game audio.
            AudioDeviceModuleBuilder.enableLoudspeakerAndNoDucking(context)

            // Create the factory.
            factory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(adm!!)
                .createPeerConnectionFactory()

            // Create audio source + track.
            audioSource = factory!!.createAudioSource(MediaConstraints())
            audioTrack = factory!!.createAudioTrack("ARDAMS", audioSource)
            audioTrack!!.setEnabled(true)

            // Create peer connection with STUN + TURN servers.
            // TURN is REQUIRED for mobile networks — carriers use symmetric
            // NAT which STUN can't traverse. TURN relays the voice through
            // a server when P2P fails.
            //
            // OpenRelay provides free TURN (100GB/month free):
            // https://www.metered.ca/openrelay/
            val iceServers = listOf(
                // Google STUN (free, no auth)
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
                // OpenRelay TURN (free, 100GB/month) — relays voice when P2P fails
                PeerConnection.IceServer.builder(
                    "turn:openrelay.metered.ca:80"
                ).setUsername("openrelayproject").setPassword(
                    "openrelayproject"
                ).createIceServer(),
                PeerConnection.IceServer.builder(
                    "turn:openrelay.metered.ca:443"
                ).setUsername("openrelayproject").setPassword(
                    "openrelayproject"
                ).createIceServer(),
                PeerConnection.IceServer.builder(
                    "turn:openrelay.metered.ca:443?transport=tcp"
                ).setUsername("openrelayproject").setPassword(
                    "openrelayproject"
                ).createIceServer()
            )
            val config = PeerConnection.RTCConfiguration(iceServers).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                iceTransportsType = PeerConnection.IceTransportsType.ALL
                bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
                rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
                // Enable ICE candidate gathering on all interfaces
                // (needed for some mobile networks)
                keyType = PeerConnection.KeyType.ECDSA
            }

            peerConnection = factory!!.createPeerConnection(config, object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {}

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    Log.i(TAG, "ICE state: $state")
                    state?.let { listener.onIceConnectionStateChange(it) }
                }

                override fun onIceConnectionReceivingChange(receiving: Boolean) {}

                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                    Log.i(TAG, "ICE gathering: $state")
                }

                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate?.let { listener.onIceCandidate(it) }
                }

                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

                override fun onAddStream(stream: org.webrtc.MediaStream?) {}

                override fun onRemoveStream(stream: org.webrtc.MediaStream?) {}

                override fun onDataChannel(dc: org.webrtc.DataChannel?) {}

                override fun onRenegotiationNeeded() {}

                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out org.webrtc.MediaStream>?) {
                    // Remote audio track arrived — WebRTC auto-renders it to the speaker.
                    Log.i(TAG, "Remote track added")
                }
            })

            if (peerConnection == null) {
                listener.onError("createPeerConnection returned null")
                return false
            }

            // Add our mic audio track to the peer connection.
            peerConnection!!.addTrack(audioTrack)

            Log.i(TAG, "WebRtcClient initialized")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "init failed", t)
            listener.onError("init: ${t.message}")
            false
        }
    }

    /** Create an SDP offer (caller side). */
    fun createOffer() {
        Log.i(TAG, "Creating offer...")
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                val desc = sdp ?: return
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.i(TAG, "Local description (offer) set")
                    }
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {
                        Log.e(TAG, "setLocalDescription failed: $p0")
                    }
                }, desc)
                listener.onOfferReady(desc.description)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                listener.onError("createOffer: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, MediaConstraints())
    }

    /** Create an SDP answer (callee side, after receiving an offer). */
    fun createAnswer() {
        Log.i(TAG, "Creating answer...")
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                val desc = sdp ?: return
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.i(TAG, "Local description (answer) set")
                    }
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {
                        Log.e(TAG, "setLocalDescription failed: $p0")
                    }
                }, desc)
                listener.onAnswerReady(desc.description)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                listener.onError("createAnswer: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, MediaConstraints())
    }

    /** Rollback the local offer (for perfect negotiation glare handling). */
    fun rollbackOffer() {
        Log.i(TAG, "Rolling back local offer (glare resolution)")
        try {
            // Create a rollback SDP (empty description with ROLLBACK type)
            peerConnection?.setLocalDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    Log.i(TAG, "Rollback successful")
                }
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onCreateFailure(p0: String?) {}
                override fun onSetFailure(p0: String?) {
                    Log.e(TAG, "Rollback failed: $p0")
                }
            }, SessionDescription(SessionDescription.Type.ROLLBACK, ""))
        } catch (t: Throwable) {
            Log.e(TAG, "Rollback exception", t)
        }
    }

    /** Get the current signaling state as a string. */
    fun signalingState(): String {
        return peerConnection?.signalingState()?.name?.lowercase() ?: "closed"
    }

    /** Set the remote SDP (offer or answer). Flushes buffered ICE candidates. */
    fun setRemoteDescription(type: SessionDescription.Type, sdp: String) {
        val desc = SessionDescription(type, sdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.i(TAG, "Remote SDP set (${type})")
                remoteSdpSet = true
                // Flush any buffered ICE candidates.
                synchronized(pendingIceCandidates) {
                    pendingIceCandidates.forEach { peerConnection?.addIceCandidate(it, SimpleIceObserver()) }
                    pendingIceCandidates.clear()
                }
            }
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(error: String?) {
                listener.onError("setRemoteDescription: $error")
            }
        }, desc)
    }

    /** Add a remote ICE candidate. Buffers if remote SDP not yet set. */
    fun addIceCandidate(candidate: IceCandidate) {
        if (remoteSdpSet) {
            peerConnection?.addIceCandidate(candidate, SimpleIceObserver())
        } else {
            synchronized(pendingIceCandidates) {
                pendingIceCandidates.add(candidate)
            }
            Log.i(TAG, "Buffered ICE candidate (remote SDP not set yet)")
        }
    }

    /** Mute/unmute the local mic. */
    fun setMuted(muted: Boolean) {
        audioTrack?.setEnabled(!muted)
    }

    /** Tear down everything. */
    fun release() {
        try { audioTrack?.dispose() } catch (_: Throwable) {}
        try { audioSource?.dispose() } catch (_: Throwable) {}
        try { peerConnection?.dispose() } catch (_: Throwable) {}
        try { factory?.dispose() } catch (_: Throwable) {}
        try { adm?.release() } catch (_: Throwable) {}
        audioTrack = null
        audioSource = null
        peerConnection = null
        factory = null
        adm = null
        remoteSdpSet = false
        synchronized(pendingIceCandidates) { pendingIceCandidates.clear() }
        Log.i(TAG, "WebRtcClient released")
    }

    companion object {
        private const val TAG = "MicUp/WebRtc"
    }
}

class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}

class SimpleIceObserver : org.webrtc.AddIceObserver {
    override fun onAddSuccess() {}
    override fun onAddFailure(error: String?) {}
}
