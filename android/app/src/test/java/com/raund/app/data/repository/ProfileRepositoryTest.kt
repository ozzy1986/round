package com.raund.app.data.repository

import com.raund.app.data.dao.ProfileDao
import com.raund.app.data.dao.RoundDao
import com.raund.app.data.local.SyncPrefs
import com.raund.app.data.local.TokenStore
import com.raund.app.data.remote.ApiService
import com.raund.app.data.remote.AuthService
import com.raund.app.data.remote.BugReportRequest
import com.raund.app.data.remote.BugReportResponse
import com.raund.app.data.remote.ProfileWithRoundsDto
import com.raund.app.data.remote.RegisterResponse
import com.raund.app.data.remote.ProfilesWithRoundsPageDto
import com.raund.app.data.remote.RoundDto
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
    private val database = mockk<RoomDatabase>(relaxed = true)
    private lateinit var repository: ProfileRepository

    @Before
    fun setup() {
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
            database = database
        )
    }

    private fun bugReportRequest(message: String = "The timer crashes after I pause and resume quickly."): BugReportRequest {
        return BugReportRequest(
            message = message,
            screen = "profile_list_settings",
            device_manufacturer = "Google",
            device_model = "Pixel 8",
            os_version = "15",
            sdk_int = 35,
            app_version = "1.0.0",
            app_build = "1",
            build_fingerprint = null
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
        val nowIso = java.time.Instant.ofEpochMilli(System.currentTimeMillis()).toString()
        every { syncPrefs.getLastSyncedAtMillis() } answers { lastSyncedAt.get() }
        every { syncPrefs.getLastSyncedAtForApi() } returns null
        every { syncPrefs.setLastSyncedAt(any()) } answers {
            lastSyncedAt.set(java.time.Instant.parse(firstArg()).toEpochMilli())
        }
        coEvery { api.getProfilesWithRoundsPage(any(), any(), any(), any()) } coAnswers {
            Thread.sleep(50)
            ProfilesWithRoundsPageDto(emptyList(), null, nowIso)
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
        coEvery { api.getProfilesWithRoundsPage(any(), any(), any(), any()) } returns ProfilesWithRoundsPageDto(emptyList(), null, "2026-03-08T12:00:00Z")
        repository = createRepository(api)

        repository.forceSync()

        coVerify(exactly = 1) { api.getProfilesWithRoundsPage(100, any(), "rounds", null) }
    }

    @Test
    fun `requestSync registers anonymous user when no token exists`() = runTest {
        every { tokenStore.getToken() } returns null
        coEvery { authService.register() } returns RegisterResponse(
            token = "new_access",
            refresh_token = "new_refresh",
            refresh_token_expires_at = null,
            user_id = "user-1"
        )
        coEvery { api.getProfilesWithRoundsPage(any(), any(), any(), any()) } returns ProfilesWithRoundsPageDto(
            emptyList(),
            null,
            "2026-03-08T12:00:00Z"
        )
        repository = createRepository(api)

        repository.requestSync()

        coVerify(exactly = 1) { authService.register() }
        verify { tokenStore.setTokens("new_access", "new_refresh") }
    }

    @Test
    fun `submitBugReport registers anonymous user when needed and calls API`() = runTest {
        every { tokenStore.getToken() } returnsMany listOf(null, null, "new_access", "new_access")
        coEvery { authService.register() } returns RegisterResponse(
            token = "new_access",
            refresh_token = "new_refresh",
            refresh_token_expires_at = null,
            user_id = "user-1"
        )
        coEvery { api.submitBugReport(any()) } returns BugReportResponse(
            id = "report-1",
            created_at = "2026-03-09T12:00:00Z"
        )
        repository = createRepository(api)
        val request = bugReportRequest()

        repository.submitBugReport(request)

        coVerify(exactly = 1) { authService.register() }
        verify { tokenStore.setTokens("new_access", "new_refresh") }
        coVerify(exactly = 1) { api.submitBugReport(request) }
    }

    @Test
    fun `submitBugReport propagates API failures`() = runTest {
        val request = bugReportRequest()
        coEvery { api.submitBugReport(any()) } throws IllegalStateException("api down")
        repository = createRepository(api)

        try {
            repository.submitBugReport(request)
            fail("Expected submitBugReport to throw")
        } catch (e: IllegalStateException) {
            assertEquals("api down", e.message)
        }
    }

    @Test
    fun `syncFromApi stores server updated_at for local profile and sync watermark`() = runTest {
        every { syncPrefs.getLastSyncedAtForApi() } returns "2026-03-08T09:00:00Z"
        coEvery { api.getProfilesWithRoundsPage(any(), any(), any(), any()) } returns ProfilesWithRoundsPageDto(
            data = listOf(
                ProfileWithRoundsDto(
                    id = "profile-1",
                    name = "Boxing",
                    emoji = "🥊",
                    updated_at = "2026-03-08T10:15:30Z",
                    rounds = listOf(
                        RoundDto(
                            id = "round-1",
                            profile_id = "profile-1",
                            name = "Round 1",
                            duration_seconds = 180,
                            warn10sec = true,
                            position = 0
                        )
                    )
                )
            ),
            next_cursor = null,
            synced_at = "2026-03-08T10:30:00Z"
        )
        repository = createRepository(api)

        repository.syncFromApi()

        coVerify {
            profileDao.insert(
                match { profile ->
                    profile.id == "profile-1" &&
                        profile.updatedAt == java.time.Instant.parse("2026-03-08T10:15:30Z").toEpochMilli()
                }
            )
        }
        verify { syncPrefs.setLastSyncedAt("2026-03-08T10:30:00Z") }
    }
}
