package com.raund.app.ui

import android.app.Application
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raund.app.LocaleManager
import com.raund.app.R
import com.raund.app.TimerService
import com.raund.app.TimerStateHolder
import com.raund.app.timer.TimerProfile
import com.raund.app.tts.TtsCache
import com.raund.app.viewmodel.TimerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    viewModel: TimerViewModel,
    onBack: () -> Unit
) {
    val profileId = viewModel.profileId
    val profile by viewModel.profile.collectAsState()
    val cacheReady by viewModel.cacheReady.collectAsState()
    val finished by viewModel.finished.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val timerState by viewModel.timerState.collectAsState()
    val running = timerState.isRunning
    val remaining = if (running) timerState.remaining else (profile?.rounds?.firstOrNull()?.durationSeconds ?: 0)
    val roundTotal = if (running) timerState.roundTotal else (profile?.rounds?.firstOrNull()?.durationSeconds ?: 1)
    val currentRound = if (running) timerState.roundName else (profile?.rounds?.firstOrNull()?.name ?: "")
    val roundInfo = if (running) "${timerState.roundIndex} / ${timerState.totalRounds}" else (if ((profile?.rounds?.size ?: 0) > 0) "1 / ${profile!!.rounds.size}" else "")
    val paused = timerState.paused
    val context = LocalContext.current
    val timerFinishedText = stringResource(R.string.timer_finished)
    val restartTimerText = stringResource(R.string.restart_timer)
    val pauseTimerText = stringResource(R.string.pause_timer)
    val resumeTimerText = stringResource(R.string.resume_timer)
    val timerPausedText = stringResource(R.string.timer_paused)

    LaunchedEffect(profileId) {
        TimerService.warmup(context)
    }

    LaunchedEffect(profile) {
        val p = profile ?: return@LaunchedEffect
        if (p.rounds.isEmpty()) return@LaunchedEffect
        val locale = LocaleManager.currentLanguageTag(context)
        val finishedText = context.getString(R.string.timer_finished)
        val phrases = TtsCache.buildPhraseList(p, finishedText)
        Log.d("TimerScreen", "cache check profile=${p.name} locale=$locale phrases=${phrases.size}")
        val allExist = TtsCache.allExist(context, locale, phrases)
        if (allExist) {
            Log.d("TimerScreen", "cache full, skip ensureCache")
            viewModel.setCacheReady(true)
            return@LaunchedEffect
        }
        Log.d("TimerScreen", "cache missing, calling ensureCache")
        TtsCache.ensureCache(context, locale, phrases)
        Log.d("TimerScreen", "ensureCache returned")
        viewModel.setCacheReady(true)
    }

    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.volumeControlStream = AudioManager.STREAM_ALARM
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activity != null) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
        onDispose {
            activity?.volumeControlStream = AudioManager.USE_DEFAULT_STREAM_TYPE
        }
    }

    DisposableEffect(running) {
        val activity = context as? Activity
        if (running) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(Unit) {
        val activity = context as? Activity
        val lifecycleOwner = activity as? androidx.lifecycle.LifecycleOwner
        val lifecycleObserver = object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onResume(owner: androidx.lifecycle.LifecycleOwner) {
                android.util.Log.i("TimerScreen", "onResume -> sending TIMER_VISIBLE")
                context.sendBroadcast(Intent(TimerService.ACTION_TIMER_VISIBLE).setPackage(context.packageName))
            }
            override fun onPause(owner: androidx.lifecycle.LifecycleOwner) {
                android.util.Log.i("TimerScreen", "onPause -> sending TIMER_HIDDEN")
                context.sendBroadcast(Intent(TimerService.ACTION_TIMER_HIDDEN).setPackage(context.packageName))
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(lifecycleObserver)
        context.sendBroadcast(Intent(TimerService.ACTION_TIMER_VISIBLE).setPackage(context.packageName))
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
            context.sendBroadcast(Intent(TimerService.ACTION_TIMER_HIDDEN).setPackage(context.packageName))
        }
    }

    val defaultBgColor = MaterialTheme.colorScheme.background
    val progressRatioForBg = if (roundTotal > 0) remaining.toFloat() / roundTotal.toFloat() else 1f
    val targetBgColor = if (running && !finished) {
        lerp(Color(0xFF8B0000), Color(0xFF006400), progressRatioForBg)
    } else {
        defaultBgColor
    }
    val animatedBgColor by animateColorAsState(
        targetValue = targetBgColor,
        animationSpec = tween(1000, easing = LinearEasing),
        label = "bgColor"
    )

    val canRefresh = !running && !finished
    val pullToRefreshState = rememberPullToRefreshState()
    if (pullToRefreshState.isRefreshing && canRefresh) {
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
            .background(animatedBgColor)
            .then(if (canRefresh) Modifier.nestedScroll(pullToRefreshState.nestedScrollConnection) else Modifier)
    ) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val maxW = maxWidth.value
        val maxH = maxHeight.value
        val timerFontSize = minOf(maxW / 3.5f, maxH / 8f).coerceIn(48f, 120f).sp
        val surfaceVarColor = MaterialTheme.colorScheme.surfaceVariant
        val primaryColor = MaterialTheme.colorScheme.primary
        val onBgColor = MaterialTheme.colorScheme.onBackground
        val onSurfaceVarColor = MaterialTheme.colorScheme.onSurfaceVariant

        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (canRefresh) Modifier.verticalScroll(scrollState) else Modifier)
        ) {
            TimerTopBar(
                profileName = profile?.name?.ifBlank { stringResource(R.string.unnamed_profile) } ?: "",
                profileEmoji = profile?.emoji?.ifBlank { "⏱" } ?: "⏱",
                onBack = { TimerService.stop(context); onBack() }
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (!canRefresh) Modifier.weight(1f) else Modifier.height((maxH * 0.7f).dp))
                    .padding(horizontal = 24.dp)
            ) {
                if (finished) {
                    Text(
                        timerFinishedText,
                        style = MaterialTheme.typography.displaySmall,
                        color = primaryColor,
                        textAlign = TextAlign.Center
                    )
                } else {
                    val displayRoundName = if (currentRound.length > 10) currentRound.take(10) + "…" else currentRound
                    Text(
                        displayRoundName,
                        style = MaterialTheme.typography.displaySmall,
                        color = primaryColor,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (roundInfo.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = surfaceVarColor
                            ) {
                                Text(
                                    roundInfo,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = onSurfaceVarColor,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            if (paused) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = surfaceVarColor
                                ) {
                                    Text(
                                        timerPausedText,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = onSurfaceVarColor,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(48.dp))
                    TimerCountdownRing(
                        remaining = remaining,
                        roundTotal = roundTotal,
                        isRunning = running,
                        isFinished = finished,
                        maxRingSize = minOf(this@BoxWithConstraints.maxWidth, 320.dp),
                        timerFontSize = timerFontSize,
                        onBgColor = onBgColor
                    )
                }
            }

            TimerControls(
                hasRounds = profile?.rounds?.isNotEmpty() == true,
                profile = profile,
                cacheReady = cacheReady,
                syncing = isRefreshing || profile == null,
                running = running,
                paused = paused,
                finished = finished,
                pauseTimerText = pauseTimerText,
                resumeTimerText = resumeTimerText,
                restartTimerText = restartTimerText,
                onStart = { viewModel.setFinished(false); TimerService.start(context, profileId, profile!!, LocaleManager.currentLanguageTag(context), timerFinishedText) },
                onPause = { TimerService.pause(context) },
                onResume = { TimerService.resume(context) },
                onStop = { viewModel.setFinished(true); TimerService.stop(context) },
                onRestart = { viewModel.setFinished(false) }
            )
        }
    }
    if (canRefresh) {
        PullToRefreshContainer(
            state = pullToRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
    }
}

@Composable
private fun TimerTopBar(
    profileName: String,
    profileEmoji: String,
    onBack: () -> Unit
) {
    val surfaceVarColor = MaterialTheme.colorScheme.surfaceVariant
    val onBgColor = MaterialTheme.colorScheme.onBackground
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            profileName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = onBgColor
        )
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(surfaceVarColor),
            contentAlignment = Alignment.Center
        ) {
            Text(profileEmoji, fontSize = 24.sp)
        }
    }
}

@Composable
private fun TimerCountdownRing(
    remaining: Int,
    roundTotal: Int,
    isRunning: Boolean,
    isFinished: Boolean,
    maxRingSize: Dp,
    timerFontSize: androidx.compose.ui.unit.TextUnit,
    onBgColor: Color
) {
    val surfaceVarColor = MaterialTheme.colorScheme.surfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val inWarningZone = isRunning && !isFinished && remaining in 1..10
    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val ringScale = if (inWarningZone) pulseScale else 1f
    val progressRatio = if (roundTotal > 0) remaining.toFloat() / roundTotal.toFloat() else 1f
    val animatedProgress by animateFloatAsState(
        targetValue = progressRatio,
        label = "progress"
    )
    val warningColor = MaterialTheme.colorScheme.tertiary
    val ringColor = if (inWarningZone) warningColor else primaryColor
    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .size(maxRingSize)
                .graphicsLayer {
                    scaleX = ringScale
                    scaleY = ringScale
                },
            strokeWidth = 36.dp,
            trackColor = surfaceVarColor,
            color = ringColor,
            strokeCap = StrokeCap.Round
        )
        Text(
            "%02d:%02d".format(remaining / 60, remaining % 60),
            fontSize = timerFontSize,
            fontWeight = FontWeight.Black,
            color = onBgColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
    }
}

@Composable
private fun TimerControls(
    hasRounds: Boolean,
    profile: TimerProfile?,
    cacheReady: Boolean,
    syncing: Boolean,
    running: Boolean,
    paused: Boolean,
    finished: Boolean,
    pauseTimerText: String,
    resumeTimerText: String,
    restartTimerText: String,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit
) {
    val errorColor = MaterialTheme.colorScheme.error
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        if (!running && !finished) {
            if (!hasRounds && profile != null && !syncing) {
                Text(
                    stringResource(R.string.no_rounds),
                    style = MaterialTheme.typography.bodyMedium,
                    color = errorColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )
            }
            val startEnabled = hasRounds && cacheReady && !syncing
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(32.dp),
                enabled = startEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (syncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.loading),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.start_timer), modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.start_timer),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        if (running && !paused) {
            Button(
                onClick = onPause,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(AppIcons.Pause, contentDescription = pauseTimerText, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    pauseTimerText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (running && paused) {
            Button(
                onClick = onResume,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = resumeTimerText, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    resumeTimerText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, MaterialTheme.colorScheme.error
                )
            ) {
                Icon(AppIcons.Stop, contentDescription = stringResource(R.string.stop_timer), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.stop_timer),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (finished) {
            Button(
                onClick = onRestart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(32.dp)
            ) {
                Text(
                    restartTimerText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
