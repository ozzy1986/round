package com.raund.app.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenStore(context: Context) {
    private val prefs: SharedPreferences?
    private val lock = Any()

    @Volatile
    private var memoryToken: String? = null

    @Volatile
    private var memoryRefreshToken: String? = null

    init {
        prefs = try {
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context.applicationContext,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("TokenStore", "EncryptedSharedPreferences failed; using in-memory tokens only", e)
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .apply()
            null
        }
    }

    fun getToken(): String? = prefs?.getString(KEY_TOKEN, null) ?: synchronized(lock) { memoryToken }

    fun getRefreshToken(): String? =
        prefs?.getString(KEY_REFRESH_TOKEN, null) ?: synchronized(lock) { memoryRefreshToken }

    fun setToken(token: String) {
        val storedPrefs = prefs
        if (storedPrefs != null) {
            safeCommit(storedPrefs) {
                putString(KEY_TOKEN, token)
            }
            return
        }

        synchronized(lock) {
            memoryToken = token
        }
    }

    fun setTokens(token: String, refreshToken: String) {
        val storedPrefs = prefs
        if (storedPrefs != null) {
            safeCommit(storedPrefs) {
                putString(KEY_TOKEN, token)
                putString(KEY_REFRESH_TOKEN, refreshToken)
            }
            return
        }

        synchronized(lock) {
            memoryToken = token
            memoryRefreshToken = refreshToken
        }
    }

    fun clearToken() {
        val storedPrefs = prefs
        if (storedPrefs != null) {
            safeCommit(storedPrefs) {
                remove(KEY_TOKEN)
                remove(KEY_REFRESH_TOKEN)
            }
            return
        }

        synchronized(lock) {
            memoryToken = null
            memoryRefreshToken = null
        }
    }

    private inline fun safeCommit(
        prefs: SharedPreferences,
        edit: SharedPreferences.Editor.() -> Unit
    ) {
        val editor = prefs.edit().apply(edit)
        try {
            if (!editor.commit()) {
                Log.w("TokenStore", "commit() returned false; using apply()")
                prefs.edit().apply(edit).apply()
            }
        } catch (e: Exception) {
            Log.w("TokenStore", "commit() failed; using apply()", e)
            prefs.edit().apply(edit).apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "raund_auth"
        private const val KEY_TOKEN = "token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
    }
}
