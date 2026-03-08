package com.raund.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.raund.app.RaundApplication
import com.raund.app.TimerStateHolder
import com.raund.app.data.repository.ProfileRepository
import com.raund.app.timer.TimerProfile
import android.content.Context
import com.raund.app.LocaleManager
import com.raund.app.R
import com.raund.app.tts.TtsCache
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TimerDisplayState(
    val remaining: Int = 0,
    val roundTotal: Int = 1,
    val currentRound: String = "",
    val roundInfo: String = ""
)

class TimerViewModel(
    val profileId: String,
    application: Application
) : AndroidViewModel(application) {

    private val repository: ProfileRepository = (application as RaundApplication).profileRepository

    private val _profile = MutableStateFlow<TimerProfile?>(null)
    private val _startReady = MutableStateFlow(false)
    private val _finished = MutableStateFlow(false)
    private val _prevTimerRunning = MutableStateFlow(false)
    private val _isRefreshing = MutableStateFlow(false)

    val timerState = TimerStateHolder.state
    val profile: StateFlow<TimerProfile?> = _profile.asStateFlow()
    val startReady: StateFlow<Boolean> = _startReady.asStateFlow()
    val finished: StateFlow<Boolean> = _finished.asStateFlow()
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    val isRunning: StateFlow<Boolean> = timerState
        .map { it.isRunning }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), timerState.value.isRunning)
    val isPaused: StateFlow<Boolean> = timerState
        .map { it.paused }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), timerState.value.paused)
    val displayState: StateFlow<TimerDisplayState> = combine(profile, timerState) { timerProfile, state ->
        val firstRound = timerProfile?.rounds?.firstOrNull()
        val running = state.isRunning
        val remaining = if (running) state.remaining else (firstRound?.durationSeconds ?: 0)
        val roundTotal = if (running) state.roundTotal else (firstRound?.durationSeconds ?: 1)
        val currentRound = if (running) state.roundName else (firstRound?.name ?: "")
        val roundInfo = if (running) {
            "${state.roundIndex} / ${state.totalRounds}"
        } else if ((timerProfile?.rounds?.size ?: 0) > 0) {
            "1 / ${timerProfile!!.rounds.size}"
        } else {
            ""
        }
        TimerDisplayState(
            remaining = remaining,
            roundTotal = roundTotal,
            currentRound = currentRound,
            roundInfo = roundInfo
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        TimerDisplayState()
    )

    init {
        viewModelScope.launch {
            _profile.value = repository.getProfileWithRounds(profileId)
            _startReady.value = true
        }
        viewModelScope.launch {
            timerState.collect { state ->
                if (_prevTimerRunning.value && !state.isRunning) {
                    _finished.value = true
                }
                _prevTimerRunning.value = state.isRunning
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _startReady.value = false
            try {
                repository.syncSingleProfile(profileId)
                _profile.value = repository.getProfileWithRounds(profileId)
                _startReady.value = true
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun prefillTts(context: Context) {
        val timerProfile = _profile.value ?: return
        if (timerProfile.rounds.isEmpty()) return
        val appContext = context.applicationContext
        val locale = LocaleManager.currentLanguageTag(appContext)
        val finishedText = appContext.getString(R.string.timer_finished)
        val phrases = TtsCache.buildPhraseList(timerProfile, finishedText)
        repository.prefillTtsInBackground(appContext, locale, phrases)
    }

    fun setFinished(finished: Boolean) {
        _finished.value = finished
    }
}
