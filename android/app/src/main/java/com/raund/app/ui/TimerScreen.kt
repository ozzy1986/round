package com.raund.app.ui

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    var remaining by remember { mutableStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    LaunchedEffect(profileId) {
        profile = repository.getProfileWithRounds(profileId)
    }

    DisposableEffect(context) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts = engine
                engine.setLanguage(Locale.getDefault())
            }
        }
        onDispose { engine.shutdown() }
    }

    Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Column(modifier = Modifier.align(Alignment.Center).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(profile?.emoji ?: "⏱", fontSize = 48.sp)
            Text(profile?.name ?: "", style = MaterialTheme.typography.titleLarge)
            Text(currentRound, style = MaterialTheme.typography.titleMedium)
            Text(
                "%02d:%02d".format(remaining / 60, remaining % 60),
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold
            )
            if (finished) {
                Text(stringResource(R.string.timer_finished))
            }
            Button(
                onClick = {
                    if (running || finished) return@Button
                    val p = profile ?: return@Button
                    running = true
                    scope.launch {
                        val engine = TimerEngine(p) { event ->
                            when (event) {
                                is TimerEvent.RoundStart -> {
                                    currentRound = event.round.name
                                    remaining = event.round.durationSeconds
                                    tts?.speak(event.round.name, TextToSpeech.QUEUE_FLUSH, null, null)
                                }
                                is TimerEvent.Tick -> remaining = event.remainingSeconds
                                is TimerEvent.Warn10 -> tts?.speak("${event.round.name} in 10 seconds", TextToSpeech.QUEUE_ADD, null, null)
                                is TimerEvent.RoundEnd -> {}
                                is TimerEvent.TrainingEnd -> {
                                    running = false
                                    finished = true
                                    tts?.speak(stringResource(R.string.timer_finished), TextToSpeech.QUEUE_ADD, null, null)
                                }
                            }
                        }
                        engine.advance()
                        while (scope.isActive && running) {
                            delay(1000L)
                            if (!engine.advance()) break
                        }
                    }
                }
            ) { Text(if (running) "…" else stringResource(R.string.start_timer)) }
            Button(onClick = onBack) { Text("Back") }
        }
    }
}
