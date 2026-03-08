package com.raund.app

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ManifestAndBuildConfigTest {

    @Test
    fun `API base URL uses HTTPS`() {
        assertTrue(
            "API_BASE_URL must use HTTPS",
            BuildConfig.API_BASE_URL.startsWith("https://")
        )
    }

    @Test
    fun `SENTRY_DSN build config field exists`() {
        // Just verify the field exists and is a String (empty in debug, set in release)
        assertNotNull(BuildConfig.SENTRY_DSN)
    }

    @Test
    fun `SENTRY_ENABLED is never true without a DSN`() {
        assertFalse(BuildConfig.SENTRY_ENABLED && BuildConfig.SENTRY_DSN.isBlank())
    }
}
