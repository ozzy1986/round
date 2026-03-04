package com.raund.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Immutable timer state emitted by TimerService and collected by the timer UI.
 * Replaces Intent-based broadcast for per-second updates.
 */
data class TimerState(
    val remaining: Int = 0,
    val roundName: String = "",
    val roundTotal: Int = 0,
    val roundIndex: Int = 0,
    val totalRounds: Int = 0,
    val isRunning: Boolean = false,
    val paused: Boolean = false
)

/**
 * Singleton holding the current timer state. TimerService updates it;
 * TimerScreen collects via state.asStateFlow().
 */
object TimerStateHolder {
    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    fun update(
        remaining: Int = _state.value.remaining,
        roundName: String = _state.value.roundName,
        roundTotal: Int = _state.value.roundTotal,
        roundIndex: Int = _state.value.roundIndex,
        totalRounds: Int = _state.value.totalRounds,
        isRunning: Boolean = _state.value.isRunning,
        paused: Boolean = _state.value.paused
    ) {
        _state.value = TimerState(
            remaining = remaining,
            roundName = roundName,
            roundTotal = roundTotal,
            roundIndex = roundIndex,
            totalRounds = totalRounds,
            isRunning = isRunning,
            paused = paused
        )
    }

    fun reset() {
        _state.value = TimerState()
    }
}
