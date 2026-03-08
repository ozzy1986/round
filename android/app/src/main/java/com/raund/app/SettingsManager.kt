package com.raund.app

import android.content.Context
import android.content.SharedPreferences

data class TimerRuntimeSettings(
    val keepRunningOnScreenOff: Boolean,
    val keepRunningWhenLeavingApp: Boolean
)

object SettingsManager {
    private const val KEY_KEEP_RUNNING_SCREEN_OFF = "keep_running_screen_off"
    private const val KEY_KEEP_RUNNING_LEAVE_APP = "keep_running_leave_app"
    private const val PREFS_NAME = "raund_settings"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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

    fun timerRuntimeSettings(context: Context): TimerRuntimeSettings = TimerRuntimeSettings(
        keepRunningOnScreenOff = isKeepRunningOnScreenOff(context),
        keepRunningWhenLeavingApp = isKeepRunningWhenLeavingApp(context)
    )

    fun registerListener(
        context: Context,
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        prefs(context).registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(
        context: Context,
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        prefs(context).unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun isTimerRuntimeSetting(key: String?): Boolean =
        key == KEY_KEEP_RUNNING_SCREEN_OFF || key == KEY_KEEP_RUNNING_LEAVE_APP
}
