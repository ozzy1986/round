package com.raund.app.ui

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
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
import com.raund.app.LocaleManager
import com.raund.app.R
import com.raund.app.data.repository.ProfileRepository
import com.raund.app.timer.TimerEngine
import com.raund.app.timer.TimerEvent
import com.raund.app.timer.TimerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    var profile by remember { mutableStateOf<TimerProfile?>(null) }
    var currentRound by remember { mutableStateOf("") }
    var remaining by remember { mutableIntStateOf(0) }
    var roundTotal by remember { mutableIntStateOf(1) }
    var roundInfo by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var defaultTtsLocale by remember { mutableStateOf<Locale?>(null) }
    val timerFinishedText = stringResource(R.string.timer_finished)
    val restartTimerText = stringResource(R.string.restart_timer)
    val pauseTimerText = stringResource(R.string.pause_timer)
    val resumeTimerText = stringResource(R.string.resume_timer)
    val timerPausedText = stringResource(R.string.timer_paused)

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
            ToneGenerator(AudioManager.STREAM_ALARM, 55)
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
    val prolongedToneMs = 1200

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
                            paused = false
                            scope.launch {
                                var waited = 0
                                while (tts == null && waited < 1500 && running) {
                                    delay(100)
                                    waited += 100
                                }
                                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                                val focusRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                                        .setAudioAttributes(
                                            AudioAttributes.Builder()
                                                .setUsage(AudioAttributes.USAGE_ALARM)
                                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                                .build()
                                        )
                                        .build()
                                } else null
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                                    am?.requestAudioFocus(focusRequest)
                                } else {
                                    @Suppress("DEPRECATION")
                                    am?.requestAudioFocus({}, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN)
                                }
                                var pendingToneJob: Job? = null
                                var finalTtsJob: Job? = null
                                try {
                                val engine = TimerEngine(p) { event ->
                                    when (event) {
                                        is TimerEvent.RoundStart -> {
                                            currentRound = event.round.name
                                            remaining = event.round.durationSeconds
                                            roundTotal = event.round.durationSeconds
                                            roundInfo = "${event.roundIndex + 1} / ${event.totalRounds}"
                                            val roundName = event.round.name
                                            val isFirstRound = event.roundIndex == 0
                                            scope.launch {
                                                pendingToneJob?.join()
                                                if (isFirstRound) {
                                                    playProlongedAlarmTone(prolongedToneMs)
                                                }
                                                tts?.speak(roundName, TextToSpeech.QUEUE_FLUSH, ttsVolumeParams, null)
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
                                            pendingToneJob = scope.launch { playProlongedAlarmTone(prolongedToneMs) }
                                        }
                                        is TimerEvent.TrainingEnd -> {
                                            val ttsRef = tts
                                            val finishedText = timerFinishedText
                                            val defaultLocale = defaultTtsLocale
                                            running = false
                                            finished = true
                                            paused = false
                                            finalTtsJob = scope.launch(Dispatchers.Main) {
                                                pendingToneJob?.join()
                                                val nameHasCyrillic = p.name.any { it.isCyrillic() }
                                                if (nameHasCyrillic && defaultLocale != null) {
                                                    ttsRef?.setLanguage(Locale.forLanguageTag("ru"))
                                                    TrainingEndPending.finishedText = finishedText
                                                    TrainingEndPending.defaultLocale = defaultLocale
                                                }
                                                ttsRef?.speak(" ", TextToSpeech.QUEUE_FLUSH, ttsVolumeParams, "warmup_${System.currentTimeMillis()}")
                                                delay(250)
                                                val nameId = "training_end_name_${System.currentTimeMillis()}"
                                                if (nameHasCyrillic && defaultLocale != null) {
                                                    ttsRef?.speak(p.name, TextToSpeech.QUEUE_ADD, ttsVolumeParams, nameId)
                                                } else {
                                                    ttsRef?.speak(p.name, TextToSpeech.QUEUE_ADD, ttsVolumeParams, nameId)
                                                    ttsRef?.speak(finishedText, TextToSpeech.QUEUE_ADD, ttsVolumeParams, "training_end_done_${System.currentTimeMillis()}")
                                                }
                                                delay(5000)
                                            }
                                        }
                                    }
                                }
                                engine.advance()
                                while (scope.isActive && running) {
                                    if (paused) {
                                        delay(500L)
                                    } else {
                                        delay(1000L)
                                        if (!engine.advance()) break
                                    }
                                }
                                } finally {
                                    finalTtsJob?.join()
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                                        am?.abandonAudioFocusRequest(focusRequest)
                                    } else {
                                        @Suppress("DEPRECATION")
                                        am?.abandonAudioFocus {}
                                    }
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
                
                if (running && !paused) {
                    Button(
                        onClick = {
                            paused = true
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
                if (running && paused) {
                    Button(
                        onClick = { paused = false },
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
