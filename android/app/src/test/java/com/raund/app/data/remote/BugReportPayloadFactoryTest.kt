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
        assertFalse(payload.device_model.isBlank())
        assertFalse(payload.os_version.isBlank())
    }
}
