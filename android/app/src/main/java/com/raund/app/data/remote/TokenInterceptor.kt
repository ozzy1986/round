package com.raund.app.data.remote

import com.raund.app.data.local.TokenStore
import okhttp3.Interceptor
import okhttp3.Response

/** Adds Authorization header when a token is present. Does not handle 401; use Authenticator for that. */
class TokenInterceptor(private val tokenStore: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenStore.getToken() ?: return chain.proceed(chain.request())
        val request = chain.request().newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(request)
    }
}
