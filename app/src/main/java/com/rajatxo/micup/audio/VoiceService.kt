package com.rajatxo.micup.audio

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rajatxo.micup.MainActivity
import com.rajatxo.micup.R
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection

private const val SIGNALING_URL = "wss://micup-signal.chxsexo.workers.dev"

/**
 * Foreground service that OWNS the voice room.
 *
 * The WebRTC peer connections and the signaling WebSocket live here, not in
 * the Activity. That is why swiping MicUp away from the task manager no
 * longer closes the room: the service (and the call) keeps running, with a
 * persistent notification showing "End chat".
 *
 * There is no host role. Ending the call only drops THIS device from the
 * room; the server tells everyone else we left, and the room continues
 * until its last participant leaves.
 *
 * Type: FOREGROUND_SERVICE_TYPE_MICROPHONE (Android 14+ requirement).
 */
class VoiceService : Service() {

    private var webRtc: WebRtcClient? = null
    private var signaling: SignalingClient? = null
    private var myPeerId: String = ""
    private var displayName: String = ""

    // Set while tearing down so late WebRTC/WebSocket callbacks cannot
    // re-post the notification after it has been removed.
    @Volatile
    private var ending = false

    companion object {
        private const val TAG = "MicUp/VoiceService"
        const val NOTIF_ID = 4201
        const val ACTION_JOIN = "com.rajatxo.micup.JOIN_VOICE"
        const val ACTION_END = "com.rajatxo.micup.END_VOICE"
        const val ACTION_MUTE = "com.rajatxo.micup.MUTE_VOICE"
        const val EXTRA_ROOM = "room"
        const val EXTRA_NAME = "name"
        const val EXTRA_MUTED = "muted"

        /** Join a room. Starts the foreground service, which owns the call. */
        fun join(context: Context, room: String, name: String) {
            CallState.roomCode.value = room
            CallState.playerName.value = name
            CallState.errorMessage.value = null
            CallState.isConnecting.value = true
            CallState.status.value = "Connecting..."
            start(context, ACTION_JOIN) { putExtra(EXTRA_ROOM, room).putExtra(EXTRA_NAME, name) }
        }

        /** End only THIS device's call. Other people stay in the room. */
        fun end(context: Context) = start(context, ACTION_END)

        fun setMuted(context: Context, muted: Boolean) =
            start(context, ACTION_MUTE) { putExtra(EXTRA_MUTED, muted) }

        private fun start(context: Context, action: String, extra: (Intent.() -> Unit)? = null) {
            val i = Intent(context, VoiceService::class.java).apply {
                this.action = action
                extra?.invoke(this)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(i)
                } else {
                    context.startService(i)
                }
            } catch (_: Throwable) {
                try { context.startService(i) } catch (_: Throwable) {}
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_JOIN -> {
                startForegroundWithNotification()
                // A second Join while already in a room is a no-op.
                if (webRtc == null) {
                    startCall(
                        intent.getStringExtra(EXTRA_ROOM) ?: "",
                        intent.getStringExtra(EXTRA_NAME) ?: ""
                    )
                }
            }
            ACTION_END -> endCall()
            ACTION_MUTE -> {
                val muted = intent.getBooleanExtra(EXTRA_MUTED, false)
                webRtc?.setMuted(muted)
                CallState.isMuted.value = muted
            }
            else -> {
                // System restarted us with no saved call state (e.g. after a
                // process kill). There is no connection to restore.
                endCall()
            }
        }
        return START_STICKY
    }

    /** Default onTaskRemoved: do nothing. The service and the call survive. */

    private fun startCall(room: String, name: String) {
        ending = false
        myPeerId = (1..8).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
        displayName = name.ifBlank { "Friend" }
        CallState.roomCode.value = room
        CallState.playerName.value = name

        val rtc = WebRtcClient(applicationContext, myPeerId, object : WebRtcClient.Listener {
            override fun onPeerIceStateChanged(peerId: String, state: PeerConnection.IceConnectionState) {
                updatePeer(peerId, state.name)
                val connected = CallState.participants.value.count {
                    it.iceState == "CONNECTED" || it.iceState == "COMPLETED"
                }
                CallState.status.value = when (state) {
                    PeerConnection.IceConnectionState.CHECKING -> "Connecting to friend..."
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> "✓ Connected"
                    PeerConnection.IceConnectionState.FAILED -> "Connection failed"
                    PeerConnection.IceConnectionState.DISCONNECTED -> "Friend disconnected"
                    else -> state.name
                }
                if (connected > 0) CallState.status.value = "✓ Connected"
                updateNotification()
            }

            override fun onIceCandidateForPeer(peerId: String, candidate: IceCandidate) {
                signaling?.send(SignalingMessage.Ice(myPeerId, peerId, candidate.sdp,
                    candidate.sdpMid, candidate.sdpMLineIndex))
            }

            override fun onOfferReadyForPeer(peerId: String, sdp: String) {
                signaling?.send(SignalingMessage.Offer(myPeerId, peerId, sdp))
            }

            override fun onAnswerReadyForPeer(peerId: String, sdp: String) {
                signaling?.send(SignalingMessage.Answer(myPeerId, peerId, sdp))
            }

            override fun onPeerError(peerId: String, message: String) {
                CallState.errorMessage.value = message
            }
        })

        if (!rtc.init()) {
            CallState.errorMessage.value = "WebRTC init failed"
            endCall()
            return
        }
        webRtc = rtc

        val sig = SignalingClient(SIGNALING_URL, myPeerId, object : SignalingClient.Listener {
            override fun onConnected() {
                CallState.status.value = "Waiting for friends..."
                CallState.isConnected.value = true
                CallState.isConnecting.value = false
                signaling?.send(SignalingMessage.Hello(myPeerId, displayName))
                updateNotification()
            }

            override fun onMessage(message: SignalingMessage) {
                when (message) {
                    is SignalingMessage.Hello -> {
                        addPeer(message.from, message.name.ifBlank { "Friend" })
                        if (webRtc?.addPeer(message.from) == true) {
                            webRtc?.maybeCreateOffer(message.from)
                        }
                    }
                    is SignalingMessage.Peers -> {
                        for (p in message.peers) {
                            addPeer(p.id, p.name.ifBlank { "Friend" })
                            if (webRtc?.addPeer(p.id) == true) {
                                webRtc?.maybeCreateOffer(p.id)
                            }
                        }
                    }
                    is SignalingMessage.Offer -> {
                        addPeer(message.from, "Friend")
                        webRtc?.addPeer(message.from)
                        webRtc?.handleRemoteOffer(message.from, message.sdp)
                    }
                    is SignalingMessage.Answer -> {
                        webRtc?.handleRemoteAnswer(message.from, message.sdp)
                    }
                    is SignalingMessage.Ice -> {
                        val candidate = IceCandidate(message.sdpMid, message.sdpMLineIndex, message.sdp)
                        webRtc?.addIceCandidate(message.from, candidate)
                    }
                    is SignalingMessage.Bye -> {
                        webRtc?.removePeer(message.from)
                        CallState.participants.value =
                            CallState.participants.value.filterNot { it.peerId == message.from }
                        updateNotification()
                    }
                    is SignalingMessage.Error -> {
                        val msg = message.message
                        CallState.errorMessage.value = msg
                        CallState.status.value = msg
                        CallState.isConnected.value = false
                        endCall()
                        // endCall() resets state — keep the error visible.
                        CallState.errorMessage.value = msg
                        CallState.status.value = msg
                    }
                }
            }

            override fun onDisconnected(reason: String) {
                val connected = CallState.participants.value.any {
                    it.iceState == "CONNECTED" || it.iceState == "COMPLETED"
                }
                if (!connected) {
                    CallState.status.value = "Disconnected: $reason"
                    CallState.isConnected.value = false
                    updateNotification()
                }
            }

            override fun onError(error: String) {
                CallState.errorMessage.value = error
                CallState.isConnecting.value = false
                if (error.contains("Room is full", ignoreCase = true)) {
                    CallState.isConnected.value = false
                    endCall()
                    CallState.errorMessage.value = error
                }
            }
        })
        signaling = sig
        sig.connect(room, myPeerId)
    }

    private fun addPeer(id: String, name: String) {
        if (id == myPeerId) return
        if (CallState.participants.value.any { it.peerId == id }) return
        CallState.participants.value = CallState.participants.value + CallParticipant(id, name)
    }

    private fun updatePeer(id: String, iceState: String) {
        CallState.participants.value = CallState.participants.value.map {
            if (it.peerId == id) it.copy(iceState = iceState) else it
        }
    }

    /** End only this device's participation; everyone else stays in the room. */
    private fun endCall() {
        ending = true
        try { signaling?.disconnect() } catch (_: Throwable) {}
        signaling = null
        try { webRtc?.release() } catch (_: Throwable) {}
        webRtc = null
        CallState.reset()
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Throwable) {}
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIF_ID)
        } catch (_: Throwable) {}
        stopSelf()
    }

    private fun startForegroundWithNotification() {
        val notif = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIF_ID, notif)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "startForeground failed", t)
        }
    }

    private fun updateNotification() {
        // A callback can still be in flight after End chat (socket close,
        // peer dispose). Don't let it resurrect the notification.
        if (ending || webRtc == null) return
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.notify(NOTIF_ID, buildNotification())
        } catch (_: Throwable) {}
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val endCall = PendingIntent.getService(
            this, 1,
            Intent(this, VoiceService::class.java).apply { action = ACTION_END },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val count = CallState.participants.value.size + 1
        val text = if (CallState.isConnected.value) {
            "Connected · $count in room"
        } else {
            "Waiting for friends…"
        }
        return NotificationCompat.Builder(this, "micup_voice_room")
            .setContentTitle("MicUp — Voice Room")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_splash_icon)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End chat", endCall)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        try { signaling?.disconnect() } catch (_: Throwable) {}
        signaling = null
        try { webRtc?.release() } catch (_: Throwable) {}
        webRtc = null
        super.onDestroy()
    }
}
