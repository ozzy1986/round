package com.raund.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.raund.app.R
import com.raund.app.data.repository.ProfileRepository
import kotlinx.coroutines.launch

@Composable
fun ProfileEditorScreen(
    repository: ProfileRepository,
    profileId: String?,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("⏱") }
    val rounds = remember { mutableStateListOf<Triple<String, Int, Boolean>>() }
    val isNew = profileId == null || profileId == "new"

    LaunchedEffect(profileId) {
        if (!isNew && profileId != null) {
            val profile = repository.getProfileWithRounds(profileId)
            name = profile?.name ?: ""
            emoji = profile?.emoji ?: "⏱"
            rounds.clear()
            profile?.rounds?.forEach { r ->
                rounds.add(Triple(r.name, r.durationSeconds, r.warn10sec))
            }
        }
    }

    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.profile_name)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = emoji, onValueChange = { emoji = it }, label = { Text(stringResource(R.string.profile_emoji)) }, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.rounds), modifier = Modifier.padding(vertical = 8.dp))
        rounds.forEachIndexed { index, (rName, dur, warn) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = rName, onValueChange = { rounds[index] = Triple(it, dur, warn) }, modifier = Modifier.weight(1f).padding(4.dp))
                OutlinedTextField(value = dur.toString(), onValueChange = { rounds[index] = Triple(rName, it.toIntOrNull() ?: 0, warn) }, modifier = Modifier.weight(0.5f).padding(4.dp))
                Checkbox(checked = warn, onCheckedChange = { rounds[index] = Triple(rName, dur, it) })
            }
        }
        Button(onClick = { rounds.add(Triple("", 60, false)) }) { Text("+ Round") }
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBack) { Text("Back") }
            Button(onClick = {
                scope.launch {
                    val id = if (isNew) repository.insertProfile(name, emoji) else profileId!!
                    if (!isNew) repository.updateProfile(profileId!!, name, emoji)
                    repository.saveRounds(id, rounds.filter { it.first.isNotBlank() })
                    onBack()
                }
            }) { Text(stringResource(R.string.save)) }
        }
    }
}
