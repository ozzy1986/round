package com.raund.app.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.UtteranceProgressListener
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.media.ToneGenerator
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.WindowManager
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.raund.app.LocaleManager
import com.raund.app.R
import com.raund.app.RaundApplication
import com.raund.app.SettingsManager
import com.raund.app.TimerService
import com.raund.app.data.repository.ProfileRepository
import com.raund.app.timer.TimerEvent
import com.raund.app.timer.TimerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

private object TrainingEndPending {
    var finishedText: String? = null
    var defaultLocale: Locale? = null
}

private fun Char.isCyrillic(): Boolean = this in '\u0400'..'\u04FF'

private const val TONE_VOLUME = 0.55f

private val ttsVolumeParams: Bundle by lazy {
    Bundle().apply {
        putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f)
    }
}

private suspend fun playProlongedAlarmTone(durationMs: Int) = withContext(Dispatchers.IO) {
    val sampleRate = 44100
    val numSamples = sampleRate * durationMs / 1000
    val buffer = ShortArray(numSamples)
    val freq = 880.0
    for (i in 0 until numSamples) {
        buffer[i] = (sin(2.0 * PI * freq * i / sampleRate) * 32767 * TONE_VOLUME).toInt().toShort()
    }
    val minBufSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
    val bufSizeBytes = (numSamples * 2).coerceAtLeast(minBufSize)
    val track = AudioTrack.Builder()
        .setAudioAttributes(
            android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .setAudioFormat(
            android.media.AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setBufferSizeInBytes(bufSizeBytes)
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()
    try {
        track.setVolume(TONE_VOLUME)
        track.write(buffer, 0, buffer.size)
        track.play()
        delay(durationMs.toLong())
    } finally {
        track.release()
    }
}

@Composable
fun TimerScreen(
    repository: ProfileRepository,
    profileId: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val controller = (context.applicationContext as RaundApplication).timerController
    val state by controller.state.collectAsState()
    var profile by remember { mutableStateOf<TimerProfile?>(null) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var defaultTtsLocale by remember { mutableStateOf<Locale?>(null) }
    val timerFinishedText = stringResource(R.string.timer_finished)
    val restartTimerText = stringResource(R.string.restart_timer)
    val pauseTimerText = stringResource(R.string.pause_timer)
    val resumeTimerText = stringResource(R.string.resume_timer)
    val timerPausedText = stringResource(R.string.timer_paused)

    val displayCurrentRound = when {
        state.running -> state.currentRound
        state.finished -> ""
        else -> profile?.rounds?.firstOrNull()?.name ?: ""
    }
    val displayRemaining = when {
        state.running -> state.remaining
        else -> profile?.rounds?.firstOrNull()?.durationSeconds ?: 0
    }
    val displayRoundTotal = when {
        state.running -> state.roundTotal
        else -> profile?.rounds?.firstOrNull()?.durationSeconds ?: 1
    }
    val displayRoundInfo = when {
        state.running -> state.roundInfo
        else -> "1 / ${profile?.rounds?.size ?: 0}"
    }

    LaunchedEffect(profileId) {
        profile = repository.getProfileWithRounds(profileId)
    }

    DisposableEffect(context) {
        lateinit var ttsEngine: TextToSpeech
        ttsEngine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts = ttsEngine
                val appLang = LocaleManager.currentLanguageTag(context)
                val ttsLocale = when (appLang) {
                    "ru", "kk", "tg", "tt" -> Locale.forLanguageTag("ru")
                    "az", "uz" -> Locale.forLanguageTag("tr")
                    "zh" -> Locale.CHINESE
                    else -> Locale.forLanguageTag(appLang)
                }
                ttsEngine.setLanguage(ttsLocale)
                defaultTtsLocale = ttsLocale
                ttsEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId != null && utteranceId.startsWith("training_end_name_")) {
                            val text = TrainingEndPending.finishedText
                            val locale = TrainingEndPending.defaultLocale
                            TrainingEndPending.finishedText = null
                            TrainingEndPending.defaultLocale = null
                            if (text != null && locale != null) {
                                Handler(Looper.getMainLooper()).post {
                                    ttsEngine.setLanguage(locale)
                                    ttsEngine.speak(text, TextToSpeech.QUEUE_ADD, ttsVolumeParams, "training_end_done_${System.currentTimeMillis()}")
                                }
                            }
                        }
                    }
                    @Deprecated("Deprecated")
                    override fun onError(utteranceId: String?) {}
                    override fun onError(utteranceId: String?, errorCode: Int) {}
                })
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

    var alarmTone by remember { mutableStateOf<ToneGenerator?>(null) }
    val tickTone = ToneGenerator.TONE_CDMA_PIP
    DisposableEffect(context) {
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

    val tickMs = 200
    val prolongedToneMs = 1200

    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.volumeControlStream = AudioManager.STREAM_ALARM
        onDispose {
            activity?.volumeControlStream = AudioManager.USE_DEFAULT_STREAM_TYPE
        }
    }

    val screenOffPause = remember { SettingsManager.isScreenOffPause(context) }

    DisposableEffect(state.running) {
        val activity = context as? Activity
        var focusRequest: AudioFocusRequest? = null
        var requestedFocus = false
        if (state.running) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .build()
                am?.requestAudioFocus(req)
                focusRequest = req
                requestedFocus = true
            } else {
                @Suppress("DEPRECATION")
                am?.requestAudioFocus({}, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN)
                requestedFocus = true
            }
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (requestedFocus) {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                    am?.abandonAudioFocusRequest(focusRequest)
                } else {
                    @Suppress("DEPRECATION")
                    am?.abandonAudioFocus { }
                }
            }
        }
    }

    DisposableEffect(state.running, screenOffPause) {
        var wakeLock: PowerManager.WakeLock? = null
        var serviceStarted = false

        if (state.running && !screenOffPause) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "raund:timer")
            wakeLock?.acquire(60 * 60 * 1000L)
            try { TimerService.start(context); serviceStarted = true } catch (_: Exception) {}
        }

        onDispose {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            if (serviceStarted) {
                try { TimerService.stop(context) } catch (_: Exception) {}
            }
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var pendingToneJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            launch {
                controller.events.collectLatest { event ->
                val p = profile ?: return@collectLatest
                when (event) {
                    is TimerEvent.RoundStart -> {
                        pendingToneJob?.join()
                        val roundName = event.round.name
                        val isFirstRound = event.roundIndex == 0
                        pendingToneJob = scope.launch {
                            try {
                                if (isFirstRound) playProlongedAlarmTone(prolongedToneMs)
                                tts?.speak(roundName, TextToSpeech.QUEUE_FLUSH, ttsVolumeParams, null)
                            } catch (_: Exception) {}
                        }
                    }
                    is TimerEvent.Tick -> {
                        if (event.round.warn10sec && event.round.durationSeconds >= 10 && event.remainingSeconds in 1..10) {
                            try { alarmTone?.startTone(tickTone, tickMs) } catch (_: Exception) {}
                        }
                    }
                    is TimerEvent.RoundEnd -> {
                        pendingToneJob = scope.launch {
                            try { playProlongedAlarmTone(prolongedToneMs) } catch (_: Exception) {}
                        }
                    }
                    is TimerEvent.TrainingEnd -> {
                        val ttsRef = tts
                        val finishedText = timerFinishedText
                        val defaultLocale = defaultTtsLocale
                        scope.launch(Dispatchers.Main) {
                            try {
                                val nameHasCyrillic = p.name.any { it.isCyrillic() }
                                if (nameHasCyrillic && defaultLocale != null) {
                                    ttsRef?.setLanguage(Locale.forLanguageTag("ru"))
                                    TrainingEndPending.finishedText = finishedText
                                    TrainingEndPending.defaultLocale = defaultLocale
                                }
                                delay((prolongedToneMs - 400).coerceAtLeast(0).toLong())
                                val nameId = "training_end_name_${System.currentTimeMillis()}"
                                if (nameHasCyrillic && defaultLocale != null) {
                                    ttsRef?.speak(p.name, TextToSpeech.QUEUE_FLUSH, ttsVolumeParams, nameId)
                                } else {
                                    ttsRef?.speak(p.name, TextToSpeech.QUEUE_FLUSH, ttsVolumeParams, nameId)
                                    ttsRef?.speak(finishedText, TextToSpeech.QUEUE_ADD, ttsVolumeParams, "training_end_done_${System.currentTimeMillis()}")
                                }
                                delay(5000)
                            } catch (_: Exception) {}
                        }
                    }
                    else -> {}
                }
            }
        }
        }
    }

    val defaultBgColor = MaterialTheme.colorScheme.background
    val progressRatioForBg = if (displayRoundTotal > 0) displayRemaining.toFloat() / displayRoundTotal.toFloat() else 1f
    val targetBgColor = if (state.running && !state.finished) {
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
                    controller.stop()
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
                if (state.finished) {
                    Text(
                        timerFinishedText,
                        style = MaterialTheme.typography.displaySmall,
                        color = primaryColor,
                        textAlign = TextAlign.Center
                    )
                } else {
                    val displayRoundName = if (displayCurrentRound.length > 10) displayCurrentRound.take(10) + "…" else displayCurrentRound
                    Text(
                        displayRoundName,
                        style = MaterialTheme.typography.displaySmall,
                        color = primaryColor,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (displayRoundInfo.isNotEmpty()) {
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
                                    displayRoundInfo,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = onSurfaceVarColor,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            if (state.paused) {
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

                    val inWarningZone = state.running && !state.finished && displayRemaining in 1..10
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
                        val progressRatio = if (displayRoundTotal > 0) displayRemaining.toFloat() / displayRoundTotal.toFloat() else 1f
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
                            "%02d:%02d".format(displayRemaining / 60, displayRemaining % 60),
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
                if (!state.running && !state.finished) {
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
                            scope.launch {
                                var waited = 0
                                while (tts == null && waited < 1500) {
                                    delay(100)
                                    waited += 100
                                }
                                controller.start(p, screenOffPause)
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
                
                if (state.running && !state.paused) {
                    Button(
                        onClick = {
                            controller.setPaused(true)
                            tts?.stop()
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
                if (state.running && state.paused) {
                    Button(
                        onClick = { controller.setPaused(false) },
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
                            controller.stop()
                            tts?.stop()
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

                if (state.finished) {
                    Button(
                        onClick = {
                            controller.resetForRestart()
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
