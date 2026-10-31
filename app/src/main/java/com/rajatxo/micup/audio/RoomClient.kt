package com.rajatxo.micup.audio

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.webrtc.AddIceObserver
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages a single voice-room session.
 *
 * Uses a P2P full-mesh topology — every participant has a direct
 * PeerConnection with every other participant. Works great for
 * 2-6 person squads (Free Fire / BGMI / CODM standard party size).
 * For larger rooms we'd need an SFU server — out of scope for v1.
 *
 * Signaling flows over a WebSocket relay. Default URL points at a
 * public echo broker; can be swapped in Settings for a self-hosted one.
 */
class RoomClient(
    private val context: Context,
    private val signalingUrl: String = DEFAULT_SIGNALING_URL
) {
    companion object {
        private const val TAG = "MicUp/RoomClient"
        // Default broker — a public, free WebSocket relay that we use as a
        // signaling "message board". Replaceable from Settings.
        const val DEFAULT_SIGNALING_URL = "wss://relay.micup.app/signal"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var signaling: SignalingClient? = null
    private val engine = VoiceEngine(context)
    private val peers = ConcurrentHashMap<String, PeerConnection>()
    private val iceCandidatesBuffer = ConcurrentHashMap<String, MutableList<IceCandidate>>()

    private val _state = MutableStateFlow<RoomState>(RoomState.Idle)
    val state: StateFlow<RoomState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<RoomEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<RoomEvent> = _events.asSharedFlow()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Local participant ID (random per session). */
    private val localId: String = randomId()

    /** Display name — set by the user, persisted in shared prefs. */
    var localName: String = "Player"
        private set

    fun setLocalName(name: String) { localName = name }

    // ---------- Public API ----------

    /** Create a new room. The room code is returned via [RoomState.Joined]. */
    fun createRoom(): Job = scope.launch {
        if (!engine.init()) {
            _state.value = RoomState.Failed("Voice engine init failed")
            return@launch
        }
        val code = generateRoomCode()
        connectSignaling(code, isHost = true)
    }

    /** Join an existing room by its 6-character code. */
    fun joinRoom(code: String): Job = scope.launch {
        if (!engine.init()) {
            _state.value = RoomState.Failed("Voice engine init failed")
            return@launch
        }
        connectSignaling(code.uppercase(), isHost = false)
    }

    /** Mute / unmute the local mic. */
    fun setMuted(muted: Boolean) {
        engine.localAudioTrack()?.let { it.setEnabled(!muted) }
        scope.launch { _events.emit(RoomEvent.LocalMuteChanged(muted)) }
    }

    /** Leave the room and tear everything down. */
    fun leave() = scope.launch {
        try {
            signaling?.send(json.encodeToString(SignalMessage.serializer(),
                SignalMessage.Bye(localId)))
        } catch (_: Throwable) {}
        peers.values.forEach { runCatching { it.dispose() } }
        peers.clear()
        iceCandidatesBuffer.clear()
        signaling?.disconnect()
        signaling = null
        engine.release()
        _state.value = RoomState.Idle
    }

    // ---------- Signaling ----------

    private fun connectSignaling(roomCode: String, isHost: Boolean) {
        signaling = SignalingClient(
            url = "$signalingUrl?room=$roomCode&peer=$localId",
            onOpen = {
                _state.value = RoomState.Joined(
                    roomCode = roomCode,
                    isHost = isHost,
                    localId = localId,
                    participants = listOf(localId)
                )
                // Announce ourselves to the room.
                signaling?.send(json.encodeToString(SignalMessage.serializer(),
                    SignalMessage.Hello(localId, localName, isHost)))
            },
            onMessage = ::handleSignal,
            onClose = { _state.value = RoomState.Idle },
            onError = { _state.value = RoomState.Failed(it) }
        ).also { it.connect() }
    }

    private fun handleSignal(raw: String) {
        val msg = runCatching { json.decodeFromString(SignalMessage.serializer(), raw) }.getOrNull()
            ?: return
        when (msg) {
            is SignalMessage.Hello -> {
                // A new peer announced themselves. The existing peer with the
                // lexicographically smaller ID initiates the offer — this avoids
                // both sides racing to create offers.
                if (localId < msg.id) {
                    createOfferFor(msg.id, msg.name)
                }
                _events.tryEmit(RoomEvent.PeerJoined(msg.id, msg.name))
            }
            is SignalMessage.Offer -> receiveOffer(msg.from, msg.sdp)
            is SignalMessage.Answer -> receiveAnswer(msg.from, msg.sdp)
            is SignalMessage.Ice -> receiveIce(msg.from, msg.candidate)
            is SignalMessage.Bye -> removePeer(msg.id)
        }
    }

    // ---------- PeerConnection lifecycle ----------

    private fun createOfferFor(peerId: String, peerName: String) {
        val pc = makePeerConnection(peerId) ?: return
        peers[peerId] = pc

        pc.addTrack(engine.localAudioTrack())
        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                pc.setLocalDescription(SimpleSdpObserver(), sdp)
                signaling?.send(json.encodeToString(SignalMessage.serializer(),
                    SignalMessage.Offer(localId, sdp!!.description)))
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) { Log.e(TAG, "offer create fail: $error") }
            override fun onSetFailure(error: String?) {}
        }, AudioConfig.audioConstraints())
    }

    private fun receiveOffer(fromPeer: String, sdp: String) {
        val pc = peers[fromPeer] ?: makePeerConnection(fromPeer)?.also { peers[fromPeer] = it } ?: return
        pc.setRemoteDescription(SimpleSdpObserver(), SessionDescription(SessionDescription.Type.OFFER, sdp))
        pc.addTrack(engine.localAudioTrack())
        pc.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(answerSdp: SessionDescription?) {
                pc.setLocalDescription(SimpleSdpObserver(), answerSdp)
                signaling?.send(json.encodeToString(SignalMessage.serializer(),
                    SignalMessage.Answer(localId, answerSdp!!.description)))
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) {}
        }, AudioConfig.audioConstraints())
    }

    private fun receiveAnswer(fromPeer: String, sdp: String) {
        val pc = peers[fromPeer] ?: return
        pc.setRemoteDescription(SimpleSdpObserver(), SessionDescription(SessionDescription.Type.ANSWER, sdp))
        // Flush any buffered ICE candidates.
        iceCandidatesBuffer.remove(fromPeer)?.forEach { pc.addIceCandidate(it, SimpleIceObserver()) }
    }

    private fun receiveIce(fromPeer: String, candidate: IceCandidateJson) {
        val pc = peers[fromPeer]
        val ice = candidate.toWebRtc()
        if (pc == null || pc.remoteDescription == null) {
            // Buffer until remote SDP is set.
            iceCandidatesBuffer.getOrPut(fromPeer) { mutableListOf() }.add(ice)
        } else {
            pc.addIceCandidate(ice, SimpleIceObserver())
        }
    }

    private fun removePeer(peerId: String) {
        peers.remove(peerId)?.dispose()
        iceCandidatesBuffer.remove(peerId)
        _events.tryEmit(RoomEvent.PeerLeft(peerId))
    }

    private fun makePeerConnection(peerId: String): PeerConnection? {
        return engine.createPeerConnection(VoiceEngine.defaultIceServers, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                if (state == PeerConnection.IceConnectionState.CONNECTED) {
                    _events.tryEmit(RoomEvent.PeerConnected(peerId))
                }
                if (state == PeerConnection.IceConnectionState.DISCONNECTED ||
                    state == PeerConnection.IceConnectionState.FAILED) {
                    _events.tryEmit(RoomEvent.PeerDisconnected(peerId))
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate ?: return
                signaling?.send(json.encodeToString(SignalMessage.serializer(),
                    SignalMessage.Ice(localId, IceCandidateJson.fromWebRtc(candidate))))
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(dc: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                _events.tryEmit(RoomEvent.PeerAudioReady(peerId))
            }
        })
    }

    // ---------- Helpers ----------

    private fun generateRoomCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"  // no I/O/0/1 for readability
        return (1..6).map { alphabet.random() }.joinToString("")
    }

    private fun randomId(): String =
        (1..12).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
}

// ---------- State / Events / Signaling DTOs ----------

sealed interface RoomState {
    data object Idle : RoomState
    data class Joined(
        val roomCode: String,
        val isHost: Boolean,
        val localId: String,
        val participants: List<String>
    ) : RoomState
    data class Failed(val message: String) : RoomState
}

sealed interface RoomEvent {
    data class PeerJoined(val id: String, val name: String) : RoomEvent
    data class PeerLeft(val id: String) : RoomEvent
    data class PeerConnected(val id: String) : RoomEvent
    data class PeerDisconnected(val id: String) : RoomEvent
    data class PeerAudioReady(val id: String) : RoomEvent
    data class LocalMuteChanged(val muted: Boolean) : RoomEvent
}

@Serializable
sealed interface SignalMessage {
    @Serializable data class Hello(val id: String, val name: String, val isHost: Boolean) : SignalMessage
    @Serializable data class Offer(val from: String, val sdp: String) : SignalMessage
    @Serializable data class Answer(val from: String, val sdp: String) : SignalMessage
    @Serializable data class Ice(val from: String, val candidate: IceCandidateJson) : SignalMessage
    @Serializable data class Bye(val id: String) : SignalMessage
}

@Serializable
data class IceCandidateJson(
    val sdp: String,
    val sdpMid: String?,
    val sdpMLineIndex: Int
) {
    fun toWebRtc(): IceCandidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
    companion object {
        fun fromWebRtc(c: IceCandidate): IceCandidateJson =
            IceCandidateJson(c.sdp, c.sdpMid, c.sdpMLineIndex)
    }
}

/** No-op SdpObserver — MicUp doesn't need to react to set-success. */
class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}

/** No-op AddIceObserver — we don't need to react when ICE candidates land. */
class SimpleIceObserver : AddIceObserver {
    override fun onAddSuccess() {}
    override fun onAddFailure(error: String?) {}
}
