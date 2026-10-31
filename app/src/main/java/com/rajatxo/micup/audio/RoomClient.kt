package com.rajatxo.micup.audio

import android.content.Context
import android.util.Log
import io.agora.rtc.Constants
import io.agora.rtc.IRtcEngineEventHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages a single voice-room session using Agora.
 *
 * Replaces the WebRTC-based RoomClient. Agora handles all the signaling,
 * NAT traversal, and audio mixing server-side — we just join/leave a
 * channel by name and Agora does the rest.
 *
 * Channel name = room code. Anyone who joins the same channel name
 * (e.g., "ABC234") can talk to each other.
 */
class RoomClient(private val context: Context) {

    companion object {
        private const val TAG = "MicUp/RoomClient"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engine = AgoraVoiceEngine.getInstance(context)

    private val _state = MutableStateFlow<RoomState>(RoomState.Idle)
    val state: StateFlow<RoomState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<RoomEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<RoomEvent> = _events.asSharedFlow()

    /** The Agora event handler — receives callbacks from the SDK. */
    private val eventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            Log.i(TAG, "Joined channel $channel as uid $uid in ${elapsed}ms")
            _state.value = RoomState.Joined(
                roomCode = channel ?: "",
                isHost = false,
                localId = uid.toString(),
                participants = listOf(uid.toString())
            )
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            Log.i(TAG, "Remote user $uid joined")
            scope.launch {
                _events.emit(RoomEvent.PeerJoined(uid.toString(), "Player $uid"))
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            Log.i(TAG, "Remote user $uid left (reason=$reason)")
            scope.launch {
                _events.emit(RoomEvent.PeerLeft(uid.toString()))
            }
        }

        override fun onError(err: Int) {
            Log.e(TAG, "Agora error: $err")
            scope.launch {
                _events.emit(RoomEvent.AgoraError("Agora error code: $err"))
            }
        }

        @Suppress("UNUSED_PARAMETER")
        override fun onAudioVolumeIndication(
            speakers: Array<out AudioVolumeInfo>?,
            totalVolume: Int
        ) {
            // Used for "who's talking" indicators — emits for each speaker
            // with their current volume level.
            speakers?.forEach { info ->
                val talking = info.volume > 30  // arbitrary threshold
                scope.launch {
                    _events.emit(RoomEvent.PeerAudioLevel(info.uid.toString(), talking))
                }
            }
        }
    }

    /** Local user ID — Agora assigns this when we join the channel. */
    private var localUid: Int = 0

    /** Display name — read fresh from prefs. */
    private fun freshLocalName(): String =
        com.rajatxo.micup.data.MicUpPrefs(context).displayName

    // ---------- Public API ----------

    /** Create a new room (becomes host). Channel name = room code. */
    fun createRoom(): kotlinx.coroutines.Job = scope.launch {
        val code = generateRoomCode()
        _state.value = RoomState.Connecting(code, isHost = true)
        if (engine.init(eventHandler) == null) {
            _state.value = RoomState.Failed("Voice engine init failed")
            return@launch
        }
        val result = engine.joinChannel(code, 0)
        if (result != 0) {
            _state.value = RoomState.Failed("Failed to create room (code $result)")
        }
        // State becomes Joined when onJoinChannelSuccess fires.
    }

    /** Join an existing room by its 6-character code. */
    fun joinRoom(code: String): kotlinx.coroutines.Job = scope.launch {
        val upperCode = code.uppercase()
        _state.value = RoomState.Connecting(upperCode, isHost = false)
        if (engine.init(eventHandler) == null) {
            _state.value = RoomState.Failed("Voice engine init failed")
            return@launch
        }
        val result = engine.joinChannel(upperCode, 0)
        if (result != 0) {
            _state.value = RoomState.Failed("Failed to join room (code $result)")
        }
    }

    /** Mute / unmute the local mic. */
    fun setMuted(muted: Boolean) {
        engine.muteLocalAudioStream(muted)
        scope.launch { _events.emit(RoomEvent.LocalMuteChanged(muted)) }
    }

    /**
     * Leave the room. State goes to Idle IMMEDIATELY so the UI navigates
     * back without delay. Cleanup runs in the background.
     *
     * Idempotent — safe to call multiple times rapidly.
     */
    fun leave() {
        _state.value = RoomState.Idle
        scope.launch {
            try { engine.leaveChannel() } catch (_: Throwable) {}
        }
    }

    /** Tear down everything when the app is done with the engine. */
    fun release() {
        try { engine.leaveChannel() } catch (_: Throwable) {}
        try { engine.release() } catch (_: Throwable) {}
        _state.value = RoomState.Idle
    }

    // ---------- Helpers ----------

    private fun generateRoomCode(): String {
        // Crockford-like — exclude I/O/0/1 for readability over the phone.
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { alphabet.random() }.joinToString("")
    }
}

// ---------- State / Events ----------

sealed interface RoomState {
    data object Idle : RoomState
    data class Connecting(val roomCode: String, val isHost: Boolean) : RoomState
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
    data class PeerAudioLevel(val id: String, val talking: Boolean) : RoomEvent
    data class AgoraError(val message: String) : RoomEvent
    data class LocalMuteChanged(val muted: Boolean) : RoomEvent
}
