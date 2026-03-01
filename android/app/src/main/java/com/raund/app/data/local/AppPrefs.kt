package com.raund.app.data.local

import android.content.Context
import android.content.SharedPreferences

class AppPrefs(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** When true (default), training keeps running when screen is off. When false, training pauses when screen goes off. */
    var keepRunningWhenScreenOff: Boolean
        get() {
            migrateScreenOffPrefIfNeeded()
            return prefs.getBoolean(KEY_KEEP_RUNNING_WHEN_SCREEN_OFF, true)
        }
        set(value) = prefs.edit().putBoolean(KEY_KEEP_RUNNING_WHEN_SCREEN_OFF, value).apply()

    private fun migrateScreenOffPrefIfNeeded() {
        if (prefs.getBoolean(KEY_SCREEN_OFF_MIGRATED, false)) return
        val oldValue = prefs.getBoolean(KEY_PAUSE_ON_SCREEN_OFF_LEGACY, KEY_PAUSE_ON_SCREEN_OFF_LEGACY_DEFAULT)
        val newValue = !oldValue
        prefs.edit()
            .putBoolean(KEY_KEEP_RUNNING_WHEN_SCREEN_OFF, newValue)
            .putBoolean(KEY_SCREEN_OFF_MIGRATED, true)
            .remove(KEY_PAUSE_ON_SCREEN_OFF_LEGACY)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "raund_app"
        private const val KEY_KEEP_RUNNING_WHEN_SCREEN_OFF = "keep_running_when_screen_off"
        private const val KEY_PAUSE_ON_SCREEN_OFF_LEGACY = "pause_on_screen_off"
        private const val KEY_SCREEN_OFF_MIGRATED = "screen_off_pref_migrated"
        private const val KEY_PAUSE_ON_SCREEN_OFF_LEGACY_DEFAULT = false
    }
}
