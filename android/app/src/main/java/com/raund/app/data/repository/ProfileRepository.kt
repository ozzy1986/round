package com.raund.app.data.repository

import com.raund.app.data.dao.ProfileDao
import com.raund.app.data.dao.RoundDao
import com.raund.app.data.dao.RoundStats
import com.raund.app.data.entity.Profile
import com.raund.app.data.entity.Round
import com.raund.app.data.local.TokenStore
import com.raund.app.data.local.SyncPrefs
import com.raund.app.data.remote.ApiService
import com.raund.app.data.remote.AuthService
import com.raund.app.data.remote.CreateProfileRequest
import com.raund.app.data.remote.CreateRoundRequest
import com.raund.app.data.remote.PutRoundItem
import com.raund.app.data.remote.PutRoundsRequest
import com.raund.app.data.remote.UpdateProfileRequest
import com.raund.app.timer.TimerProfile
import com.raund.app.timer.TimerRound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class ProfileRepository(
    private val profileDao: ProfileDao,
    private val roundDao: RoundDao,
    private val api: ApiService?,
    private val tokenStore: TokenStore,
    private val authService: AuthService,
    private val syncPrefs: SyncPrefs
) {

    val profiles: Flow<List<Profile>> = profileDao.getAll()
    val roundStats: Flow<Map<String, RoundStats>> = roundDao
        .getStatsByProfile()
        .map { stats -> stats.associateBy { it.profileId } }

    fun getRounds(profileId: String): Flow<List<Round>> = roundDao.getByProfileId(profileId)

    suspend fun getProfileWithRounds(profileId: String): TimerProfile? = withContext(Dispatchers.IO) {
        val profile = profileDao.getById(profileId) ?: return@withContext null
        val rounds = roundDao.getByProfileIdOnce(profileId)
        TimerProfile(
            name = profile.name,
            emoji = profile.emoji,
            rounds = rounds.map { TimerRound(it.name, it.durationSeconds.coerceAtLeast(1), it.warn10sec) }
        )
    }

    private suspend fun ensureToken() {
        if (tokenStore.getToken() != null) return
        try {
            val r = authService.register()
            tokenStore.setTokens(r.token, r.refresh_token)
        } catch (_: Exception) {
            // Offline or server error
        }
    }

    suspend fun insertProfile(name: String, emoji: String): String = withContext(Dispatchers.IO) {
        ensureToken()
        val now = System.currentTimeMillis()
        val safeName = name.trim()
        val safeEmoji = emoji.trim().ifBlank { "⏱" }
        val id = try {
            api?.createProfile(CreateProfileRequest(safeName, safeEmoji))?.id ?: UUID.randomUUID().toString()
        } catch (_: Exception) {
            UUID.randomUUID().toString()
        }
        profileDao.insert(Profile(id = id, name = safeName, emoji = safeEmoji, updatedAt = now))
        requestSync()
        id
    }

    suspend fun updateProfile(id: String, name: String, emoji: String) = withContext(Dispatchers.IO) {
        ensureToken()
        val now = System.currentTimeMillis()
        val safeName = name.trim()
        val safeEmoji = emoji.trim().ifBlank { "⏱" }
        profileDao.update(id, safeName, safeEmoji, now)
        try {
            api?.updateProfile(id, UpdateProfileRequest(safeName, safeEmoji))
        } catch (_: Exception) {
            // Offline or server unknown profile; local update already done
        }
        requestSync()
    }

    suspend fun deleteProfile(id: String) = withContext(Dispatchers.IO) {
        ensureToken()
        roundDao.deleteByProfileId(id)
        profileDao.deleteById(id)
        try {
            api?.deleteProfile(id)
        } catch (_: Exception) {
            // Offline or server unknown profile; local delete already done
        }
        requestSync()
    }

    suspend fun saveRounds(profileId: String, rounds: List<Triple<String, Int, Boolean>>) = withContext(Dispatchers.IO) {
        ensureToken()
        val sanitizedRounds = rounds.mapNotNull { (name, durationSeconds, warn10sec) ->
            val safeName = name.trim().take(20)
            if (safeName.isBlank()) {
                null
            } else {
                Triple(safeName, durationSeconds.coerceAtLeast(5), warn10sec)
            }
        }
        roundDao.deleteByProfileId(profileId)
        val entities = sanitizedRounds.mapIndexed { index, (name, durationSeconds, warn10sec) ->
            Round(
                id = UUID.randomUUID().toString(),
                profileId = profileId,
                name = name,
                durationSeconds = durationSeconds,
                warn10sec = warn10sec,
                position = index
            )
        }
        roundDao.insertAll(entities)
        try {
            api?.putRounds(
                profileId,
                PutRoundsRequest(
                    sanitizedRounds.mapIndexed { index, (name, durationSeconds, warn10sec) ->
                        PutRoundItem(name, durationSeconds, warn10sec, index)
                    }
                )
            )
        } catch (_: Exception) {
            // Offline or server error; local state is already saved
        }
        requestSync()
    }

    /** Runs sync only if last sync was at least SYNC_THROTTLE_MS ago. Call from save completion or connectivity callback. */
    suspend fun requestSync() = withContext(Dispatchers.IO) {
        val last = syncPrefs.getLastSyncedAtMillis()
        if (last != null && (System.currentTimeMillis() - last) < SyncPrefs.SYNC_THROTTLE_MS) {
            return@withContext
        }
        syncFromApi()
    }

    suspend fun syncFromApi() = withContext(Dispatchers.IO) {
        ensureToken()
        try {
            val api = this@ProfileRepository.api ?: return@withContext
            val updatedSince = syncPrefs.getLastSyncedAtForApi()
            var cursor: String? = null
            do {
                val page = api.getProfilesWithRoundsPage(
                    limit = 100,
                    cursor = cursor,
                    updatedSince = updatedSince
                )
                page.data.forEach { dto ->
                    val profile = Profile(dto.id, dto.name, dto.emoji, System.currentTimeMillis())
                    profileDao.insert(profile)
                    roundDao.deleteByProfileId(dto.id)
                    roundDao.insertAll(dto.rounds.mapIndexed { i, r ->
                        Round(r.id, r.profile_id, r.name, r.duration_seconds.coerceAtLeast(5), r.warn10sec, i)
                    })
                }
                cursor = page.next_cursor
            } while (cursor != null)
            syncPrefs.setLastSyncedAtNow()
        } catch (_: Exception) {
            // Offline or server error; keep existing local data
        }
    }
}
