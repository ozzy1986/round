package com.raund.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.raund.app.LocaleManager
import com.raund.app.R
import com.raund.app.data.repository.ProfileRepository
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private const val MAX_PROFILE_NAME_LENGTH = 30
private const val MAX_ROUND_NAME_LENGTH = 20

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
    repository: ProfileRepository,
    profileId: String?,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("⏱") }
    var showNameError by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var selectedRoundIndices by remember { mutableStateOf(setOf<Int>()) }
    var showCopyNDialog by remember { mutableStateOf(false) }
    var copyNValue by remember { mutableStateOf("12") }
    val rounds = remember { mutableStateListOf<Triple<String, String, Boolean>>() }
    val isNew = profileId == null || profileId == "new"
    val isNameValid = name.trim().isNotEmpty()

    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var itemHeightPx by remember { mutableStateOf(0f) }
    val localDensity = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(profileId) {
        if (!isNew && profileId != null) {
            val profile = repository.getProfileWithRounds(profileId)
            name = profile?.name ?: ""
            emoji = profile?.emoji ?: "⏱"
            rounds.clear()
            profile?.rounds?.forEach { r ->
                rounds.add(Triple(r.name, r.durationSeconds.toString(), r.warn10sec))
            }
        }
    }

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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState(), enabled = draggedIndex == null)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { newValue ->
                    val limited = newValue.take(MAX_PROFILE_NAME_LENGTH)
                    name = limited
                    if (showNameError && limited.trim().isNotEmpty()) {
                        showNameError = false
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
                onValueChange = { newValue ->
                    if (newValue.isEmpty()) {
                        emoji = ""
                    } else {
                        val bi = java.text.BreakIterator.getCharacterInstance()
                        bi.setText(newValue)
                        val firstEnd = bi.next()
                        emoji = if (firstEnd != java.text.BreakIterator.DONE)
                            newValue.substring(0, firstEnd) else newValue
                    }
                },
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
            rounds.forEachIndexed { index, (rName, dur, warn) ->
                val isSelected = selectedRoundIndices.contains(index)
                val isDragged = draggedIndex == index
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            if (itemHeightPx == 0f) {
                                itemHeightPx = coords.size.height.toFloat()
                            }
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
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = true)
                                val longPress = awaitLongPressOrCancellation(down.id)
                                if (longPress != null) {
                                    longPress.consume()
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    draggedIndex = index
                                    dragOffset = 0f
                                    selectedRoundIndices = emptySet()

                                    val dragged = drag(longPress.id) { change ->
                                        dragOffset += change.positionChange().y
                                        change.consume()
                                    }

                                    if (dragged) {
                                        val di = draggedIndex
                                        if (di != null && itemHeightPx > 0f) {
                                            val spacerPx = with(localDensity) { 12.dp.toPx() }
                                            val stepPx = itemHeightPx + spacerPx
                                            val positions = (dragOffset / stepPx).roundToInt()
                                            val targetIdx = (di + positions).coerceIn(0, rounds.size - 1)
                                            if (targetIdx != di) {
                                                val item = rounds.removeAt(di)
                                                rounds.add(targetIdx, item)
                                            }
                                        }
                                    }
                                    draggedIndex = null
                                    dragOffset = 0f
                                }
                            }
                        },
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
                                    .pointerInput(index) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            down.consume()
                                            val longPress = awaitLongPressOrCancellation(down.id)
                                            if (longPress != null) {
                                                longPress.consume()
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                draggedIndex = index
                                                dragOffset = 0f
                                                selectedRoundIndices = emptySet()
                                                val dragged = drag(longPress.id) { change ->
                                                    dragOffset += change.positionChange().y
                                                    change.consume()
                                                }
                                                if (dragged) {
                                                    val di = draggedIndex
                                                    if (di != null && itemHeightPx > 0f) {
                                                        val spacerPx = with(localDensity) { 12.dp.toPx() }
                                                        val stepPx = itemHeightPx + spacerPx
                                                        val positions = (dragOffset / stepPx).roundToInt()
                                                        val targetIdx = (di + positions).coerceIn(0, rounds.size - 1)
                                                        if (targetIdx != di) {
                                                            val item = rounds.removeAt(di)
                                                            rounds.add(targetIdx, item)
                                                        }
                                                    }
                                                }
                                                draggedIndex = null
                                                dragOffset = 0f
                                            } else {
                                                selectedRoundIndices = if (index in selectedRoundIndices)
                                                    selectedRoundIndices - index
                                                else
                                                    selectedRoundIndices + index
                                            }
                                        }
                                    },
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
                                    rounds[index] = Triple(newVal.take(MAX_ROUND_NAME_LENGTH), dur, warn)
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
                                onClick = {
                                    rounds.removeAt(index)
                                    selectedRoundIndices = emptySet()
                                },
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
                                    rounds[index] = Triple(rName, digitsOnly, newWarn)
                                },
                                label = { Text(stringResource(R.string.duration_seconds)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f).padding(end = 16.dp),
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
                                    onCheckedChange = { rounds[index] = Triple(rName, dur, it) },
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
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (selectedRoundIndices.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            copyNValue = "12"
                            showCopyNDialog = true
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.copy_rounds) + " × N",
                            fontSize = 14.sp
                        )
                    }
                    TextButton(onClick = { selectedRoundIndices = emptySet() }) {
                        Text(stringResource(R.string.cancel), fontSize = 14.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { rounds.add(Triple("", "60", false)) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_round))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.add_round), fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    val safeName = name.trim()
                    if (safeName.isBlank()) {
                        showNameError = true
                        return@Button
                    }
                    val safeEmoji = emoji.trim().ifBlank { "⏱" }
                    scope.launch {
                        val id = if (isNew) repository.insertProfile(safeName, safeEmoji) else profileId!!
                        if (!isNew) repository.updateProfile(profileId!!, safeName, safeEmoji)
                        val roundsToSave = rounds.map { (rName, durString, warn) ->
                            val durInt = durString.toIntOrNull()?.coerceAtLeast(5) ?: 5
                            Triple(rName.take(MAX_ROUND_NAME_LENGTH), durInt, warn)
                        }
                        repository.saveRounds(id, roundsToSave)
                        val phrases = roundsToSave.map { it.first } + safeName + context.getString(R.string.timer_finished)
                        repository.prefillTtsInBackground(context, LocaleManager.currentLanguageTag(context), phrases)
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = isNameValid
            ) { 
                Text(stringResource(R.string.save), fontSize = 18.sp, fontWeight = FontWeight.Bold) 
            }
            if (!isNew) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
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
            Spacer(modifier = Modifier.height(48.dp))
        }
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text(stringResource(R.string.delete_profile_confirm_title), fontWeight = FontWeight.SemiBold) },
                text = { Text(stringResource(R.string.delete_profile_confirm)) },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteConfirm = false
                            scope.launch {
                                repository.deleteProfile(profileId!!)
                                onBack()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.delete_profile))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
        if (showCopyNDialog) {
            val sortedIndices = selectedRoundIndices.sorted()
            val block = sortedIndices.map { rounds[it] }
            AlertDialog(
                onDismissRequest = { showCopyNDialog = false },
                title = { Text(stringResource(R.string.number_of_copies), fontWeight = FontWeight.SemiBold) },
                text = {
                    Column {
                        Text(stringResource(R.string.copy_selected_n_times, block.size), style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = copyNValue,
                            onValueChange = { copyNValue = it.filter { c -> c.isDigit() }.take(2) },
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
                            showCopyNDialog = false
                            val copies = copyNValue.toIntOrNull()?.coerceIn(1, 99) ?: 1
                            repeat(copies - 1) { block.forEach { rounds.add(it) } }
                            selectedRoundIndices = emptySet()
                        }
                    ) {
                        Text(stringResource(R.string.copy_rounds))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCopyNDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}
