package com.raund.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class TokenStoreTest {

    private lateinit var context: Context
    private lateinit var tokenStore: TokenStore

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("raund_auth", Context.MODE_PRIVATE).edit().clear().commit()
        tokenStore = TokenStore(context)
    }

    @Test
    fun `getToken returns null when no token stored`() {
        assertNull(tokenStore.getToken())
    }

    @Test
    fun `getRefreshToken returns null when no refresh token stored`() {
        assertNull(tokenStore.getRefreshToken())
    }

    @Test
    fun `setToken stores and retrieves token`() {
        tokenStore.setToken("access_123")
        assertEquals("access_123", tokenStore.getToken())
    }

    @Test
    fun `setTokens stores both tokens`() {
        tokenStore.setTokens("access_456", "refresh_789")
        assertEquals("access_456", tokenStore.getToken())
        assertEquals("refresh_789", tokenStore.getRefreshToken())
    }

    @Test
    fun `clearToken removes both tokens`() {
        tokenStore.setTokens("a", "b")
        tokenStore.clearToken()
        assertNull(tokenStore.getToken())
        assertNull(tokenStore.getRefreshToken())
    }

    @Test
    fun `fallback to plain prefs clears existing tokens`() {
        val prefs = context.getSharedPreferences("raund_auth", Context.MODE_PRIVATE)
        prefs.edit().putString("token", "leaked").putString("refresh_token", "leaked_refresh").commit()

        // Robolectric doesn't have Tink, so EncryptedSharedPreferences will fail
        // and the fallback should clear any existing tokens in plain prefs
        val store = TokenStore(context)

        // After fallback, tokens should have been cleared
        assertNull(store.getToken())
        assertNull(store.getRefreshToken())
    }

    @Test
    fun `fallback keeps tokens only in memory and never persists them to plain prefs`() {
        val prefs = context.getSharedPreferences("raund_auth", Context.MODE_PRIVATE)
        val store = TokenStore(context)

        store.setTokens("access_mem", "refresh_mem")

        assertEquals("access_mem", store.getToken())
        assertEquals("refresh_mem", store.getRefreshToken())
        assertNull(prefs.getString("token", null))
        assertNull(prefs.getString("refresh_token", null))

        val reloadedStore = TokenStore(context)
        assertNull(reloadedStore.getToken())
        assertNull(reloadedStore.getRefreshToken())
    }
}
