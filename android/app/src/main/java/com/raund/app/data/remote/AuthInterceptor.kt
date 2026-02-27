package com.raund.app.data.remote

import com.raund.app.data.local.TokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class AuthInterceptor(
    private val tokenStore: TokenStore,
    private val authService: AuthService
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenStore.getToken()
        val request = chain.request()
        val newRequest = if (token != null) {
            request.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            request
        }
        val response = chain.proceed(newRequest)
        if (response.code == 401) {
            val refreshToken = tokenStore.getRefreshToken() ?: return response
            val refreshed = runBlocking {
                try {
                    authService.refresh(RefreshRequest(refreshToken))
                } catch (_: Exception) {
                    null
                }
            } ?: return response
            tokenStore.setTokens(refreshed.token, refreshed.refresh_token)
            val retryRequest = request.newBuilder()
                .header("Authorization", "Bearer ${refreshed.token}")
                .build()
            response.close()
            return chain.proceed(retryRequest)
        }
        return response
    }
}
