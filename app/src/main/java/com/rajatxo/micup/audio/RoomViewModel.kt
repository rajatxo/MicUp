package com.rajatxo.micup.audio

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Bridges [RoomClient] (audio + signaling) with the Compose UI.
 *
 * Exposes:
 *  - [state]: the room session state (Idle / Joined / Failed)
 *  - [participants]: who is currently in the room (live)
 *  - [isMuted]: whether the local mic is muted
 *
 * Survives configuration changes (rotation) because it's scoped to the
 * Activity lifecycle via AndroidViewModel.
 */
class RoomViewModel(app: Application) : AndroidViewModel(app) {

    private val client = RoomClient(app.applicationContext)
    private val _participants = MutableStateFlow<List<Participant>>(emptyList())
    private val _isMuted = MutableStateFlow(false)
    private val _navigated = MutableStateFlow(false)

    val state: StateFlow<RoomState> = client.state
    val participants: StateFlow<List<Participant>> = _participants.asStateFlow()
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    init {
        // Listen to peer events and update the participants list.
        viewModelScope.launch {
            client.events.collect { ev ->
                when (ev) {
                    is RoomEvent.PeerJoined -> _participants.update { it + Participant(ev.id, ev.name, talking = false, connected = false) }
                    is RoomEvent.PeerLeft, is RoomEvent.PeerDisconnected -> _participants.update { list ->
                        list.filterNot { it.id == ev.id }
                    }
                    is RoomEvent.PeerConnected -> _participants.update { list ->
                        list.map { if (it.id == ev.id) it.copy(connected = true) else it }
                    }
                    is RoomEvent.PeerAudioReady -> _participants.update { list ->
                        list.map { if (it.id == ev.id) it.copy(talking = true) else it }
                    }
                    is RoomEvent.LocalMuteChanged -> _isMuted.value = ev.muted
                }
            }
        }
    }

    fun createRoom() = viewModelScope.launch { client.createRoom() }
    fun joinRoom(code: String) = viewModelScope.launch { client.joinRoom(code) }

    fun toggleMute() {
        val newMuted = !_isMuted.value
        client.setMuted(newMuted)
    }

    fun leave() = viewModelScope.launch { client.leave() }

    /** Called by the screen after navigating so we don't re-navigate on rotate. */
    fun resetNavigation() { _navigated.value = false }

    override fun onCleared() {
        super.onCleared()
        client.leave()
    }
}

data class Participant(
    val id: String,
    val name: String,
    val talking: Boolean,
    val connected: Boolean
)

class RoomViewModelFactory(private val app: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = RoomViewModel(app) as T
}
