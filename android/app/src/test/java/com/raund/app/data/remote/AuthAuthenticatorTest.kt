package com.raund.app.data.remote

import com.raund.app.data.local.TokenStore
import io.mockk.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Call
import retrofit2.Response

class AuthAuthenticatorTest {

    private val tokenStore = mockk<TokenStore>(relaxed = true)
    private val authService = mockk<AuthService>()
    private lateinit var authenticator: AuthAuthenticator

    @Before
    fun setup() {
        authenticator = AuthAuthenticator(tokenStore, authService)
    }

    private fun build401Response(requestHasAuth: Boolean): okhttp3.Response {
        val requestBuilder = Request.Builder().url("https://example.com/profiles")
        if (requestHasAuth) {
            requestBuilder.header("Authorization", "Bearer old_token")
        }
        val request = requestBuilder.build()
        return okhttp3.Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .body("".toResponseBody("text/plain".toMediaType()))
            .build()
    }

    @Test
    fun `returns null when request already had Authorization header - prevents retry loop`() {
        val response = build401Response(requestHasAuth = true)
        val result = authenticator.authenticate(null, response)
        assertNull(result)
        verify(exactly = 0) { tokenStore.getRefreshToken() }
        verify(exactly = 0) { authService.refreshCall(any()) }
    }

    @Test
    fun `returns null when no refresh token stored`() {
        every { tokenStore.getToken() } returns null
        every { tokenStore.getRefreshToken() } returns null
        val response = build401Response(requestHasAuth = false)
        val result = authenticator.authenticate(null, response)
        assertNull(result)
    }

    @Test
    fun `refreshes token and retries when no prior auth header`() {
        every { tokenStore.getToken() } returns null
        every { tokenStore.getRefreshToken() } returns "refresh_abc"
        val refreshResp = RefreshResponse(
            token = "new_access",
            refresh_token = "new_refresh",
            refresh_token_expires_at = null,
            user_id = "user1"
        )
        val call = mockk<Call<RefreshResponse>>()
        every { authService.refreshCall(RefreshRequest("refresh_abc")) } returns call
        every { call.execute() } returns Response.success(refreshResp)

        val response = build401Response(requestHasAuth = false)
        val result = authenticator.authenticate(null, response)

        assertNotNull(result)
        assertEquals("Bearer new_access", result!!.header("Authorization"))
        verify { tokenStore.setTokens("new_access", "new_refresh") }
    }

    @Test
    fun `returns null when refresh call fails`() {
        every { tokenStore.getToken() } returns null
        every { tokenStore.getRefreshToken() } returns "refresh_abc"
        val call = mockk<Call<RefreshResponse>>()
        every { authService.refreshCall(any()) } returns call
        every { call.execute() } throws java.io.IOException("network down")

        val response = build401Response(requestHasAuth = false)
        val result = authenticator.authenticate(null, response)
        assertNull(result)
    }

    @Test
    fun `returns null when refresh returns non-successful response`() {
        every { tokenStore.getToken() } returns null
        every { tokenStore.getRefreshToken() } returns "refresh_abc"
        val call = mockk<Call<RefreshResponse>>()
        every { authService.refreshCall(any()) } returns call
        every { call.execute() } returns Response.error(
            403,
            "forbidden".toResponseBody("text/plain".toMediaType())
        )

        val response = build401Response(requestHasAuth = false)
        val result = authenticator.authenticate(null, response)
        assertNull(result)
    }

    @Test
    fun `skips refresh when token was already refreshed by another thread`() {
        every { tokenStore.getToken() } returns "already_refreshed_token"
        every { tokenStore.getRefreshToken() } returns "refresh_abc"

        val response = build401Response(requestHasAuth = false)
        val result = authenticator.authenticate(null, response)

        assertNotNull(result)
        assertEquals("Bearer already_refreshed_token", result!!.header("Authorization"))
        verify(exactly = 0) { authService.refreshCall(any()) }
    }

    @Test
    fun `concurrent calls do not duplicate refresh`() {
        every { tokenStore.getToken() } returns null
        every { tokenStore.getRefreshToken() } returns "refresh_abc"
        val refreshResp = RefreshResponse(
            token = "concurrent_token",
            refresh_token = "concurrent_refresh",
            refresh_token_expires_at = null,
            user_id = "user1"
        )
        val call = mockk<Call<RefreshResponse>>()
        every { authService.refreshCall(any()) } returns call
        every { call.execute() } answers {
            Thread.sleep(50)
            Response.success(refreshResp)
        }

        val results = java.util.concurrent.ConcurrentLinkedQueue<Request?>()
        val threads = (1..5).map {
            Thread {
                val resp = build401Response(requestHasAuth = false)
                results.add(authenticator.authenticate(null, resp))
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join(5000) }

        assertTrue(results.all { it != null })
        verify(atMost = 5) { authService.refreshCall(any()) }
    }
}
