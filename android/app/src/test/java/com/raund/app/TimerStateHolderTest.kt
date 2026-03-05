package com.raund.app

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TimerStateHolderTest {

    @Before
    fun setup() {
        TimerStateHolder.reset()
    }

    @Test
    fun `reset returns default state`() {
        TimerStateHolder.update(remaining = 42, roundName = "Work", isRunning = true)
        TimerStateHolder.reset()
        val s = TimerStateHolder.state.value
        assertEquals(0, s.remaining)
        assertEquals("", s.roundName)
        assertEquals(0, s.roundTotal)
        assertEquals(0, s.roundIndex)
        assertEquals(0, s.totalRounds)
        assertFalse(s.isRunning)
        assertFalse(s.paused)
    }

    @Test
    fun `update sets only provided fields`() {
        TimerStateHolder.update(remaining = 10, roundName = "Rest", roundTotal = 30, roundIndex = 2, totalRounds = 5, isRunning = true, paused = false)
        val s1 = TimerStateHolder.state.value
        assertEquals(10, s1.remaining)
        assertEquals("Rest", s1.roundName)
        assertEquals(30, s1.roundTotal)
        assertEquals(2, s1.roundIndex)
        assertEquals(5, s1.totalRounds)
        assertTrue(s1.isRunning)
        assertFalse(s1.paused)

        TimerStateHolder.update(paused = true)
        val s2 = TimerStateHolder.state.value
        assertEquals(10, s2.remaining)
        assertEquals("Rest", s2.roundName)
        assertTrue(s2.paused)
        assertTrue(s2.isRunning)
    }

    @Test
    fun `concurrent updates are thread-safe`() {
        val threads = (1..50).map { i ->
            Thread {
                TimerStateHolder.update(remaining = i, roundName = "R$i")
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        val s = TimerStateHolder.state.value
        assertTrue(s.remaining in 1..50)
        assertTrue(s.roundName.startsWith("R"))
    }
}
