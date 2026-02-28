package com.raund.app

import android.content.Context
import android.content.SharedPreferences

object SettingsManager {
    private const val PREFS = "raund_settings"
    private const val KEY_SCREEN_OFF_PAUSE = "screen_off_pause"

    fun isScreenOffPause(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_SCREEN_OFF_PAUSE, true)
    }

    fun setScreenOffPause(context: Context, pause: Boolean) {
        prefs(context).edit().putBoolean(KEY_SCREEN_OFF_PAUSE, pause).apply()
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
}
