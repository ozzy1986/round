package com.raund.app.tts

import androidx.test.core.app.ApplicationProvider
import com.raund.app.timer.TimerProfile
import com.raund.app.timer.TimerRound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class TtsCacheTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setup() {
        TtsCache.cacheDir(context).deleteRecursively()
    }

    @Test
    fun `buildPhraseList keeps round names then profile name then finished text`() {
        val profile = TimerProfile(
            name = "Boxing",
            emoji = "🥊",
            rounds = listOf(
                TimerRound("Warmup", 60, true),
                TimerRound("Work", 180, false)
            )
        )

        val phrases = TtsCache.buildPhraseList(profile, "Training finished")

        assertEquals(
            listOf("Warmup", "Work", "Boxing", "Training finished"),
            phrases
        )
    }

    @Test
    fun `cacheFile is deterministic and locale-specific`() {
        val first = TtsCache.cacheFile(context, "en", "Round one")
        val second = TtsCache.cacheFile(context, "en", "Round one")
        val otherLocale = TtsCache.cacheFile(context, "ru", "Round one")

        assertEquals(first.absolutePath, second.absolutePath)
        assertNotEquals(first.absolutePath, otherLocale.absolutePath)
    }

    @Test
    fun `allExist returns true only when every phrase has a cached file`() {
        val phrases = listOf("Round one", "Round two", "Training finished")
        TtsCache.cacheFile(context, "en", phrases[0]).writeText("cached")
        TtsCache.cacheFile(context, "en", phrases[1]).writeText("cached")

        assertFalse(TtsCache.allExist(context, "en", phrases))

        TtsCache.cacheFile(context, "en", phrases[2]).writeText("cached")

        assertTrue(TtsCache.allExist(context, "en", phrases))
    }
}
