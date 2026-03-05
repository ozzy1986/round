package com.raund.app.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenStore(context: Context) {
    private val prefs: SharedPreferences = try {
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
        Log.e("TokenStore", "EncryptedSharedPreferences failed; using plain prefs", e)
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also { plain ->
            plain.edit().remove(KEY_TOKEN).remove(KEY_REFRESH_TOKEN).apply()
        }
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun setToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun setTokens(token: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_REFRESH_TOKEN).apply()
    }

    companion object {
        private const val PREFS_NAME = "raund_auth"
        private const val KEY_TOKEN = "token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
    }
}
