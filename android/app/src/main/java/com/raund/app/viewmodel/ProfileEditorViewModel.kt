package com.raund.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.raund.app.RaundApplication
import com.raund.app.data.repository.ProfileRepository
import com.raund.app.timer.TimerProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

data class RoundEditState(
    val name: String,
    val duration: String,
    val warn10sec: Boolean
)

data class ProfileEditorState(
    val name: String = "",
    val emoji: String = "⏱",
    val rounds: List<RoundEditState> = emptyList(),
    val selectedRoundIndices: Set<Int> = emptySet(),
    val showNameError: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val showCopyNDialog: Boolean = false,
    val copyNValue: String = "12",
    val draggedIndex: Int? = null,
    val dragOffset: Float = 0f,
    val itemHeightPx: Float = 0f
)

class ProfileEditorViewModel(
    private val profileId: String?,
    application: Application
) : AndroidViewModel(application) {

    private val repository: ProfileRepository = (application as RaundApplication).profileRepository

    private val _state = MutableStateFlow(ProfileEditorState())
    val state: StateFlow<ProfileEditorState> = _state.asStateFlow()

    init {
        if (profileId != null && profileId != "new") {
            viewModelScope.launch {
                val profile = repository.getProfileWithRounds(profileId)
                if (profile != null) {
                    _state.value = _state.value.copy(
                        name = profile.name,
                        emoji = profile.emoji.ifBlank { "⏱" },
                        rounds = profile.rounds.map { r ->
                            RoundEditState(
                                name = r.name,
                                duration = r.durationSeconds.toString(),
                                warn10sec = r.warn10sec
                            )
                        }
                    )
                }
            }
        }
    }

    fun updateName(name: String) {
        _state.value = _state.value.copy(
            name = name.take(30),
            showNameError = _state.value.showNameError && name.trim().isNotEmpty()
        )
    }

    fun updateEmoji(emoji: String) {
        val single = if (emoji.isEmpty()) "" else {
            val bi = java.text.BreakIterator.getCharacterInstance()
            bi.setText(emoji)
            val firstEnd = bi.next()
            if (firstEnd != java.text.BreakIterator.DONE) emoji.substring(0, firstEnd) else emoji
        }
        _state.value = _state.value.copy(emoji = single)
    }

    fun setShowNameError(show: Boolean) {
        _state.value = _state.value.copy(showNameError = show)
    }

    fun setRounds(rounds: List<RoundEditState>) {
        _state.value = _state.value.copy(rounds = rounds)
    }

    fun updateRoundAt(index: Int, round: RoundEditState) {
        val list = _state.value.rounds.toMutableList()
        if (index in list.indices) {
            list[index] = round
            _state.value = _state.value.copy(rounds = list)
        }
    }

    fun setSelectedRoundIndices(indices: Set<Int>) {
        _state.value = _state.value.copy(selectedRoundIndices = indices)
    }

    fun toggleRoundSelection(index: Int) {
        val current = _state.value.selectedRoundIndices
        _state.value = _state.value.copy(
            selectedRoundIndices = if (index in current) current - index else current + index
        )
    }

    fun addRound() {
        _state.value = _state.value.copy(
            rounds = _state.value.rounds + RoundEditState("", "60", false)
        )
    }

    fun removeRoundAt(index: Int) {
        val list = _state.value.rounds.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _state.value = _state.value.copy(rounds = list, selectedRoundIndices = emptySet())
        }
    }

    fun reorderRounds(fromIndex: Int, toIndex: Int) {
        val list = _state.value.rounds.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices && fromIndex != toIndex) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            _state.value = _state.value.copy(rounds = list)
        }
    }

    fun setDraggedIndex(index: Int?) {
        _state.value = _state.value.copy(draggedIndex = index)
    }

    fun setDragOffset(offset: Float) {
        _state.value = _state.value.copy(dragOffset = offset)
    }

    fun addDragOffset(delta: Float) {
        _state.value = _state.value.copy(dragOffset = _state.value.dragOffset + delta)
    }

    fun setItemHeightPx(px: Float) {
        if (_state.value.itemHeightPx == 0f) {
            _state.value = _state.value.copy(itemHeightPx = px)
        }
    }

    fun setShowDeleteConfirm(show: Boolean) {
        _state.value = _state.value.copy(showDeleteConfirm = show)
    }

    fun setShowCopyNDialog(show: Boolean) {
        _state.value = _state.value.copy(showCopyNDialog = show)
    }

    fun setCopyNValue(value: String) {
        _state.value = _state.value.copy(copyNValue = value)
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selectedRoundIndices = emptySet())
    }

    fun getRepository(): ProfileRepository = repository
}
