package com.raund.app

import android.content.Context
import android.content.SharedPreferences

object SettingsManager {
    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences("raund_settings", Context.MODE_PRIVATE)
    }
}
