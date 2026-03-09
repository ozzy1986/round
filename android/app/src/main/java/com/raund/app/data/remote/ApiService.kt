package com.raund.app.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

data class ProfileDto(
    val id: String,
    val name: String,
    val emoji: String,
    val updated_at: String? = null
)

data class ProfileWithRoundsDto(
    val id: String,
    val name: String,
    val emoji: String,
    val updated_at: String? = null,
    val rounds: List<RoundDto>
)

data class RoundDto(
    val id: String,
    val profile_id: String,
    val name: String,
    val duration_seconds: Int,
    val warn10sec: Boolean,
    val position: Int
)

data class CreateProfileRequest(val name: String, val emoji: String, val id: String? = null)
data class UpdateProfileRequest(val name: String? = null, val emoji: String? = null)
data class CreateRoundRequest(
    val name: String,
    val duration_seconds: Int,
    val warn10sec: Boolean = false,
    val position: Int
)
data class UpdateRoundRequest(
    val name: String? = null,
    val duration_seconds: Int? = null,
    val warn10sec: Boolean? = null,
    val position: Int? = null
)

data class ProfilesPageDto(
    val data: List<ProfileDto>,
    val next_cursor: String?
)

data class ProfilesWithRoundsPageDto(
    val data: List<ProfileWithRoundsDto>,
    val next_cursor: String?,
    val synced_at: String? = null
)

data class PutRoundsRequest(val rounds: List<PutRoundItem>)
data class PutRoundItem(
    val name: String,
    val duration_seconds: Int,
    val warn10sec: Boolean = false,
    val position: Int
)

interface ApiService {
    @POST("bug-reports")
    suspend fun submitBugReport(@Body body: BugReportRequest): BugReportResponse

    @GET("profiles")
    suspend fun getProfilesPage(
        @Query("limit") limit: Int = 20,
        @Query("cursor") cursor: String? = null
    ): ProfilesPageDto

    @GET("profiles")
    suspend fun getProfilesWithRoundsPage(
        @Query("limit") limit: Int = 100,
        @Query("cursor") cursor: String? = null,
        @Query("include") include: String = "rounds",
        @Query("updated_since") updatedSince: String? = null
    ): ProfilesWithRoundsPageDto

    @GET("profiles/{id}")
    suspend fun getProfileWithRounds(@Path("id") id: String): ProfileWithRoundsDto

    @POST("profiles")
    suspend fun createProfile(@Body body: CreateProfileRequest): ProfileDto

    @PATCH("profiles/{id}")
    suspend fun updateProfile(@Path("id") id: String, @Body body: UpdateProfileRequest): ProfileDto

    @DELETE("profiles/{id}")
    suspend fun deleteProfile(@Path("id") id: String): Unit

    @GET("profiles/{id}/rounds")
    suspend fun getRounds(@Path("id") profileId: String): List<RoundDto>

    @POST("profiles/{id}/rounds")
    suspend fun createRound(@Path("id") profileId: String, @Body body: CreateRoundRequest): RoundDto

    @PATCH("rounds/{roundId}")
    suspend fun updateRound(@Path("roundId") roundId: String, @Body body: UpdateRoundRequest): RoundDto

    @DELETE("rounds/{roundId}")
    suspend fun deleteRound(@Path("roundId") roundId: String): Unit

    @PUT("profiles/{id}/rounds")
    suspend fun putRounds(
        @Path("id") profileId: String,
        @Body body: PutRoundsRequest
    ): List<RoundDto>
}
