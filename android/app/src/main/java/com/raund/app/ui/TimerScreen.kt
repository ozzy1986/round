package com.raund.app.ui

import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
    var roundInfo by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    val timerFinishedText = stringResource(R.string.timer_finished)
    val warn10Template = stringResource(R.string.timer_warn10)

    LaunchedEffect(profileId) {
        profile = repository.getProfileWithRounds(profileId)
    }

    DisposableEffect(context) {
        var ttsEngine: TextToSpeech? = null
        ttsEngine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts = ttsEngine
                ttsEngine?.setLanguage(Locale.getDefault())
            }
        }
        onDispose { ttsEngine?.shutdown() }
    }

    var tickTone by remember { mutableStateOf<ToneGenerator?>(null) }
    DisposableEffect(context) {
        val tg = try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        } catch (e: Exception) {
            null
        }
        tickTone = tg
        onDispose {
            tg?.release()
            tickTone = null
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        val maxHeight = maxHeight
        val timerFontSize = (maxHeight.value / 4.2f).coerceIn(80f, 200f).sp
        val emojiFontSize = (maxHeight.value / 6f).coerceIn(48f, 120f).sp

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    profile?.emoji ?: "⏱",
                    fontSize = emojiFontSize,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Text(
                    profile?.name ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    currentRound,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                if (roundInfo.isNotEmpty()) {
                    Text(
                        roundInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "%02d:%02d".format(remaining / 60, remaining % 60),
                    fontSize = timerFontSize,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (finished) {
                Text(
                    timerFinishedText,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            if (!running && !finished) {
                val hasRounds = profile?.rounds?.isNotEmpty() == true
                if (!hasRounds && profile != null) {
                    Text(
                        stringResource(R.string.no_rounds),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Button(
                    onClick = {
                        val p = profile ?: return@Button
                        if (p.rounds.isEmpty()) return@Button
                        running = true
                        scope.launch {
                            val engine = TimerEngine(p) { event ->
                                when (event) {
                                    is TimerEvent.RoundStart -> {
                                        currentRound = event.round.name
                                        remaining = event.round.durationSeconds
                                        roundInfo = "${event.roundIndex + 1} / ${event.totalRounds}"
                                        tts?.speak(event.round.name, TextToSpeech.QUEUE_FLUSH, null, null)
                                    }
                                    is TimerEvent.Tick -> {
                                        remaining = event.remainingSeconds
                                        if (event.round.warn10sec && event.remainingSeconds in 1..10) {
                                            tickTone?.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
                                        }
                                    }
                                    is TimerEvent.Warn10 -> {
                                        val msg = warn10Template.format(event.round.name)
                                        tts?.speak(msg, TextToSpeech.QUEUE_ADD, null, null)
                                    }
                                    is TimerEvent.RoundEnd -> {}
                                    is TimerEvent.TrainingEnd -> {
                                        running = false
                                        finished = true
                                        tts?.speak(timerFinishedText, TextToSpeech.QUEUE_ADD, null, null)
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
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = hasRounds
                ) {
                    Text(
                        stringResource(R.string.start_timer),
                        fontSize = 20.sp
                    )
                }
            }
            if (running) {
                Button(
                    onClick = {
                        running = false
                        finished = true
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        stringResource(R.string.stop_timer),
                        fontSize = 20.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = {
                running = false
                onBack()
            }) {
                Text(stringResource(R.string.back))
            }
        }
    }
}
