package com.rajatxo.micup.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Tiny SharedPreferences wrapper for MicUp's user-configurable settings.
 *
 * No DataStore / Room — overkill for v1. SharedPreferences is enough
 * for the signaling URL + display name.
 */
class MicUpPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** WebSocket URL of the signaling server. Defaults to the public
     *  placeholder — change in Settings after deploying your own worker. */
    var signalingUrl: String
        get() = prefs.getString(KEY_SIGNALING_URL, DEFAULT_SIGNALING_URL) ?: DEFAULT_SIGNALING_URL
        set(value) { prefs.edit().putString(KEY_SIGNALING_URL, value).apply() }

    /** Display name shown to other participants in a voice room. */
    var displayName: String
        get() = prefs.getString(KEY_DISPLAY_NAME, "Player") ?: "Player"
        set(value) { prefs.edit().putString(KEY_DISPLAY_NAME, value).apply() }

    companion object {
        private const val PREFS_NAME = "micup_prefs"
        private const val KEY_SIGNALING_URL = "signaling_url"
        private const val KEY_DISPLAY_NAME = "display_name"

        /**
         * The default signaling URL — points at a non-existent server.
         * Deploy your own Cloudflare Worker (see server/worker.js +
         * README.md) and update this in Settings → Signaling Server.
         */
        const val DEFAULT_SIGNALING_URL = "wss://relay.micup.app/signal"
    }
}
