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
 * Multi-peer WebRTC client — manages a MESH of peer connections.
 *
 * For each remote peer in the room, we create a separate PeerConnection.
 * Each pair does its own perfect negotiation (lower peer ID = caller).
 *
 * Mesh limits:
 * - 2 people: 1 connection each (perfect)
 * - 3 people: 2 connections each (great)
 * - 4 people: 3 connections each (works, may get heavy on weak networks)
 * - 5+ people: would need an SFU server (not supported)
 *
 * Hotspot fix: iceTransportPolicy = RELAY forces TURN relay when direct
 * P2P fails (which happens when both phones share a hotspot).
 */
class WebRtcClient(
    private val context: Context,
    private val myPeerId: String,
    private val listener: Listener
) {
    interface Listener {
        fun onPeerIceStateChanged(peerId: String, state: PeerConnection.IceConnectionState)
        fun onIceCandidateForPeer(peerId: String, candidate: IceCandidate)
        fun onOfferReadyForPeer(peerId: String, sdp: String)
        fun onAnswerReadyForPeer(peerId: String, sdp: String)
        fun onPeerError(peerId: String, message: String)
    }

    private var factory: PeerConnectionFactory? = null
    private var adm: JavaAudioDeviceModule? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null

    /** Map of remotePeerId → PeerConnectionEntry (one per remote peer). */
    private val peers = ConcurrentHashMap<String, PeerEntry>()

    private data class PeerEntry(
        val peerId: String,
        val connection: PeerConnection,
        var makingOffer: Boolean = false,
        var ignoreOffer: Boolean = false,
        var remoteSdpSet: Boolean = false,
        val pendingIceCandidates: MutableList<IceCandidate> = mutableListOf()
    )

    fun init(): Boolean {
        if (factory != null) return true
        return try {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions
                    .builder(context)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()
            )

            adm = AudioDeviceModuleBuilder.build(context, null)
            AudioDeviceModuleBuilder.enableLoudspeakerAndNoDucking(context)

            factory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(adm!!)
                .createPeerConnectionFactory()

            audioSource = factory!!.createAudioSource(MediaConstraints())
            audioTrack = factory!!.createAudioTrack("ARDAMS", audioSource)
            audioTrack!!.setEnabled(true)

            Log.i(TAG, "WebRtcClient initialized (myPeerId=$myPeerId)")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "init failed", t)
            false
        }
    }

    /**
     * Create a peer connection for a remote peer (if not already exists).
     * Called when we receive a Hello from a new peer.
     * Returns true if this is a new peer (and we should create an offer
     * if we're the caller for this pair).
     */
    fun addPeer(remotePeerId: String): Boolean {
        if (peers.containsKey(remotePeerId)) return false

        Log.i(TAG, "Adding peer: $remotePeerId")

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            // OpenRelay free TURN — relays voice when P2P fails (hotspot, symmetric NAT)
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
                .setUsername("openrelayproject").setPassword("openrelayproject").createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
                .setUsername("openrelayproject").setPassword("openrelayproject").createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp")
                .setUsername("openrelayproject").setPassword("openrelayproject").createIceServer()
        )
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            keyType = PeerConnection.KeyType.ECDSA
        }

        val connection = factory!!.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.i(TAG, "[$remotePeerId] ICE state: $state")
                state?.let { listener.onPeerIceStateChanged(remotePeerId, it) }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}

            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let { listener.onIceCandidateForPeer(remotePeerId, it) }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: org.webrtc.MediaStream?) {}
            override fun onRemoveStream(stream: org.webrtc.MediaStream?) {}
            override fun onDataChannel(dc: org.webrtc.DataChannel?) {}
            override fun onRenegotiationNeeded() {}

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out org.webrtc.MediaStream>?) {
                Log.i(TAG, "[$remotePeerId] Remote track added")
            }
        }) ?: run {
            listener.onPeerError(remotePeerId, "createPeerConnection returned null")
            return false
        }

        // Add our mic audio track to this peer connection.
        connection.addTrack(audioTrack)

        peers[remotePeerId] = PeerEntry(remotePeerId, connection)
        return true
    }

    /**
     * Perfect negotiation: caller (lower peer ID) creates the offer.
     */
    fun maybeCreateOffer(remotePeerId: String) {
        val entry = peers[remotePeerId] ?: return
        // Lower peer ID = caller
        if (myPeerId >= remotePeerId) return // we're the callee, wait for offer
        if (entry.makingOffer) return // already making an offer

        entry.makingOffer = true
        Log.i(TAG, "[$remotePeerId] Creating offer (we are caller)")
        entry.connection.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                val desc = sdp ?: return
                entry.connection.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() { Log.i(TAG, "[$remotePeerId] Local offer set") }
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) { Log.e(TAG, "[$remotePeerId] setLocalOffer failed: $p0") }
                }, desc)
                entry.makingOffer = false
                listener.onOfferReadyForPeer(remotePeerId, desc.description)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                entry.makingOffer = false
                listener.onPeerError(remotePeerId, "createOffer: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, MediaConstraints())
    }

    /**
     * Handle an incoming offer from a remote peer (perfect negotiation).
     */
    fun handleRemoteOffer(remotePeerId: String, sdp: String) {
        val entry = peers[remotePeerId] ?: return
        val offerCollision = entry.makingOffer || entry.connection.signalingState() == PeerConnection.SignalingState.HAVE_LOCAL_OFFER
        val polite = myPeerId > remotePeerId // callee is polite

        entry.ignoreOffer = !polite && offerCollision
        if (entry.ignoreOffer) {
            Log.i(TAG, "[$remotePeerId] Ignoring offer (impolite glare)")
            return
        }

        if (offerCollision) {
            Log.i(TAG, "[$remotePeerId] Glare — rolling back our offer (polite)")
            entry.connection.setLocalDescription(object : SdpObserver {
                override fun onSetSuccess() { Log.i(TAG, "[$remotePeerId] Rollback OK") }
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onCreateFailure(p0: String?) {}
                override fun onSetFailure(p0: String?) { Log.e(TAG, "[$remotePeerId] Rollback failed: $p0") }
            }, SessionDescription(SessionDescription.Type.ROLLBACK, ""))
        }

        entry.connection.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.i(TAG, "[$remotePeerId] Remote offer set")
                entry.remoteSdpSet = true
                flushPendingIce(remotePeerId)
                // Create answer
                entry.connection.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription?) {
                        val desc = sdp ?: return
                        entry.connection.setLocalDescription(object : SdpObserver {
                            override fun onSetSuccess() { Log.i(TAG, "[$remotePeerId] Local answer set") }
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onCreateFailure(p0: String?) {}
                            override fun onSetFailure(p0: String?) { Log.e(TAG, "[$remotePeerId] setLocalAnswer failed: $p0") }
                        }, desc)
                        listener.onAnswerReadyForPeer(remotePeerId, desc.description)
                    }
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(error: String?) {
                        listener.onPeerError(remotePeerId, "createAnswer: $error")
                    }
                    override fun onSetFailure(error: String?) {}
                }, MediaConstraints())
            }
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(error: String?) {
                listener.onPeerError(remotePeerId, "setRemoteOffer: $error")
            }
        }, SessionDescription(SessionDescription.Type.OFFER, sdp))
    }

    /**
     * Handle an incoming answer from a remote peer.
     */
    fun handleRemoteAnswer(remotePeerId: String, sdp: String) {
        val entry = peers[remotePeerId] ?: return
        if (entry.ignoreOffer) {
            Log.i(TAG, "[$remotePeerId] Ignoring answer (glare)")
            return
        }
        entry.connection.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.i(TAG, "[$remotePeerId] Remote answer set")
                entry.remoteSdpSet = true
                flushPendingIce(remotePeerId)
            }
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(error: String?) {
                listener.onPeerError(remotePeerId, "setRemoteAnswer: $error")
            }
        }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
    }

    /**
     * Add a remote ICE candidate for a specific peer.
     */
    fun addIceCandidate(remotePeerId: String, candidate: IceCandidate) {
        val entry = peers[remotePeerId] ?: return
        if (entry.remoteSdpSet) {
            entry.connection.addIceCandidate(candidate, object : org.webrtc.AddIceObserver {
                override fun onAddSuccess() {}
                override fun onAddFailure(error: String?) { Log.e(TAG, "[$remotePeerId] addIce failed: $error") }
            })
        } else {
            synchronized(entry.pendingIceCandidates) {
                entry.pendingIceCandidates.add(candidate)
            }
            Log.i(TAG, "[$remotePeerId] Buffered ICE candidate")
        }
    }

    private fun flushPendingIce(remotePeerId: String) {
        val entry = peers[remotePeerId] ?: return
        synchronized(entry.pendingIceCandidates) {
            entry.pendingIceCandidates.forEach { cand ->
                entry.connection.addIceCandidate(cand, object : org.webrtc.AddIceObserver {
                    override fun onAddSuccess() {}
                    override fun onAddFailure(error: String?) {}
                })
            }
            entry.pendingIceCandidates.clear()
        }
    }

    /** Remove a peer (when they leave). */
    fun removePeer(remotePeerId: String) {
        val entry = peers.remove(remotePeerId) ?: return
        try { entry.connection.dispose() } catch (_: Throwable) {}
        Log.i(TAG, "[$remotePeerId] Removed")
    }

    /** Mute/unmute the local mic (affects all peer connections). */
    fun setMuted(muted: Boolean) {
        audioTrack?.setEnabled(!muted)
    }

    /** Get all connected peer IDs. */
    fun getConnectedPeers(): Set<String> = peers.keys

    /** Tear down everything. */
    fun release() {
        peers.values.forEach { entry ->
            try { entry.connection.dispose() } catch (_: Throwable) {}
        }
        peers.clear()
        try { audioTrack?.dispose() } catch (_: Throwable) {}
        try { audioSource?.dispose() } catch (_: Throwable) {}
        try { factory?.dispose() } catch (_: Throwable) {}
        try { adm?.release() } catch (_: Throwable) {}
        audioTrack = null
        audioSource = null
        factory = null
        adm = null
        Log.i(TAG, "WebRtcClient released")
    }

    companion object {
        private const val TAG = "MicUp/WebRtc"
    }
}
