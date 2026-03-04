package com.raund.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TimerState(
    val remaining: Int = 0,
    val roundName: String = "",
    val roundTotal: Int = 0,
    val roundIndex: Int = 0,
    val totalRounds: Int = 0,
    val isRunning: Boolean = false,
    val paused: Boolean = false
)

object TimerStateHolder {
    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    fun update(
        remaining: Int? = null,
        roundName: String? = null,
        roundTotal: Int? = null,
        roundIndex: Int? = null,
        totalRounds: Int? = null,
        isRunning: Boolean? = null,
        paused: Boolean? = null
    ) {
        _state.update { current ->
            TimerState(
                remaining = remaining ?: current.remaining,
                roundName = roundName ?: current.roundName,
                roundTotal = roundTotal ?: current.roundTotal,
                roundIndex = roundIndex ?: current.roundIndex,
                totalRounds = totalRounds ?: current.totalRounds,
                isRunning = isRunning ?: current.isRunning,
                paused = paused ?: current.paused
            )
        }
    }

    fun reset() {
        _state.value = TimerState()
    }
}
