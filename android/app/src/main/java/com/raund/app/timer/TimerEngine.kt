package com.raund.app.timer

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Same event contract as backend timer engine: round_start, tick, warn10, round_end, training_end.
 */
class TimerEngine(
    private val profile: TimerProfile,
    private val onEvent: (TimerEvent) -> Unit
) {
    private var state: TimerState? = getInitialState()

    private fun getInitialState(): TimerState? {
        val rounds = profile.rounds
        if (rounds.isEmpty()) return null
        return TimerState(roundIndex = 0, remainingSeconds = rounds[0].durationSeconds)
    }

    fun advance(): Boolean {
        val s = state ?: return false
        val rounds = profile.rounds
        if (rounds.isEmpty()) {
            onEvent(TimerEvent.TrainingEnd)
            return false
        }
        if (s.roundIndex >= rounds.size) {
            onEvent(TimerEvent.TrainingEnd)
            return false
        }

        val round = rounds[s.roundIndex]
        val totalRounds = rounds.size

        if (s.remainingSeconds == round.durationSeconds) {
            onEvent(TimerEvent.RoundStart(s.roundIndex, round, totalRounds))
        }
        onEvent(TimerEvent.Tick(s.roundIndex, round, s.remainingSeconds, totalRounds))
        if (round.warn10sec && s.remainingSeconds == 10) {
            onEvent(TimerEvent.Warn10(s.roundIndex, round, totalRounds))
        }
        if (s.remainingSeconds == 0) {
            onEvent(TimerEvent.RoundEnd(s.roundIndex, round, totalRounds))
            val nextIndex = s.roundIndex + 1
            if (nextIndex >= rounds.size) {
                onEvent(TimerEvent.TrainingEnd)
                state = null
                return false
            }
            state = TimerState(nextIndex, rounds[nextIndex].durationSeconds)
            return true
        }
        state = TimerState(s.roundIndex, s.remainingSeconds - 1)
        return true
    }
}

@Parcelize
data class TimerProfile(
    val name: String,
    val emoji: String,
    val rounds: List<TimerRound>
) : Parcelable
