package com.raund.app.data.repository

import com.raund.app.data.dao.ProfileDao
import com.raund.app.data.dao.RoundDao
import com.raund.app.data.local.DataConsentPrefs
import com.raund.app.data.local.SyncPrefs
import com.raund.app.data.local.TokenStore
import com.raund.app.data.remote.ApiService
import com.raund.app.data.remote.AuthService
import com.raund.app.data.remote.ProfilesWithRoundsPageDto
import io.mockk.*
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.room.RoomDatabase
import androidx.room.withTransaction

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ProfileRepositoryTest {

    private val profileDao = mockk<ProfileDao>(relaxed = true)
    private val roundDao = mockk<RoundDao>(relaxed = true)
    private val api = mockk<ApiService>(relaxed = true)
    private val tokenStore = mockk<TokenStore>(relaxed = true)
    private val authService = mockk<AuthService>(relaxed = true)
    private val syncPrefs = mockk<SyncPrefs>(relaxed = true)
    private val dataConsentPrefs = mockk<DataConsentPrefs>(relaxed = true)
    private val database = mockk<RoomDatabase>(relaxed = true)
    private lateinit var repository: ProfileRepository

    @Before
    fun setup() {
        every { dataConsentPrefs.isConsentGranted() } returns true
        every { tokenStore.getToken() } returns "access_token"
        repository = createRepository()

        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { database.withTransaction<Unit>(captureLambda()) } coAnswers {
            lambda<suspend () -> Unit>().captured.invoke()
        }
    }

    private fun createRepository(apiService: ApiService? = null): ProfileRepository {
        return ProfileRepository(
            profileDao = profileDao,
            roundDao = roundDao,
            apiProvider = { apiService },
            tokenStoreProvider = { tokenStore },
            authServiceProvider = { authService },
            syncPrefs = syncPrefs,
            dataConsentPrefs = dataConsentPrefs,
            database = database
        )
    }

    @Test
    fun `saveRounds calls delete and insert atomically`() = runTest {
        val rounds = listOf(
            Triple("Work", 30, true),
            Triple("Rest", 15, false)
        )

        repository.saveRounds("profile-1", rounds)

        coVerify {
            roundDao.deleteByProfileId("profile-1")
            roundDao.insertAll(match { entities ->
                entities.size == 2 &&
                entities[0].name == "Work" && entities[0].durationSeconds == 30 && entities[0].warn10sec &&
                entities[1].name == "Rest" && entities[1].durationSeconds == 15 && !entities[1].warn10sec &&
                entities[0].position == 0 && entities[1].position == 1 &&
                entities[0].profileId == "profile-1" && entities[1].profileId == "profile-1"
            })
        }
    }

    @Test
    fun `saveRounds sanitizes round names - trims and takes 20 chars`() = runTest {
        val rounds = listOf(
            Triple("  This is a very long round name that exceeds limit  ", 60, false)
        )

        repository.saveRounds("p-1", rounds)

        coVerify {
            roundDao.insertAll(match { entities ->
                entities.size == 1 && entities[0].name == "This is a very long " && entities[0].name.length == 20
            })
        }
    }

    @Test
    fun `saveRounds drops blank names`() = runTest {
        val rounds = listOf(
            Triple("", 30, false),
            Triple("   ", 15, true),
            Triple("Valid", 20, false)
        )

        repository.saveRounds("p-1", rounds)

        coVerify {
            roundDao.insertAll(match { entities ->
                entities.size == 1 && entities[0].name == "Valid"
            })
        }
    }

    @Test
    fun `saveRounds enforces minimum duration of 5 seconds`() = runTest {
        val rounds = listOf(
            Triple("Short", 2, false),
            Triple("Normal", 30, true)
        )

        repository.saveRounds("p-1", rounds)

        coVerify {
            roundDao.insertAll(match { entities ->
                entities.size == 2 && entities[0].durationSeconds == 5 && entities[1].durationSeconds == 30
            })
        }
    }

    @Test
    fun `saveRounds enforces maximum duration of 7200 seconds`() = runTest {
        val rounds = listOf(
            Triple("TooLong", 9000, false),
            Triple("AtMax", 7200, true),
            Triple("Normal", 300, false)
        )

        repository.saveRounds("p-1", rounds)

        coVerify {
            roundDao.insertAll(match { entities ->
                entities.size == 3 &&
                entities[0].durationSeconds == 7200 &&
                entities[1].durationSeconds == 7200 &&
                entities[2].durationSeconds == 300
            })
        }
    }

    @Test
    fun `requestSync dedupes concurrent calls inside mutex using throttle`() = runTest {
        val lastSyncedAt = java.util.concurrent.atomic.AtomicReference<Long?>(null)
        every { syncPrefs.getLastSyncedAtMillis() } answers { lastSyncedAt.get() }
        every { syncPrefs.getLastSyncedAtForApi() } returns null
        every { syncPrefs.setLastSyncedAtNow() } answers { lastSyncedAt.set(System.currentTimeMillis()) }
        coEvery { api.getProfilesWithRoundsPage(any(), any(), any(), any()) } coAnswers {
            Thread.sleep(50)
            ProfilesWithRoundsPageDto(emptyList(), null)
        }
        repository = createRepository(api)

        val jobs = listOf(
            launch { repository.requestSync() },
            launch { repository.requestSync() }
        )
        jobs.joinAll()

        coVerify(exactly = 1) { api.getProfilesWithRoundsPage(100, any(), "rounds", null) }
    }

    @Test
    fun `forceSync bypasses throttle`() = runTest {
        every { syncPrefs.getLastSyncedAtMillis() } returns System.currentTimeMillis()
        every { syncPrefs.getLastSyncedAtForApi() } returns null
        coEvery { api.getProfilesWithRoundsPage(any(), any(), any(), any()) } returns ProfilesWithRoundsPageDto(emptyList(), null)
        repository = createRepository(api)

        repository.forceSync()

        coVerify(exactly = 1) { api.getProfilesWithRoundsPage(100, any(), "rounds", null) }
    }
}
