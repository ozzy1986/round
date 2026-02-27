package com.raund.app.data.remote

import com.raund.app.data.local.TokenStore
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * On 401, tries to refresh the token via synchronous call (no runBlocking).
 * Returns a new request with the new token, or null to fail the request.
 */
class AuthAuthenticator(
    private val tokenStore: TokenStore,
    private val authService: AuthService
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        val refreshToken = tokenStore.getRefreshToken() ?: return null
        val call = authService.refreshCall(RefreshRequest(refreshToken))
        val refreshed = try {
            val resp = call.execute()
            if (resp.isSuccessful) resp.body() else null
        } catch (_: Exception) {
            null
        } ?: return null
        tokenStore.setTokens(refreshed.token, refreshed.refresh_token)
        return response.request.newBuilder()
            .header("Authorization", "Bearer ${refreshed.token}")
            .build()
    }
}
