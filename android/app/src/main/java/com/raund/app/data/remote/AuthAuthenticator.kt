package com.raund.app.data.remote

import com.raund.app.data.local.TokenStore
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * On 401, refreshes the token via a synchronous call and retries the request.
 *
 * Thread safety: OkHttp may call [authenticate] from multiple threads concurrently.
 * A [synchronized] block ensures only one refresh runs at a time; subsequent callers
 * reuse the freshly stored token instead of triggering duplicate rotations.
 */
class AuthAuthenticator(
    private val tokenStore: TokenStore,
    private val authService: AuthService
) : Authenticator {

    private val refreshLock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.header("Authorization") != null) return null

        synchronized(refreshLock) {
            val currentToken = tokenStore.getToken()
            val requestToken = response.request.header("Authorization")
                ?.removePrefix("Bearer ")

            if (currentToken != null && currentToken != requestToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }

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
}
