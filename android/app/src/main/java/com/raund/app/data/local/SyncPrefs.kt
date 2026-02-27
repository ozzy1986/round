package com.raund.app.data.local

import android.content.Context
import android.content.SharedPreferences
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Stores last sync timestamp for delta sync (updated_since) and throttle (min 30s between syncs).
 */
class SyncPrefs(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLastSyncedAtMillis(): Long? {
        val iso = prefs.getString(KEY_LAST_SYNCED_AT, null) ?: return null
        return try {
            Instant.parse(iso).toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }

    /** ISO-8601 string for API updated_since query param. */
    fun getLastSyncedAtForApi(): String? = prefs.getString(KEY_LAST_SYNCED_AT, null)

    fun setLastSyncedAt(isoTimestamp: String) {
        prefs.edit().putString(KEY_LAST_SYNCED_AT, isoTimestamp).apply()
    }

    fun setLastSyncedAtNow() {
        setLastSyncedAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
    }

    companion object {
        private const val PREFS_NAME = "raund_sync"
        private const val KEY_LAST_SYNCED_AT = "last_synced_at_iso"

        const val SYNC_THROTTLE_MS = 30_000L
        const val CONNECTIVITY_SYNC_DELAY_MS = 60_000L
    }
}
