package com.raund.app

import android.content.Context
import android.content.SharedPreferences

object SettingsManager {
    private const val KEY_KEEP_RUNNING_SCREEN_OFF = "keep_running_screen_off"
    private const val KEY_KEEP_RUNNING_LEAVE_APP = "keep_running_leave_app"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences("raund_settings", Context.MODE_PRIVATE)
    }

    fun isKeepRunningOnScreenOff(context: Context): Boolean =
        prefs(context).getBoolean(KEY_KEEP_RUNNING_SCREEN_OFF, true)

    fun setKeepRunningOnScreenOff(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_KEEP_RUNNING_SCREEN_OFF, value).apply()
    }

    fun isKeepRunningWhenLeavingApp(context: Context): Boolean =
        prefs(context).getBoolean(KEY_KEEP_RUNNING_LEAVE_APP, true)

    fun setKeepRunningWhenLeavingApp(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_KEEP_RUNNING_LEAVE_APP, value).apply()
    }
}
