package com.raund.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.raund.app.RaundApplication
import com.raund.app.data.dao.RoundStats
import com.raund.app.data.entity.Profile
import com.raund.app.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ProfileListState(
    val profiles: List<Profile> = emptyList(),
    val roundStats: Map<String, RoundStats> = emptyMap()
)

class ProfileListViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ProfileRepository = (application as RaundApplication).profileRepository

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
}
