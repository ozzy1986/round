package com.raund.app.timer

import org.junit.Assert.*
import org.junit.Test

class TimerEngineTest {

    private fun collectEvents(profile: TimerProfile): List<TimerEvent> {
        val events = mutableListOf<TimerEvent>()
        val engine = TimerEngine(profile) { events.add(it) }
        while (engine.advance()) { /* tick */ }
        return events
    }

    @Test
    fun `empty rounds emits no events and advance returns false`() {
        val profile = TimerProfile("Test", "⏱", emptyList())
        val events = mutableListOf<TimerEvent>()
        val engine = TimerEngine(profile) { events.add(it) }
        val result = engine.advance()
        assertFalse(result)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `single round with 1 second emits correct event sequence`() {
        val round = TimerRound("Work", 1, false)
        val profile = TimerProfile("Test", "⏱", listOf(round))
        val events = collectEvents(profile)

        assertEquals(TimerEvent.RoundStart(0, round, 1), events[0])
        assertEquals(TimerEvent.Tick(0, round, 1, 1), events[1])
        assertEquals(TimerEvent.Tick(0, round, 0, 1), events[2])
        assertEquals(TimerEvent.RoundEnd(0, round, 1), events[3])
        assertEquals(TimerEvent.TrainingEnd, events[4])
        assertEquals(5, events.size)
    }

    @Test
    fun `two rounds with 2 seconds each produce correct round transitions`() {
        val r1 = TimerRound("Work", 2, false)
        val r2 = TimerRound("Rest", 2, false)
        val profile = TimerProfile("Test", "⏱", listOf(r1, r2))
        val events = collectEvents(profile)

        val roundStarts = events.filterIsInstance<TimerEvent.RoundStart>()
        assertEquals(2, roundStarts.size)
        assertEquals(0, roundStarts[0].roundIndex)
        assertEquals(1, roundStarts[1].roundIndex)

        val roundEnds = events.filterIsInstance<TimerEvent.RoundEnd>()
        assertEquals(2, roundEnds.size)

        assertTrue(events.last() is TimerEvent.TrainingEnd)
    }

    @Test
    fun `warn10 fires when warn10sec is true and remaining equals 10`() {
        val round = TimerRound("Work", 12, true)
        val profile = TimerProfile("Test", "⏱", listOf(round))
        val events = collectEvents(profile)

        val warns = events.filterIsInstance<TimerEvent.Warn10>()
        assertEquals(1, warns.size)
        assertEquals(0, warns[0].roundIndex)
    }

    @Test
    fun `warn10 does not fire when warn10sec is false`() {
        val round = TimerRound("Work", 12, false)
        val profile = TimerProfile("Test", "⏱", listOf(round))
        val events = collectEvents(profile)

        val warns = events.filterIsInstance<TimerEvent.Warn10>()
        assertTrue(warns.isEmpty())
    }

    @Test
    fun `warn10 does not fire for short rounds under 10 seconds`() {
        val round = TimerRound("Work", 5, true)
        val profile = TimerProfile("Test", "⏱", listOf(round))
        val events = collectEvents(profile)

        val warns = events.filterIsInstance<TimerEvent.Warn10>()
        assertTrue(warns.isEmpty())
    }

    @Test
    fun `tick counts down from duration to zero`() {
        val round = TimerRound("Work", 3, false)
        val profile = TimerProfile("Test", "⏱", listOf(round))
        val events = collectEvents(profile)

        val ticks = events.filterIsInstance<TimerEvent.Tick>()
        val remainingValues = ticks.map { it.remainingSeconds }
        assertEquals(listOf(3, 2, 1, 0), remainingValues)
    }

    @Test
    fun `totalRounds is correct in all events`() {
        val r1 = TimerRound("A", 1, false)
        val r2 = TimerRound("B", 1, false)
        val r3 = TimerRound("C", 1, false)
        val profile = TimerProfile("Test", "⏱", listOf(r1, r2, r3))
        val events = collectEvents(profile)

        events.forEach { event ->
            when (event) {
                is TimerEvent.RoundStart -> assertEquals(3, event.totalRounds)
                is TimerEvent.Tick -> assertEquals(3, event.totalRounds)
                is TimerEvent.Warn10 -> assertEquals(3, event.totalRounds)
                is TimerEvent.RoundEnd -> assertEquals(3, event.totalRounds)
                is TimerEvent.TrainingEnd -> { }
            }
        }
    }

    @Test
    fun `round name propagates correctly`() {
        val round = TimerRound("Heavy Bag", 2, false)
        val profile = TimerProfile("Boxing", "🥊", listOf(round))
        val events = collectEvents(profile)

        val roundStart = events.filterIsInstance<TimerEvent.RoundStart>().first()
        assertEquals("Heavy Bag", roundStart.round.name)
    }

    @Test
    fun `advance returns false after training ends`() {
        val round = TimerRound("Work", 1, false)
        val profile = TimerProfile("Test", "⏱", listOf(round))
        val events = mutableListOf<TimerEvent>()
        val engine = TimerEngine(profile) { events.add(it) }

        while (engine.advance()) { }
        val extra = engine.advance()
        assertFalse(extra)
    }
}
