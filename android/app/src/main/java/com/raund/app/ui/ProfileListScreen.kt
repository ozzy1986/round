package com.raund.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raund.app.R
import com.raund.app.data.entity.Profile
import com.raund.app.data.repository.ProfileRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileListScreen(
    repository: ProfileRepository,
    onProfileClick: (String) -> Unit,
    onAddProfile: () -> Unit,
    onStartTimer: (String) -> Unit
) {
    val profiles by repository.profiles.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.profiles)) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddProfile) {
                Text("+", fontSize = 24.sp)
            }
        }
    ) { padding ->
        if (profiles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.no_profiles),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(profiles) { profile: Profile ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onProfileClick(profile.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    profile.emoji.ifBlank { "⏱" },
                                    fontSize = 32.sp,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    profile.name,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            TextButton(onClick = { onStartTimer(profile.id) }) {
                                Text(
                                    stringResource(R.string.start_timer),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
