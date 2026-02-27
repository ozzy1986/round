package com.raund.app.ui

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raund.app.R
import com.raund.app.data.repository.ProfileRepository
import com.raund.app.timer.TimerEngine
import com.raund.app.timer.TimerEvent
import com.raund.app.timer.TimerProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun TimerScreen(
    repository: ProfileRepository,
    profileId: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<TimerProfile?>(null) }
    var currentRound by remember { mutableStateOf("") }
    var remaining by remember { mutableIntStateOf(0) }
    var roundTotal by remember { mutableIntStateOf(1) }
    var roundInfo by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var stoppedByUser by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    val timerFinishedText = stringResource(R.string.timer_finished)
    val timerStoppedText = stringResource(R.string.timer_stopped)
    val restartTimerText = stringResource(R.string.restart_timer)

    LaunchedEffect(profileId) {
        profile = repository.getProfileWithRounds(profileId)
    }

    DisposableEffect(context) {
        lateinit var ttsEngine: TextToSpeech
        ttsEngine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts = ttsEngine
                val locale = if (Locale.getDefault().language == "ru") Locale.forLanguageTag("ru") else Locale.getDefault()
                ttsEngine.setLanguage(locale)
                val audioAttributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                ttsEngine.setAudioAttributes(audioAttributes)
            }
        }
        onDispose {
            tts = null
            ttsEngine.stop()
            ttsEngine.shutdown()
        }
    }

    // Piercing high-pitched alarm: short ticks, long round-end/training-end beeps — audible across the hall
    var alarmTone by remember { mutableStateOf<ToneGenerator?>(null) }
    val piercingTone = ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
    val tickTone = ToneGenerator.TONE_CDMA_PIP
    DisposableEffect(context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (am != null) {
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            am.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
        }
        val tg = try {
            ToneGenerator(AudioManager.STREAM_ALARM, 100)
        } catch (e: Exception) {
            null
        }
        alarmTone = tg
        onDispose {
            tg?.release()
            alarmTone = null
        }
    }

    val tickMs = 150
    val longBeepMs = 450
    val longBeepGapMs = 400L

    LaunchedEffect(profile) {
        val p = profile ?: return@LaunchedEffect
        if (!running && !finished && p.rounds.isNotEmpty()) {
            remaining = p.rounds[0].durationSeconds
            roundTotal = p.rounds[0].durationSeconds
            currentRound = p.rounds[0].name
            roundInfo = "1 / ${p.rounds.size}"
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
                    tts?.stop()
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
                        if (stoppedByUser) timerStoppedText else timerFinishedText,
                        style = MaterialTheme.typography.displaySmall,
                        color = primaryColor,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        currentRound,
                        style = MaterialTheme.typography.displaySmall,
                        color = primaryColor,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (roundInfo.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
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
                    }
                    
                    Spacer(modifier = Modifier.height(48.dp))

                    Box(contentAlignment = Alignment.Center) {
                        val progressRatio = if (roundTotal > 0) remaining.toFloat() / roundTotal.toFloat() else 1f
                        val animatedProgress by animateFloatAsState(
                            targetValue = progressRatio,
                            label = "progress"
                        )
                        
                        CircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.size(minOf(this@BoxWithConstraints.maxWidth, 320.dp)),
                            strokeWidth = 36.dp,
                            trackColor = surfaceVarColor,
                            color = primaryColor,
                            strokeCap = StrokeCap.Round
                        )
                        
                        Text(
                            "%02d:%02d".format(remaining / 60, remaining % 60),
                            fontSize = timerFontSize,
                            fontWeight = FontWeight.Bold,
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
                            stoppedByUser = false
                            scope.launch {
                                var waited = 0
                                while (tts == null && waited < 1500 && running) {
                                    delay(100)
                                    waited += 100
                                }
                                val engine = TimerEngine(p) { event ->
                                    when (event) {
                                        is TimerEvent.RoundStart -> {
                                            currentRound = event.round.name
                                            remaining = event.round.durationSeconds
                                            roundTotal = event.round.durationSeconds
                                            roundInfo = "${event.roundIndex + 1} / ${event.totalRounds}"
                                            val roundName = event.round.name
                                            scope.launch {
                                                val tone = alarmTone
                                                if (tone != null) {
                                                    tone.startTone(piercingTone, longBeepMs)
                                                    delay(longBeepGapMs)
                                                    tone.startTone(piercingTone, longBeepMs)
                                                    delay(longBeepGapMs)
                                                }
                                                tts?.speak(roundName, TextToSpeech.QUEUE_FLUSH, null, null)
                                            }
                                        }
                                        is TimerEvent.Tick -> {
                                            remaining = event.remainingSeconds
                                            if (event.round.warn10sec && event.round.durationSeconds >= 10 && event.remainingSeconds in 1..10) {
                                                alarmTone?.startTone(tickTone, tickMs)
                                            }
                                        }
                                        is TimerEvent.Warn10 -> {
                                            // Voice warning removed per user request
                                        }
                                        is TimerEvent.RoundEnd -> {
                                            scope.launch {
                                                alarmTone?.startTone(piercingTone, 1000)
                                            }
                                        }
                                        is TimerEvent.TrainingEnd -> {
                                            running = false
                                            finished = true
                                            stoppedByUser = false
                                            tts?.speak(timerFinishedText, TextToSpeech.QUEUE_FLUSH, null, null)
                                            scope.launch {
                                                val tone = alarmTone
                                                if (tone != null) {
                                                    for (i in 1..3) {
                                                        tone.startTone(piercingTone, longBeepMs)
                                                        delay(longBeepGapMs)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                engine.advance()
                                while (scope.isActive && running) {
                                    delay(1000L)
                                    if (!engine.advance()) break
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        shape = RoundedCornerShape(32.dp),
                        enabled = hasRounds,
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
                
                if (running) {
                    Button(
                        onClick = {
                            running = false
                            finished = true
                            stoppedByUser = true
                            tts?.stop()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        shape = RoundedCornerShape(32.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.stop_timer), modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.stop_timer),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (finished) {
                    Button(
                        onClick = {
                            finished = false
                            stoppedByUser = false
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
