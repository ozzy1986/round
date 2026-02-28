package com.raund.app.ui

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raund.app.LocaleManager
import com.raund.app.R
import com.raund.app.TimerService
import com.raund.app.data.repository.ProfileRepository
import com.raund.app.timer.TimerProfile
import com.raund.app.tts.TtsCache

@Composable
fun TimerScreen(
    repository: ProfileRepository,
    profileId: String,
    onBack: () -> Unit
) {
    var profile by remember { mutableStateOf<TimerProfile?>(null) }
    var currentRound by remember { mutableStateOf("") }
    var remaining by remember { mutableIntStateOf(0) }
    var roundTotal by remember { mutableIntStateOf(1) }
    var roundInfo by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var cacheReady by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val timerFinishedText = stringResource(R.string.timer_finished)
    val restartTimerText = stringResource(R.string.restart_timer)
    val pauseTimerText = stringResource(R.string.pause_timer)
    val resumeTimerText = stringResource(R.string.resume_timer)
    val timerPausedText = stringResource(R.string.timer_paused)

    LaunchedEffect(profileId) {
        profile = repository.getProfileWithRounds(profileId)
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
            cacheReady = true
            return@LaunchedEffect
        }
        Log.d("TimerScreen", "cache missing, calling ensureCache")
        TtsCache.ensureCache(context, locale, phrases)
        Log.d("TimerScreen", "ensureCache returned")
        cacheReady = true
    }

    LaunchedEffect(profile) {
        val p = profile ?: return@LaunchedEffect
        if (!running && !finished && p.rounds.isNotEmpty()) {
            remaining = p.rounds[0].durationSeconds
            roundTotal = p.rounds[0].durationSeconds
            currentRound = p.rounds[0].name
            roundInfo = "1 / ${p.rounds.size}"
        }
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
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (i == null) return
                remaining = i.getIntExtra(TimerService.EXTRA_REMAINING, remaining)
                currentRound = i.getStringExtra(TimerService.EXTRA_ROUND_NAME) ?: currentRound
                roundTotal = i.getIntExtra(TimerService.EXTRA_ROUND_TOTAL, roundTotal)
                val idx = i.getIntExtra(TimerService.EXTRA_ROUND_INDEX, 0)
                val total = i.getIntExtra(TimerService.EXTRA_TOTAL_ROUNDS, 1)
                roundInfo = "$idx / $total"
                if (!i.getBooleanExtra(TimerService.EXTRA_IS_RUNNING, true)) {
                    running = false
                    finished = true
                }
            }
        }
        val filter = IntentFilter(TimerService.ACTION_TIMER_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
            if (running) TimerService.stop(context)
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(animatedBgColor)
    ) {
        val maxW = maxWidth.value
        val maxH = maxHeight.value
        val timerFontSize = minOf(maxW / 3.5f, maxH / 8f).coerceIn(48f, 120f).sp
        val surfaceVarColor = MaterialTheme.colorScheme.surfaceVariant
        val primaryColor = MaterialTheme.colorScheme.primary
        val onBgColor = MaterialTheme.colorScheme.onBackground
        val onSurfaceVarColor = MaterialTheme.colorScheme.onSurfaceVariant
        val errorColor = MaterialTheme.colorScheme.error
        
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    running = false
                    TimerService.stop(context)
                    onBack()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    profile?.name?.ifBlank { stringResource(R.string.unnamed_profile) } ?: "",
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
                    Text(profile?.emoji?.ifBlank { "⏱" } ?: "⏱", fontSize = 24.sp)
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
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

                    val inWarningZone = running && !finished && remaining in 1..10
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

                    Box(contentAlignment = Alignment.Center) {
                        val progressRatio = if (roundTotal > 0) remaining.toFloat() / roundTotal.toFloat() else 1f
                        val animatedProgress by animateFloatAsState(
                            targetValue = progressRatio,
                            label = "progress"
                        )

                        val warningColor = MaterialTheme.colorScheme.tertiary
                        val ringColor = if (inWarningZone) warningColor else primaryColor

                        CircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .size(minOf(this@BoxWithConstraints.maxWidth, 320.dp))
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
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                if (!running && !finished) {
                    val hasRounds = profile?.rounds?.isNotEmpty() == true
                    if (!hasRounds && profile != null) {
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
                    Button(
                        onClick = {
                            val p = profile ?: return@Button
                            if (p.rounds.isEmpty()) return@Button
                            running = true
                            paused = false
                            finished = false
                            remaining = p.rounds[0].durationSeconds
                            roundTotal = p.rounds[0].durationSeconds
                            currentRound = p.rounds[0].name
                            roundInfo = "1 / ${p.rounds.size}"
                            (context as? Activity)?.let { activity ->
                                val pm = activity.getSystemService(Context.POWER_SERVICE) as? PowerManager
                                if (pm != null && !pm.isIgnoringBatteryOptimizations(activity.packageName)) {
                                    try {
                                        val intent = Intent().apply {
                                            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                            data = Uri.parse("package:${activity.packageName}")
                                        }
                                        activity.startActivity(intent)
                                    } catch (_: Exception) {}
                                }
                            }
                            TimerService.start(context, p, LocaleManager.currentLanguageTag(context), timerFinishedText)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        shape = RoundedCornerShape(32.dp),
                        enabled = hasRounds && cacheReady,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.start_timer), modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.start_timer),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                if (running && !paused) {
                    Button(
                        onClick = {
                            paused = true
                            TimerService.pause(context)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        shape = RoundedCornerShape(32.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(Icons.Filled.Pause, contentDescription = pauseTimerText, modifier = Modifier.size(32.dp))
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
                        onClick = {
                            paused = false
                            TimerService.resume(context)
                        },
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
                        onClick = {
                            running = false
                            finished = true
                            paused = false
                            TimerService.stop(context)
                        },
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
                        Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.stop_timer), modifier = Modifier.size(24.dp))
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
                        onClick = {
                            finished = false
                            paused = false
                            val p = profile
                            if (p != null && p.rounds.isNotEmpty()) {
                                remaining = p.rounds[0].durationSeconds
                                roundTotal = p.rounds[0].durationSeconds
                                currentRound = p.rounds[0].name
                                roundInfo = "1 / ${p.rounds.size}"
                            }
                        },
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
    }
}
