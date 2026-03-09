package com.raund.app.ui

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raund.app.LocaleManager
import com.raund.app.R
import com.raund.app.SettingsManager
import com.raund.app.data.entity.Profile
import com.raund.app.viewmodel.ProfileListViewModel

private val supportedLanguages = listOf(
    Triple("ru", "Русский", "🇷🇺"),
    Triple("uz", "Oʻzbek", "🇺🇿"),
    Triple("kk", "Қазақ", "🇰🇿"),
    Triple("az", "Azərbaycan", "🇦🇿"),
    Triple("tg", "Тоҷикӣ", "🇹🇯"),
    Triple("tt", "Татар", "🇷🇺"),
    Triple("en", "English", "🇬🇧"),
    Triple("zh", "中文", "🇨🇳"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileListScreen(
    viewModel: ProfileListViewModel,
    onProfileClick: (String) -> Unit,
    onAddProfile: () -> Unit,
    onStartTimer: (String) -> Unit
) {
    val listState by viewModel.listState.collectAsState()
    val profiles = listState.profiles
    val roundStats = listState.roundStats
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isSubmittingBugReport by viewModel.isSubmittingBugReport.collectAsState()
    val context = LocalContext.current
    var currentLang by remember { mutableStateOf(LocaleManager.currentLanguageTag(context)) }
    var showSettings by remember { mutableStateOf(false) }
    var keepRunning by remember { mutableStateOf(SettingsManager.isKeepRunningOnScreenOff(context)) }
    var keepRunningLeaveApp by remember { mutableStateOf(SettingsManager.isKeepRunningWhenLeavingApp(context)) }
    var showBugReportDialog by remember { mutableStateOf(false) }
    var bugReportMessage by remember { mutableStateOf("") }
    var bugReportError by remember { mutableStateOf<String?>(null) }
    val closeBugReportDialog = {
        showBugReportDialog = false
        bugReportMessage = ""
        bugReportError = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profiles), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    Box(modifier = Modifier.size(48.dp)) { /* fill slot so tap does not trigger system back */ }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                    }
                    Box {
                        var langMenuExpanded by remember { mutableStateOf(false) }
                        val currentFlag = supportedLanguages.find { it.first == currentLang }?.third ?: "🌐"
                        Surface(
                            onClick = { langMenuExpanded = true },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(currentFlag, fontSize = 18.sp)
                                Text(
                                    currentLang.uppercase(),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = langMenuExpanded,
                            onDismissRequest = { langMenuExpanded = false }
                        ) {
                            supportedLanguages.forEach { (code, name, flag) ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(flag, fontSize = 20.sp, modifier = Modifier.width(32.dp))
                                            Text(
                                                code.uppercase(),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.width(28.dp)
                                            )
                                            Text(name, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    },
                                    onClick = {
                                        langMenuExpanded = false
                                        if (code != currentLang) {
                                            currentLang = code
                                            LocaleManager.setLanguage(context, code)
                                            (context as? Activity)?.recreate()
                                        }
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddProfile,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_profile))
            }
        }
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
            if (profiles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            stringResource(R.string.no_profiles),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onAddProfile,
                            modifier = Modifier.height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(stringResource(R.string.create_first_profile), fontSize = 16.sp)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                    items(profiles, key = { it.id }) { profile: Profile ->
                        val stats = roundStats[profile.id]
                        val roundsCount = (stats?.roundsCount ?: 0L).toInt()
                        val totalDurationSeconds = (stats?.totalDurationSeconds ?: 0L).toInt()
                        val hasRounds = roundsCount > 0
                        val profileName = profile.name.ifBlank { stringResource(R.string.unnamed_profile) }
                        val profileSummary = if (hasRounds) {
                            stringResource(
                                R.string.profile_summary,
                                roundsCount,
                                formatDurationShort(totalDurationSeconds)
                            )
                        } else {
                            stringResource(R.string.no_rounds_short)
                        }

                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onProfileClick(profile.id) },
                            shape = RoundedCornerShape(percent = 50),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .aspectRatio(1f)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        profile.emoji.ifBlank { "⏱" },
                                        fontSize = 34.sp
                                    )
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 14.dp, vertical = 16.dp)
                                ) {
                                    Text(
                                        profileName,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        profileSummary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { onStartTimer(profile.id) },
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .aspectRatio(1f),
                                    enabled = hasRounds,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        disabledContainerColor = MaterialTheme.colorScheme.surface,
                                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        contentDescription = stringResource(R.string.start_timer),
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = {
                Text(
                    stringResource(R.string.settings),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.screen_off_continue),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                stringResource(R.string.screen_off_setting_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = keepRunning,
                            onCheckedChange = { checked ->
                                keepRunning = checked
                                SettingsManager.setKeepRunningOnScreenOff(context, checked)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.leave_app_continue),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                stringResource(R.string.leave_app_setting_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = keepRunningLeaveApp,
                            onCheckedChange = { checked ->
                                keepRunningLeaveApp = checked
                                SettingsManager.setKeepRunningWhenLeavingApp(context, checked)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            showSettings = false
                            bugReportError = null
                            showBugReportDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.report_bug))
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showBugReportDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isSubmittingBugReport) {
                    closeBugReportDialog()
                }
            },
            title = {
                Text(
                    stringResource(R.string.report_bug),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        stringResource(R.string.bug_report_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = bugReportMessage,
                        onValueChange = { value ->
                            bugReportMessage = value.take(BUG_REPORT_MAX_LENGTH)
                            if (bugReportError != null) {
                                bugReportError = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(stringResource(R.string.bug_report_placeholder))
                        },
                        minLines = 4,
                        maxLines = 8
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.bug_report_device_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    bugReportError?.let { error ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isSubmittingBugReport,
                    onClick = {
                        val trimmedMessage = bugReportMessage.trim()
                        if (trimmedMessage.length < BUG_REPORT_MIN_LENGTH) {
                            bugReportError = context.getString(R.string.bug_report_message_required)
                            return@TextButton
                        }

                        bugReportError = null
                        viewModel.submitBugReport(
                            message = trimmedMessage,
                            onSuccess = {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.bug_report_success),
                                    Toast.LENGTH_SHORT
                                ).show()
                                closeBugReportDialog()
                            },
                            onError = {
                                bugReportError = context.getString(R.string.bug_report_failed)
                            }
                        )
                    }
                ) {
                    if (isSubmittingBugReport) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Text(stringResource(R.string.sending_bug_report))
                        }
                    } else {
                        Text(stringResource(R.string.send_bug_report))
                    }
                }
            }
        )
    }
}

private fun formatDurationShort(totalSeconds: Int): String = com.raund.app.formatDuration(totalSeconds)

private const val BUG_REPORT_MIN_LENGTH = 10
private const val BUG_REPORT_MAX_LENGTH = 5000
