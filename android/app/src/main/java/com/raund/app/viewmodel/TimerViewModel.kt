package com.raund.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.raund.app.RaundApplication
import com.raund.app.TimerStateHolder
import com.raund.app.data.repository.ProfileRepository
import com.raund.app.timer.TimerProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TimerViewModel(
    val profileId: String,
    application: Application
) : AndroidViewModel(application) {

    private val repository: ProfileRepository = (application as RaundApplication).profileRepository

    private val _profile = MutableStateFlow<TimerProfile?>(null)
    private val _cacheReady = MutableStateFlow(false)
    private val _finished = MutableStateFlow(false)
    private val _prevTimerRunning = MutableStateFlow(false)
    private val _isRefreshing = MutableStateFlow(false)

    val timerState = TimerStateHolder.state
    val profile: StateFlow<TimerProfile?> = _profile.asStateFlow()
    val cacheReady: StateFlow<Boolean> = _cacheReady.asStateFlow()
    val finished: StateFlow<Boolean> = _finished.asStateFlow()
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        viewModelScope.launch {
            _profile.value = repository.getProfileWithRounds(profileId)
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
            try {
                repository.syncSingleProfile(profileId)
                _profile.value = repository.getProfileWithRounds(profileId)
                _cacheReady.value = false
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun setCacheReady(ready: Boolean) {
        _cacheReady.value = ready
    }

    fun setFinished(finished: Boolean) {
        _finished.value = finished
    }
}
