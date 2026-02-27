package com.raund.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raund.app.R
import com.raund.app.data.repository.ProfileRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (isNew) R.string.new_profile else R.string.edit_profile))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("<", fontSize = 20.sp) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.profile_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = emoji,
                onValueChange = { emoji = it },
                label = { Text(stringResource(R.string.profile_emoji)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.rounds),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            rounds.forEachIndexed { index, (rName, dur, warn) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = rName,
                                onValueChange = { rounds[index] = Triple(it, dur, warn) },
                                label = { Text(stringResource(R.string.round_name)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f).padding(end = 4.dp)
                            )
                            IconButton(
                                onClick = { rounds.removeAt(index) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text("✕", color = MaterialTheme.colorScheme.error, fontSize = 18.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = dur.toString(),
                                onValueChange = { rounds[index] = Triple(rName, it.toIntOrNull() ?: 0, warn) },
                                label = { Text(stringResource(R.string.duration_seconds)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = warn,
                                    onCheckedChange = { rounds[index] = Triple(rName, dur, it) }
                                )
                                Text(
                                    stringResource(R.string.warn_10_sec),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = { rounds.add(Triple("", 60, false)) }) {
                Text(stringResource(R.string.add_round))
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    scope.launch {
                        val id = if (isNew) repository.insertProfile(name, emoji) else profileId!!
                        if (!isNew) repository.updateProfile(profileId!!, name, emoji)
                        repository.saveRounds(id, rounds.filter { it.first.isNotBlank() })
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.save)) }
            if (!isNew) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            repository.deleteProfile(profileId!!)
                            onBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.delete_profile)) }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
