package com.raund.app.timer

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class TimerRound(
    val name: String,
    val durationSeconds: Int,
    val warn10sec: Boolean
) : Parcelable

sealed class TimerEvent {
    data class RoundStart(val roundIndex: Int, val round: TimerRound, val totalRounds: Int) : TimerEvent()
    data class Tick(val roundIndex: Int, val round: TimerRound, val remainingSeconds: Int, val totalRounds: Int) : TimerEvent()
    data class Warn10(val roundIndex: Int, val round: TimerRound, val totalRounds: Int) : TimerEvent()
    data class RoundEnd(val roundIndex: Int, val round: TimerRound, val totalRounds: Int) : TimerEvent()
    data object TrainingEnd : TimerEvent()
}

data class TimerState(
    val roundIndex: Int,
    val remainingSeconds: Int
)
