package com.raund.app.data.remote

import retrofit2.http.Body
import retrofit2.http.POST

data class RegisterResponse(
    val token: String,
    val refresh_token: String,
    val refresh_token_expires_at: String?,
    val user_id: String
)

data class RefreshRequest(val refresh_token: String)
data class RefreshResponse(
    val token: String,
    val refresh_token: String,
    val refresh_token_expires_at: String?,
    val user_id: String
)

data class LogoutRequest(val refresh_token: String)

interface AuthService {
    @POST("auth/register")
    suspend fun register(): RegisterResponse

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): RefreshResponse

    @POST("auth/logout")
    suspend fun logout(@Body body: LogoutRequest)
}
