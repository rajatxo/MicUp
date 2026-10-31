package com.rajatxo.micup.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Tiny SharedPreferences wrapper for MicUp's user-configurable settings.
 *
 * With Agora handling all signaling, the only user-configurable setting
 * is the display name. Agora's App ID is baked into BuildConfig at
 * build time.
 */
class MicUpPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Display name shown to other participants in a voice room. */
    var displayName: String
        get() = prefs.getString(KEY_DISPLAY_NAME, "Player") ?: "Player"
        set(value) { prefs.edit().putString(KEY_DISPLAY_NAME, value).apply() }

    companion object {
        private const val PREFS_NAME = "micup_prefs"
        private const val KEY_DISPLAY_NAME = "display_name"
    }
}
