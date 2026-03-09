package com.raund.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.raund.app.RaundApplication
import com.raund.app.data.dao.RoundStats
import com.raund.app.data.entity.Profile
import com.raund.app.data.remote.BugReportPayloadFactory
import com.raund.app.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileListState(
    val profiles: List<Profile> = emptyList(),
    val roundStats: Map<String, RoundStats> = emptyMap()
)

class ProfileListViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ProfileRepository = (application as RaundApplication).profileRepository

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    private val _isSubmittingBugReport = MutableStateFlow(false)
    val isSubmittingBugReport: StateFlow<Boolean> = _isSubmittingBugReport.asStateFlow()

    val listState: StateFlow<ProfileListState> = combine(
        repository.profiles,
        repository.roundStats
    ) { profiles, stats ->
        ProfileListState(profiles = profiles, roundStats = stats)
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = ProfileListState(
            profiles = repository.cachedProfiles,
            roundStats = repository.cachedRoundStats
        )
    )

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repository.forceSync()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun submitBugReport(
        message: String,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (_isSubmittingBugReport.value) return

        viewModelScope.launch {
            _isSubmittingBugReport.value = true
            try {
                repository.submitBugReport(
                    BugReportPayloadFactory.create(
                        context = getApplication(),
                        message = message,
                        screen = BUG_REPORT_SCREEN
                    )
                )
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            } finally {
                _isSubmittingBugReport.value = false
            }
        }
    }

    companion object {
        private const val BUG_REPORT_SCREEN = "profile_list_settings"
    }
}
