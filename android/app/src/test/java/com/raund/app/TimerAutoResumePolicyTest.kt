package com.raund.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerAutoResumePolicyTest {

    @Test
    fun `resumes after screen on when keyguard is already gone and background running is allowed`() {
        assertTrue(
            shouldResumeAfterScreenOn(
                running = true,
                autoPausedByScreenOff = true,
                keepRunningOnScreenOff = false,
                keepRunningWhenLeavingApp = true,
                keyguardLocked = false
            )
        )
    }

    @Test
    fun `does not resume on screen on while keyguard is still locked`() {
        assertFalse(
            shouldResumeAfterScreenOn(
                running = true,
                autoPausedByScreenOff = true,
                keepRunningOnScreenOff = false,
                keepRunningWhenLeavingApp = true,
                keyguardLocked = true
            )
        )
    }

    @Test
    fun `does not resume on screen on when leaving app should still pause`() {
        assertFalse(
            shouldResumeAfterScreenOn(
                running = true,
                autoPausedByScreenOff = true,
                keepRunningOnScreenOff = false,
                keepRunningWhenLeavingApp = false,
                keyguardLocked = false
            )
        )
    }

    @Test
    fun `does not resume on screen on when timer was not auto paused by screen off`() {
        assertFalse(
            shouldResumeAfterScreenOn(
                running = true,
                autoPausedByScreenOff = false,
                keepRunningOnScreenOff = false,
                keepRunningWhenLeavingApp = true,
                keyguardLocked = false
            )
        )
    }

    @Test
    fun `resumes after unlock broadcast even if keyguard state was still locked on screen on`() {
        assertTrue(
            shouldResumeAfterUnlock(
                running = true,
                autoPausedByScreenOff = true,
                keepRunningOnScreenOff = false,
                keepRunningWhenLeavingApp = true
            )
        )
    }
}
