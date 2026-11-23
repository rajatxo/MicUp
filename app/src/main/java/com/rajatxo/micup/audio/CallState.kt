package com.rajatxo.micup.audio

import kotlinx.coroutines.flow.MutableStateFlow

/** One row in the room list shown by the UI. */
data class CallParticipant(
    val peerId: String,
    val name: String,
    val iceState: String = "new"
)

/**
 * Shared call state. Owned by VoiceService, observed by MainActivity.
 *
 * The service owns the WebRTC/signaling objects so the room survives the
 * user swiping the app away to open their game. StateFlow is thread-safe,
 * so the service's WebSocket/WebRTC callbacks can update it directly.
 */
object CallState {
    val roomCode = MutableStateFlow("")
    val playerName = MutableStateFlow("")
    val status = MutableStateFlow("Enter a room code to start")
    val isConnected = MutableStateFlow(false)
    val isConnecting = MutableStateFlow(false)
    val isMuted = MutableStateFlow(false)
    val participants = MutableStateFlow<List<CallParticipant>>(emptyList())
    val errorMessage = MutableStateFlow<String?>(null)

    fun reset() {
        roomCode.value = ""
        status.value = "Enter a room code to start"
        isConnected.value = false
        isConnecting.value = false
        isMuted.value = false
        participants.value = emptyList()
        errorMessage.value = null
    }
}
