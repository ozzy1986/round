package com.raund.app.data.repository

import android.os.SystemClock
import android.util.Log
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.raund.app.data.dao.ProfileDao
import com.raund.app.data.dao.RoundDao
import com.raund.app.data.dao.RoundStats
import com.raund.app.data.entity.Profile
import com.raund.app.data.entity.Round
import com.raund.app.data.local.DataConsentPrefs
import com.raund.app.data.local.TokenStore
import com.raund.app.data.local.SyncPrefs
import com.raund.app.data.remote.ApiService
import com.raund.app.data.remote.AuthService
import com.raund.app.data.remote.CreateProfileRequest
import com.raund.app.data.remote.PutRoundItem
import com.raund.app.data.remote.PutRoundsRequest
import com.raund.app.data.remote.UpdateProfileRequest
import android.content.Context
import com.raund.app.timer.TimerProfile
import com.raund.app.timer.TimerRound
import com.raund.app.tts.TtsCache
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ProfileRepository(
    private val profileDao: ProfileDao,
    private val roundDao: RoundDao,
    private val apiProvider: () -> ApiService?,
    private val tokenStoreProvider: () -> TokenStore,
    private val authServiceProvider: () -> AuthService,
    private val syncPrefs: SyncPrefs,
    private val dataConsentPrefs: DataConsentPrefs,
    private val database: RoomDatabase
) {

    private val bgScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pendingProfileCreate = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val syncMutex = Mutex()
    private val api: ApiService?
        get() = apiProvider()
    private val tokenStore: TokenStore
        get() = tokenStoreProvider()
    private val authService: AuthService
        get() = authServiceProvider()

    @Volatile
    var cachedProfiles: List<Profile> = emptyList()
        private set

    @Volatile
    var cachedRoundStats: Map<String, RoundStats> = emptyMap()
        private set

    val profiles: Flow<List<Profile>> = profileDao.getAll()
    val roundStats: Flow<Map<String, RoundStats>> = roundDao
        .getStatsByProfile()
        .map { stats -> stats.associateBy { it.profileId } }

    suspend fun preloadCache() {
        val start = SystemClock.elapsedRealtime()
        coroutineScope {
            val profilesDeferred = async { profileDao.getAllOnce() }
            val roundStatsDeferred = async { roundDao.getStatsByProfileOnce() }
            cachedProfiles = profilesDeferred.await()
            cachedRoundStats = roundStatsDeferred.await().associateBy { it.profileId }
        }
        Log.i(PERF, "preloadCache: ${cachedProfiles.size} profiles in ${SystemClock.elapsedRealtime() - start}ms")
    }

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
        if (!dataConsentPrefs.isConsentGranted()) return
        if (tokenStore.getToken() != null) return
        try {
            val r = authService.register()
            tokenStore.setTokens(r.token, r.refresh_token)
        } catch (e: Exception) {
            Log.w(PERF, "ensureToken: register failed", e)
        }
    }

    suspend fun insertProfile(name: String, emoji: String): String = withContext(Dispatchers.IO) {
        val start = SystemClock.elapsedRealtime()
        val now = System.currentTimeMillis()
        val safeName = name.trim()
        val safeEmoji = emoji.trim().ifBlank { "⏱" }
        val id = UUID.randomUUID().toString()
        profileDao.insert(Profile(id = id, name = safeName, emoji = safeEmoji, updatedAt = now))
        val roomMs = SystemClock.elapsedRealtime() - start
        Log.i(PERF, "insertProfile: Room insert took ${roomMs}ms, id=$id")

        val deferred = CompletableDeferred<Unit>()
        pendingProfileCreate[id] = deferred
        bgScope.launch {
            val apiStart = SystemClock.elapsedRealtime()
            try {
                ensureToken()
                api?.createProfile(CreateProfileRequest(safeName, safeEmoji, id))
                Log.i(PERF, "insertProfile: API call completed in ${SystemClock.elapsedRealtime() - apiStart}ms (background)")
            } catch (e: Exception) {
                Log.i(PERF, "insertProfile: API call failed in ${SystemClock.elapsedRealtime() - apiStart}ms: ${e.message}")
            } finally {
                deferred.complete(Unit)
                pendingProfileCreate.remove(id)
            }
        }
        id
    }

    suspend fun updateProfile(id: String, name: String, emoji: String) = withContext(Dispatchers.IO) {
        val start = SystemClock.elapsedRealtime()
        val now = System.currentTimeMillis()
        val safeName = name.trim()
        val safeEmoji = emoji.trim().ifBlank { "⏱" }
        profileDao.update(id, safeName, safeEmoji, now)
        Log.i(PERF, "updateProfile: Room update took ${SystemClock.elapsedRealtime() - start}ms")

        bgScope.launch {
            val apiStart = SystemClock.elapsedRealtime()
            try {
                ensureToken()
                api?.updateProfile(id, UpdateProfileRequest(safeName, safeEmoji))
                Log.i(PERF, "updateProfile: API call completed in ${SystemClock.elapsedRealtime() - apiStart}ms (background)")
            } catch (e: Exception) {
                Log.i(PERF, "updateProfile: API call failed in ${SystemClock.elapsedRealtime() - apiStart}ms: ${e.message}")
            }
            requestSync()
        }
    }

    suspend fun deleteProfile(id: String) = withContext(Dispatchers.IO) {
        val start = SystemClock.elapsedRealtime()
        roundDao.deleteByProfileId(id)
        profileDao.deleteById(id)
        Log.i(PERF, "deleteProfile: Room delete took ${SystemClock.elapsedRealtime() - start}ms")

        bgScope.launch {
            val apiStart = SystemClock.elapsedRealtime()
            try {
                ensureToken()
                api?.deleteProfile(id)
                Log.i(PERF, "deleteProfile: API call completed in ${SystemClock.elapsedRealtime() - apiStart}ms (background)")
            } catch (e: Exception) {
                Log.i(PERF, "deleteProfile: API call failed in ${SystemClock.elapsedRealtime() - apiStart}ms: ${e.message}")
            }
            requestSync()
        }
    }

    suspend fun saveRounds(profileId: String, rounds: List<Triple<String, Int, Boolean>>) = withContext(Dispatchers.IO) {
        val start = SystemClock.elapsedRealtime()
        val sanitizedRounds = rounds.mapNotNull { (name, durationSeconds, warn10sec) ->
            val safeName = name.trim().take(20)
            if (safeName.isBlank()) null
            else Triple(safeName, durationSeconds.coerceIn(com.raund.app.MIN_ROUND_DURATION_SECONDS, com.raund.app.MAX_ROUND_DURATION_SECONDS), warn10sec)
        }
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
        database.withTransaction {
            roundDao.deleteByProfileId(profileId)
            roundDao.insertAll(entities)
        }
        Log.i(PERF, "saveRounds: Room save took ${SystemClock.elapsedRealtime() - start}ms (${entities.size} rounds)")

        bgScope.launch {
            pendingProfileCreate[profileId]?.let { pending ->
                Log.i(PERF, "saveRounds: waiting for pending profile create for $profileId")
                pending.await()
                Log.i(PERF, "saveRounds: profile create completed, proceeding with rounds PUT")
            }
            val apiStart = SystemClock.elapsedRealtime()
            try {
                ensureToken()
                api?.putRounds(
                    profileId,
                    PutRoundsRequest(
                        sanitizedRounds.mapIndexed { index, (name, durationSeconds, warn10sec) ->
                            PutRoundItem(name, durationSeconds, warn10sec, index)
                        }
                    )
                )
                Log.i(PERF, "saveRounds: API call completed in ${SystemClock.elapsedRealtime() - apiStart}ms (background)")
            } catch (e: Exception) {
                Log.i(PERF, "saveRounds: API call failed in ${SystemClock.elapsedRealtime() - apiStart}ms: ${e.message}")
            }
            requestSync()
        }
    }

    fun prefillTtsInBackground(context: Context, locale: String, phrases: List<String>) {
        bgScope.launch {
            try {
                TtsCache.ensureCache(context, locale, phrases)
                Log.i(PERF, "prefillTts: completed ${phrases.size} phrases")
            } catch (e: Exception) {
                Log.i(PERF, "prefillTts: failed: ${e.message}")
            }
        }
    }

    suspend fun requestSync() = withContext(Dispatchers.IO) { syncGuarded(force = false) }

    suspend fun forceSync() = withContext(Dispatchers.IO) { syncGuarded(force = true) }

    private suspend fun syncGuarded(force: Boolean) {
        syncMutex.withLock {
            val last = syncPrefs.getLastSyncedAtMillis()
            if (!force && last != null && (System.currentTimeMillis() - last) < SyncPrefs.SYNC_THROTTLE_MS) {
                return@withLock
            }
            syncFromApi()
        }
    }

    suspend fun syncSingleProfile(profileId: String) = withContext(Dispatchers.IO) {
        ensureToken()
        try {
            val api = this@ProfileRepository.api ?: return@withContext
            val dto = api.getProfileWithRounds(profileId)
            database.withTransaction {
                profileDao.insert(Profile(dto.id, dto.name, dto.emoji, System.currentTimeMillis()))
                roundDao.deleteByProfileId(dto.id)
                roundDao.insertAll(dto.rounds.mapIndexed { i, r ->
                    Round(r.id, r.profile_id, r.name, r.duration_seconds.coerceAtLeast(5), r.warn10sec, i)
                })
            }
            Log.i(PERF, "syncSingleProfile: synced $profileId (${dto.rounds.size} rounds)")
        } catch (e: Exception) {
            Log.w(PERF, "syncSingleProfile failed for $profileId", e)
        }
    }

    suspend fun syncFromApi() = withContext(Dispatchers.IO) {
        val totalStart = SystemClock.elapsedRealtime()
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
                if (page.data.isNotEmpty()) {
                    val pageStart = SystemClock.elapsedRealtime()
                    database.withTransaction {
                        page.data.forEach { dto ->
                            profileDao.insert(Profile(dto.id, dto.name, dto.emoji, System.currentTimeMillis()))
                            roundDao.deleteByProfileId(dto.id)
                            roundDao.insertAll(dto.rounds.mapIndexed { i, r ->
                                Round(r.id, r.profile_id, r.name, r.duration_seconds.coerceAtLeast(5), r.warn10sec, i)
                            })
                        }
                    }
                    Log.i(PERF, "syncFromApi: page upsert took ${SystemClock.elapsedRealtime() - pageStart}ms (${page.data.size} profiles, transaction=true)")
                }
                cursor = page.next_cursor
            } while (cursor != null)
            syncPrefs.setLastSyncedAtNow()
            Log.i(PERF, "syncFromApi: total sync took ${SystemClock.elapsedRealtime() - totalStart}ms")
        } catch (e: Exception) {
            Log.w(PERF, "syncFromApi failed", e)
        }
    }

    companion object {
        private const val PERF = "PerfFix"
    }
}
