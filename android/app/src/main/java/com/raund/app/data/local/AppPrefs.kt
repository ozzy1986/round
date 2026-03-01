package com.raund.app.data.local

import android.content.Context
import android.content.SharedPreferences

class AppPrefs(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var pauseOnScreenOff: Boolean
        get() = prefs.getBoolean(KEY_PAUSE_ON_SCREEN_OFF, false)
        set(value) = prefs.edit().putBoolean(KEY_PAUSE_ON_SCREEN_OFF, value).apply()

    companion object {
        private const val PREFS_NAME = "raund_app"
        private const val KEY_PAUSE_ON_SCREEN_OFF = "pause_on_screen_off"
    }
}
