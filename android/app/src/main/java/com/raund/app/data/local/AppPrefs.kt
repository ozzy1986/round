package com.raund.app.data.local

import android.content.Context
import android.content.SharedPreferences

class AppPrefs(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** When true (default), training keeps running when screen is off. When false, training pauses when screen goes off. */
    var keepRunningWhenScreenOff: Boolean
        get() = prefs.getBoolean(KEY_KEEP_RUNNING_WHEN_SCREEN_OFF, true)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_RUNNING_WHEN_SCREEN_OFF, value).apply()

    companion object {
        private const val PREFS_NAME = "raund_app"
        private const val KEY_KEEP_RUNNING_WHEN_SCREEN_OFF = "keep_running_when_screen_off"
    }
}
