package com.raund.app.data.remote

import retrofit2.http.POST

data class RegisterResponse(
    val token: String,
    val user_id: String
)

interface AuthService {
    @POST("auth/register")
    suspend fun register(): RegisterResponse
}
