package com.raund.app.data.remote

import androidx.test.core.app.ApplicationProvider
import com.raund.app.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class BugReportPayloadFactoryTest {

    @Test
    fun `create includes app and device metadata`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val payload = BugReportPayloadFactory.create(
            context = context,
            message = "  The timer freezes after I tap start twice.  ",
            screen = "profile_list_settings"
        )

        assertEquals("The timer freezes after I tap start twice.", payload.message)
        assertEquals("profile_list_settings", payload.screen)
        assertEquals(BuildConfig.VERSION_NAME, payload.app_version)
        assertTrue(payload.app_build.isNotBlank())
        assertTrue(payload.sdk_int > 0)
        assertFalse(payload.device_manufacturer.isBlank())
        payload.device_brand?.let {
            assertTrue(it.isNotBlank())
            assertTrue(it.length <= 120)
        }
        assertFalse(payload.device_model.isBlank())
        assertFalse(payload.os_version.isBlank())
        payload.os_incremental?.let {
            assertTrue(it.isNotBlank())
            assertTrue(it.length <= 160)
        }
        payload.build_display?.let {
            assertTrue(it.isNotBlank())
            assertTrue(it.length <= 160)
        }
        payload.build_fingerprint?.let {
            assertTrue(it.isNotBlank())
            assertTrue(it.length <= 256)
        }
        payload.security_patch?.let {
            assertTrue(it.isNotBlank())
            assertTrue(it.length <= 32)
        }
    }
}
