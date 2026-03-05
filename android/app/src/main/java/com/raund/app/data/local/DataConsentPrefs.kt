package com.raund.app.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores user consent for data processing (152-FZ). Sync and API calls are gated until consent is granted.
 */
class DataConsentPrefs(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isConsentGranted(): Boolean = prefs.getBoolean(KEY_CONSENT_GRANTED, false)

    fun setConsentGranted(granted: Boolean) {
        prefs.edit().putBoolean(KEY_CONSENT_GRANTED, granted).apply()
    }

    companion object {
        private const val PREFS_NAME = "raund_data_consent"
        private const val KEY_CONSENT_GRANTED = "data_consent_granted"
    }
}
