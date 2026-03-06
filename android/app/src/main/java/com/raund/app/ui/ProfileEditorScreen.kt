package com.raund.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.raund.app.MAX_ROUNDS_PER_PROFILE
import com.raund.app.MAX_ROUND_DURATION_SECONDS
import com.raund.app.MIN_ROUND_DURATION_SECONDS
import com.raund.app.R
import com.raund.app.viewmodel.ProfileEditorViewModel
import com.raund.app.viewmodel.RoundEditState
import com.raund.app.viewmodel.SaveResult

private const val MAX_PROFILE_NAME_LENGTH = 30
private const val MAX_ROUND_NAME_LENGTH = 20

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
    viewModel: ProfileEditorViewModel,
    profileId: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(saveResult) {
        val result = saveResult
        if (result is SaveResult.Error) {
            snackbarHostState.showSnackbar(result.message)
            viewModel.clearSaveResult()
        }
    }

    val name = state.name
    val emoji = state.emoji
    val showNameError = state.showNameError
    val showDeleteConfirm = state.showDeleteConfirm
    val selectedRoundIndices = state.selectedRoundIndices
    val showCopyNDialog = state.showCopyNDialog
    val copyNValue = state.copyNValue
    val rounds = state.rounds
    val isNew = profileId == null || profileId == "new"
    val isNameValid = name.trim().isNotEmpty()
    val draggedIndex = state.draggedIndex
    val dragTargetIndex = state.dragTargetIndex
    val dragOffset = state.dragOffset
    val localDensity = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (isNew) R.string.new_profile else R.string.edit_profile),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val pullToRefreshState = rememberPullToRefreshState()
        if (pullToRefreshState.isRefreshing) {
            LaunchedEffect(true) {
                viewModel.refresh()
            }
        }
        LaunchedEffect(isRefreshing) {
            if (!isRefreshing) pullToRefreshState.endRefresh()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            userScrollEnabled = draggedIndex == null
        ) {
            item(key = "header") {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                value = name,
                onValueChange = { newValue ->
                    val limited = newValue.take(MAX_PROFILE_NAME_LENGTH)
                    viewModel.updateName(limited)
                    if (showNameError && limited.trim().isNotEmpty()) {
                        viewModel.setShowNameError(false)
                    }
                },
                label = { Text(stringResource(R.string.profile_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                isError = showNameError && !isNameValid
            )
            if (showNameError && !isNameValid) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.profile_name_required),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = emoji,
                onValueChange = { viewModel.updateEmoji(it) },
                label = { Text(stringResource(R.string.profile_emoji)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                stringResource(R.string.rounds),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            }
            itemsIndexed(rounds, key = { _, r -> r.stableId }) { index, round ->
                val rName = round.name
                val dur = round.duration
                val warn = round.warn10sec
                val isSelected = selectedRoundIndices.contains(index)
                val isDragged = draggedIndex == index
                val isDragging = draggedIndex != null

                val showGapBefore = isDragging && dragTargetIndex != null && draggedIndex != null
                        && dragTargetIndex != draggedIndex && index == dragTargetIndex && dragTargetIndex < draggedIndex
                if (showGapBefore) {
                    Spacer(modifier = Modifier.height(48.dp))
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            viewModel.setItemHeightPx(coords.size.height.toFloat())
                        }
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer {
                            if (isDragged) {
                                translationY = dragOffset
                                scaleX = 1.03f
                                scaleY = 1.03f
                                shadowElevation = 16f
                            }
                        }
                        .dragReorder(
                            index = index,
                            state = state,
                            viewModel = viewModel,
                            roundsSize = rounds.size,
                            haptics = haptics,
                            density = localDensity,
                            onTapWhenNoDrag = null
                        ),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = if (isSelected)
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    else null
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                    )
                                    .dragReorder(
                                        index = index,
                                        state = state,
                                        viewModel = viewModel,
                                        roundsSize = rounds.size,
                                        haptics = haptics,
                                        density = localDensity,
                                        onTapWhenNoDrag = { viewModel.toggleRoundSelection(index) }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${index + 1}",
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = rName,
                                onValueChange = { newVal ->
                                    viewModel.updateRoundAt(index, round.copy(name = newVal.take(MAX_ROUND_NAME_LENGTH)))
                                },
                                label = { Text(stringResource(R.string.round_name)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedContainerColor = MaterialTheme.colorScheme.surface
                                )
                            )
                            IconButton(
                                onClick = { viewModel.removeRoundAt(index) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.delete_round),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = dur,
                                onValueChange = {
                                    val digitsOnly = it.filter { char -> char.isDigit() }
                                    val currentDur = digitsOnly.toIntOrNull() ?: 0
                                    val newWarn = if (currentDur <= 10) false else warn
                                    viewModel.updateRoundAt(index, round.copy(duration = digitsOnly, warn10sec = newWarn))
                                },
                                label = { Text(stringResource(R.string.duration_seconds)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                visualTransformation = VisualTransformation.None,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 16.dp)
                                    .onFocusChanged { focusState ->
                                        if (!focusState.isFocused) {
                                            val parsed = dur.toIntOrNull() ?: 0
                                            val clamped = parsed.coerceIn(MIN_ROUND_DURATION_SECONDS, MAX_ROUND_DURATION_SECONDS)
                                            if (clamped != parsed) {
                                                viewModel.updateRoundAt(
                                                    index,
                                                    round.copy(
                                                        duration = clamped.toString(),
                                                        warn10sec = if (clamped <= 10) false else warn
                                                    )
                                                )
                                            }
                                        }
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedContainerColor = MaterialTheme.colorScheme.surface
                                )
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                val currentDur = dur.toIntOrNull() ?: 0
                                Switch(
                                    checked = warn && currentDur > 10,
                                    onCheckedChange = { viewModel.updateRoundAt(index, round.copy(warn10sec = it)) },
                                    enabled = currentDur > 10
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.warn_10_sec),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                val showGapAfter = isDragging && dragTargetIndex != null && draggedIndex != null
                        && dragTargetIndex != draggedIndex && index == dragTargetIndex && dragTargetIndex > draggedIndex
                Spacer(modifier = Modifier.height(if (showGapAfter) 48.dp else 12.dp))
            }
            item(key = "copyRow") {
            if (selectedRoundIndices.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.setCopyNValue("12")
                            viewModel.setShowCopyNDialog(true)
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.copy_rounds) + " × N",
                            fontSize = 14.sp
                        )
                    }
                    TextButton(onClick = { viewModel.clearSelection() }) {
                        Text(stringResource(R.string.cancel), fontSize = 14.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            }
            item(key = "addRound") {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { viewModel.addRound() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = rounds.size < MAX_ROUNDS_PER_PROFILE
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_round))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.add_round), fontSize = 16.sp)
            }
            }
            item(key = "save") {
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    viewModel.saveProfile(context, if (isNew) null else profileId, onBack)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = isNameValid
            ) { 
                Text(stringResource(R.string.save), fontSize = 18.sp, fontWeight = FontWeight.Bold) 
            }
            }
            item(key = "delete") {
            if (!isNew) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { viewModel.setShowDeleteConfirm(true) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_profile))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.delete_profile), fontSize = 16.sp)
                }
            }
            }
            item(key = "bottom") {
            Spacer(modifier = Modifier.height(48.dp))
            }
        }
        PullToRefreshContainer(
            state = pullToRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
        }
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { viewModel.setShowDeleteConfirm(false) },
                title = { Text(stringResource(R.string.delete_profile_confirm_title), fontWeight = FontWeight.SemiBold) },
                text = { Text(stringResource(R.string.delete_profile_confirm)) },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.setShowDeleteConfirm(false)
                            viewModel.deleteProfile(profileId!!, onBack)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.delete_profile))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.setShowDeleteConfirm(false) }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
        if (showCopyNDialog) {
            val sortedIndices = selectedRoundIndices.sorted()
            val block = sortedIndices.map { rounds[it] }
            AlertDialog(
                onDismissRequest = { viewModel.setShowCopyNDialog(false) },
                title = { Text(stringResource(R.string.number_of_copies), fontWeight = FontWeight.SemiBold) },
                text = {
                    Column {
                        Text(stringResource(R.string.copy_selected_n_times, block.size), style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = copyNValue,
                            onValueChange = { viewModel.setCopyNValue(it.filter { c -> c.isDigit() }.take(2)) },
                            label = { Text(stringResource(R.string.number_of_copies)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.setShowCopyNDialog(false)
                            val copies = copyNValue.toIntOrNull()?.coerceIn(1, 99) ?: 1
                            val newRounds = (state.rounds + (1 until copies).flatMap {
                                block.map { r -> RoundEditState(r.name, r.duration, r.warn10sec) }
                            }).take(MAX_ROUNDS_PER_PROFILE)
                            viewModel.setRounds(newRounds)
                            viewModel.clearSelection()
                        }
                    ) {
                        Text(stringResource(R.string.copy_rounds))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.setShowCopyNDialog(false) }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}
