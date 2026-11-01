package com.rajatxo.micup.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores the player's display name. Persisted across app launches.
 */
class MicUpPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("micup_prefs", Context.MODE_PRIVATE)

    var playerName: String
        get() = prefs.getString(KEY_PLAYER_NAME, "") ?: ""
        set(value) { prefs.edit().putString(KEY_PLAYER_NAME, value).apply() }

    companion object {
        private const val KEY_PLAYER_NAME = "player_name"
    }
}
