package com.raund.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.raund.app.R
import com.raund.app.data.entity.Profile
import com.raund.app.data.repository.ProfileRepository

@Composable
fun ProfileListScreen(
    repository: ProfileRepository,
    onProfileClick: (String) -> Unit,
    onAddProfile: () -> Unit,
    onStartTimer: (String) -> Unit
) {
    val profiles by repository.profiles.collectAsState(initial = emptyList())

    Scaffold(
        topBar = { Text(stringResource(R.string.profiles), modifier = Modifier.padding(16.dp), style = androidx.compose.material3.MaterialTheme.typography.headlineMedium) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddProfile) {
                Text("+")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(profiles) { profile: Profile ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onProfileClick(profile.id) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ListItem(
                        headlineContent = { Text(profile.name) },
                        leadingContent = { Text(profile.emoji, style = androidx.compose.material3.MaterialTheme.typography.titleLarge) }
                    )
                    Button(onClick = { onStartTimer(profile.id) }) {
                        Text(stringResource(R.string.start_timer))
                    }
                }
            }
        }
    }
}
