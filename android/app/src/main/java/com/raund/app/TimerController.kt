package com.raund.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.raund.app.timer.TimerEngine
import com.raund.app.timer.TimerEvent
import com.raund.app.timer.TimerProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "RaundTimer"

data class TimerUiState(
    val running: Boolean = false,
    val paused: Boolean = false,
    val finished: Boolean = false,
    val currentRound: String = "",
    val remaining: Int = 0,
    val roundTotal: Int = 1,
    val roundInfo: String = ""
)

/**
 * Runs the timer in an application-scoped coroutine so it survives activity
 * stop/destroy (e.g. screen off). UI observes state and plays sounds only when resumed.
 */
class TimerController(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(TimerUiState())
    val state: StateFlow<TimerUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<TimerEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<TimerEvent> = _events.asSharedFlow()

    private var screenOffReceiver: BroadcastReceiver? = null
    private var screenOffPauseEnabled = false

    fun start(profile: TimerProfile, screenOffPause: Boolean) {
        if (_state.value.running) return
        if (profile.rounds.isEmpty()) return
        Log.d(TAG, "timer start profile=${profile.name} screenOffPause=$screenOffPause")
        screenOffPauseEnabled = screenOffPause
        if (screenOffPause) {
            screenOffReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                        setPaused(true)
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
            }
        }
        _state.update { it.copy(running = true, paused = false, finished = false) }
        scope.launch {
            val engine = TimerEngine(profile) { event ->
                when (event) {
                    is TimerEvent.RoundStart -> {
                        _state.update {
                            it.copy(
                                currentRound = event.round.name,
                                remaining = event.round.durationSeconds,
                                roundTotal = event.round.durationSeconds,
                                roundInfo = "${event.roundIndex + 1} / ${event.totalRounds}"
                            )
                        }
                        _events.tryEmit(event)
                    }
                    is TimerEvent.Tick -> {
                        _state.update { it.copy(remaining = event.remainingSeconds) }
                        _events.tryEmit(event)
                    }
                    is TimerEvent.Warn10 -> { _events.tryEmit(event) }
                    is TimerEvent.RoundEnd -> { _events.tryEmit(event) }
                    is TimerEvent.TrainingEnd -> {
                        _state.update {
                            it.copy(running = false, finished = true, paused = false)
                        }
                        _events.tryEmit(event)
                    }
                }
            }
            engine.advance()
            while (scope.isActive && _state.value.running) {
                if (_state.value.paused) {
                    delay(500L)
                } else {
                    delay(1000L)
                    if (!engine.advance()) break
                }
            }
            unregisterScreenOffReceiver()
            Log.d(TAG, "timer loop exited running=${_state.value.running} finished=${_state.value.finished}")
        }
    }

    fun stop() {
        Log.d(TAG, "timer stop")
        _state.update { it.copy(running = false, finished = true, paused = false) }
        unregisterScreenOffReceiver()
    }

    fun setPaused(paused: Boolean) {
        _state.update { it.copy(paused = paused) }
    }

    fun resetForRestart() {
        _state.update {
            it.copy(finished = false, paused = false, currentRound = "", remaining = 0, roundTotal = 1, roundInfo = "")
        }
    }

    private fun unregisterScreenOffReceiver() {
        screenOffReceiver?.let { receiver ->
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
            screenOffReceiver = null
        }
    }

    fun cancelScope() {
        scope.cancel()
    }
}
