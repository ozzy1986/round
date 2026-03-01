package com.raund.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.content.ContextCompat
import com.raund.app.timer.TimerEngine
import com.raund.app.timer.TimerEvent
import com.raund.app.timer.TimerProfile
import com.raund.app.tts.TtsCache
import kotlin.math.PI
import kotlin.math.sin
import java.io.File
import java.util.Locale

class TimerService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var silentTrack: AudioTrack? = null
    private var timerThread: Thread? = null

    @Volatile
    private var paused = false

    @Volatile
    private var ttsRef: TextToSpeech? = null

    @Volatile
    private var ttsReady = false

    @Volatile
    private var running = false

    @Volatile
    private var timerScreenVisible = false

    @Volatile
    private var currentProfileId: String? = null

    private val visibilityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_TIMER_VISIBLE -> timerScreenVisible = true
                ACTION_TIMER_HIDDEN -> timerScreenVisible = false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "raund:timer").apply {
            setReferenceCounted(false)
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.app_name),
            NotificationManager.IMPORTANCE_HIGH
        )
        channel.description = getString(R.string.timer_running)
        channel.setSound(null, null)
        channel.setShowBadge(true)
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
        val filter = IntentFilter().apply {
            addAction(ACTION_TIMER_VISIBLE)
            addAction(ACTION_TIMER_HIDDEN)
        }
        registerReceiver(visibilityReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun stopPreviousTimer() {
        running = false
        timerThread?.let { t ->
            t.join(3000)
            if (t.isAlive) t.interrupt()
        }
        timerThread = null
        try { ttsRef?.stop(); ttsRef?.shutdown() } catch (_: Exception) {}
        ttsRef = null
        ttsReady = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action}")
        when (intent?.action) {
            ACTION_WARMUP -> {
                Log.d(TAG, "warmup: process ready")
                val n = Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.timer_running))
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setOngoing(true)
                    .build()
                if (Build.VERSION.SDK_INT >= 29) {
                    startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(NOTIFICATION_ID, n)
                }
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                paused = true
                Log.d(TAG, "paused")
                return START_NOT_STICKY
            }
            ACTION_RESUME -> {
                paused = false
                Log.d(TAG, "resumed")
                return START_NOT_STICKY
            }
        }

        stopPreviousTimer()

        if (!(wakeLock?.isHeld == true)) {
            wakeLock?.acquire()
        }
        startSilentAudio()

        @Suppress("DEPRECATION")
        val profile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_PROFILE, TimerProfile::class.java)
        } else {
            intent?.getParcelableExtra(EXTRA_PROFILE)
        } ?: run {
            Log.w(TAG, "No profile in intent, showing foreground and exiting")
            currentProfileId = null
            val n = Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.timer_running))
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setOngoing(true)
                .build()
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, n)
            }
            return START_NOT_STICKY
        }

        currentProfileId = intent?.getStringExtra(EXTRA_PROFILE_ID)
        val langTag = intent?.getStringExtra(EXTRA_LANG) ?: "en"
        val finishedText = intent?.getStringExtra(EXTRA_FINISHED_TEXT) ?: getString(R.string.timer_finished)
        val phrases = TtsCache.buildPhraseList(profile, finishedText)
        Log.d(TAG, "cacheDir=${TtsCache.cacheDir(applicationContext).absolutePath} phrases count=${phrases.size} langTag=$langTag finishedText='${finishedText.take(30)}'")
        phrases.forEachIndexed { i, s -> Log.d(TAG, "  phrase[$i] exists=${TtsCache.exists(applicationContext, langTag, s)} '${s.take(25)}'") }
        val useCacheOnly = TtsCache.allExist(applicationContext, langTag, phrases)
        Log.d(TAG, "useCacheOnly=$useCacheOnly (allExist=$useCacheOnly)")
        if (useCacheOnly) Log.d(TAG, "All phrases in cache, starting without TTS")

        running = true
        paused = false

        Log.d(TAG, "Starting timer: ${profile.name}, rounds=${profile.rounds.size}, lang=$langTag")

        val contentIntent = currentProfileId?.let { pid ->
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(MainActivity.EXTRA_OPEN_TIMER_PROFILE_ID, pid)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.timer_running))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply { contentIntent?.let { setContentIntent(it) } }
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (!useCacheOnly) {
            var ttsHolder: TextToSpeech? = null
            ttsHolder = TextToSpeech(applicationContext) { status ->
                Log.d(TAG, "TTS init status=$status")
                if (status == TextToSpeech.SUCCESS) {
                    val ttsLocale = when (langTag) {
                        "ru", "kk", "tg", "tt" -> Locale.forLanguageTag("ru")
                        "az", "uz" -> Locale.forLanguageTag("tr")
                        "zh" -> Locale.CHINESE
                        else -> Locale.forLanguageTag(langTag)
                    }
                    ttsHolder?.setLanguage(ttsLocale)
                    ttsHolder?.setSpeechRate(1f)
                    ttsHolder?.setPitch(1f)
                    val attrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    ttsHolder?.setAudioAttributes(attrs)
                    ttsRef = ttsHolder
                    ttsReady = true
                }
            }
        } else {
            ttsReady = true
        }

        timerThread = Thread {
            runTimer(profile, useCacheOnly, langTag, finishedText)
        }.apply { start() }

        return START_NOT_STICKY
    }

    private fun runTimer(profile: TimerProfile, useCacheOnly: Boolean, langTag: String, finishedText: String) {
        Log.d(TAG, "runTimer useCacheOnly=$useCacheOnly")
        if (!useCacheOnly) {
            var waited = 0
            while (!ttsReady && waited < 5000 && running) {
                Thread.sleep(100)
                waited += 100
            }
            val tts = ttsRef
            if (tts == null) Log.w(TAG, "TTS not ready after ${waited}ms, continuing without TTS")
            else Log.d(TAG, "TTS ready after ${waited}ms")
        } else {
            Log.d(TAG, "runTimer using cache only, no TTS wait")
        }

        val ttsParams = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }
        val tts = ttsRef
        var alarmTone: ToneGenerator? = null
        try {
            alarmTone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        } catch (_: Exception) {}
        val tickTone = ToneGenerator.TONE_CDMA_PIP
        val tickMs = 200
        val prolongedMs = 1200

        val engine = TimerEngine(profile) { event ->
            when (event) {
                is TimerEvent.RoundStart -> {
                    Log.d(TAG, "RoundStart ${event.roundIndex+1}/${event.totalRounds} '${event.round.name}'")
                    broadcastState(event.round.name, event.round.durationSeconds, event.round.durationSeconds, event.roundIndex + 1, event.totalRounds)
                    if (event.roundIndex == 0) {
                        Thread { playProlongedTone(prolongedMs) }.start()
                    }
                    if (useCacheOnly) {
                        playCacheFile(event.round.name, langTag, null)
                    } else {
                        tts?.speak(event.round.name, TextToSpeech.QUEUE_FLUSH, ttsParams, null)
                    }
                }
                is TimerEvent.Tick -> {
                    broadcastState(event.round.name, event.remainingSeconds, event.round.durationSeconds, event.roundIndex + 1, event.totalRounds)
                    if (event.round.warn10sec && event.round.durationSeconds >= 10 && event.remainingSeconds in 1..10) {
                        try { alarmTone?.startTone(tickTone, tickMs) } catch (_: Exception) {}
                    }
                }
                is TimerEvent.RoundEnd -> {
                    Log.d(TAG, "RoundEnd ${event.roundIndex+1}")
                    Thread { playProlongedTone(prolongedMs) }.start()
                }
                is TimerEvent.TrainingEnd -> {
                    Log.d(TAG, "TrainingEnd")
                    broadcastState("", 0, 0, 0, 0, isRunning = false)
                    running = false
                    if (useCacheOnly) {
                        playCacheFile(profile.name, langTag) {
                            playCacheFile(finishedText, langTag) {
                                Handler(Looper.getMainLooper()).postDelayed({ stopSelf() }, 6000)
                            }
                        }
                    } else {
                        tts?.speak(profile.name, TextToSpeech.QUEUE_FLUSH, ttsParams, null)
                        tts?.speak(finishedText, TextToSpeech.QUEUE_ADD, ttsParams, null)
                        Handler(Looper.getMainLooper()).postDelayed({ stopSelf() }, 6000)
                    }
                }
                else -> {}
            }
        }

        engine.advance()
        while (running) {
            if (paused) {
                Thread.sleep(500)
            } else {
                Thread.sleep(1000)
                if (!engine.advance()) break
            }
        }
        Log.d(TAG, "Timer loop ended, running=$running")

        try { alarmTone?.release() } catch (_: Exception) {}
    }

    private var cachedMediaPlayer: MediaPlayer? = null
    private val cachePlayerLock = Object()
    private var cachedToneBuffer: ShortArray? = null
    private var cachedToneDurationMs: Int = 0

    private fun playCacheFile(text: String, langTag: String, onDone: (() -> Unit)? = null) {
        Thread {
            try {
                val file = TtsCache.cacheFile(applicationContext, langTag, text)
                if (!file.exists()) {
                    Handler(Looper.getMainLooper()).post { onDone?.invoke() }
                    return@Thread
                }
                synchronized(cachePlayerLock) {
                    cachedMediaPlayer?.let {
                        try { it.stop(); it.reset() } catch (_: Exception) {}
                    }
                    val player = cachedMediaPlayer ?: MediaPlayer().also { cachedMediaPlayer = it }
                    player.setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                    player.setDataSource(file.absolutePath)
                    player.prepare()
                    player.setOnCompletionListener {
                        try { it.reset() } catch (_: Exception) {}
                        Handler(Looper.getMainLooper()).post { onDone?.invoke() }
                    }
                    player.start()
                }
            } catch (_: Exception) {
                Handler(Looper.getMainLooper()).post { onDone?.invoke() }
            }
        }.start()
    }

    private fun getToneBuffer(durationMs: Int): ShortArray {
        if (cachedToneBuffer != null && cachedToneDurationMs == durationMs) return cachedToneBuffer!!
        val sampleRate = 44100
        val numSamples = sampleRate * durationMs / 1000
        val buffer = ShortArray(numSamples)
        val freq = 880.0
        val vol = 0.55f
        for (i in 0 until numSamples) {
            buffer[i] = (sin(2.0 * PI * freq * i / sampleRate) * 32767 * vol).toInt().toShort()
        }
        cachedToneBuffer = buffer
        cachedToneDurationMs = durationMs
        return buffer
    }

    private fun playProlongedTone(durationMs: Int) {
        try {
            val buffer = getToneBuffer(durationMs)
            val sampleRate = 44100
            val vol = 0.55f
            val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes((buffer.size * 2).coerceAtLeast(minBuf))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.setVolume(vol)
            track.write(buffer, 0, buffer.size)
            track.play()
            Thread.sleep(durationMs.toLong())
            track.release()
        } catch (_: Exception) {}
    }

    private var cachedNotifBuilder: Notification.Builder? = null
    private var lastForegroundMs = 0L
    private var notifTickCount = 0
    private var notifFgCount = 0
    private var notifTotalMs = 0L

    private fun broadcastState(roundName: String, remaining: Int, roundTotal: Int, roundIndex: Int, totalRounds: Int, isRunning: Boolean = true) {
        val i = Intent(ACTION_TIMER_STATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_REMAINING, remaining)
            putExtra(EXTRA_ROUND_NAME, roundName)
            putExtra(EXTRA_ROUND_TOTAL, roundTotal)
            putExtra(EXTRA_ROUND_INDEX, roundIndex)
            putExtra(EXTRA_TOTAL_ROUNDS, totalRounds)
            putExtra(EXTRA_IS_RUNNING, isRunning)
        }
        sendBroadcast(i)
        val text = if (isRunning) "${roundName.take(10)} %02d:%02d".format(remaining / 60, remaining % 60) else getString(R.string.timer_finished)
        showNotification(text, forceStartForeground = !isRunning)
    }

    private fun showNotification(text: String, forceStartForeground: Boolean = false) {
        if (timerScreenVisible && !forceStartForeground) return
        val start = android.os.SystemClock.elapsedRealtime()
        val profileId = currentProfileId
        val contentIntent = if (profileId != null) {
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(MainActivity.EXTRA_OPEN_TIMER_PROFILE_ID, profileId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null
        val builder = cachedNotifBuilder ?: Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOnlyAlertOnce(true)
            .also { cachedNotifBuilder = it }
        if (contentIntent != null) builder.setContentIntent(contentIntent)
        builder.setContentText(text)
        builder.setStyle(Notification.BigTextStyle().bigText(text))
        val notification = builder.build()

        val now = android.os.SystemClock.elapsedRealtime()
        val needFg = forceStartForeground || (now - lastForegroundMs > 5000)
        if (needFg) {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            lastForegroundMs = now
            notifFgCount++
        } else {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification)
        }
        notifTickCount++
        notifTotalMs += android.os.SystemClock.elapsedRealtime() - start
        if (notifTickCount % 10 == 0) {
            Log.i("PerfFix", "notification: ticks=$notifTickCount fgCalls=$notifFgCount avgMs=${notifTotalMs / notifTickCount}ms")
        }
    }

    private fun startSilentAudio() {
        if (silentTrack != null) return
        try {
            val sampleRate = 8000
            val bufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            val silence = ShortArray(sampleRate)
            track.write(silence, 0, silence.size)
            track.setLoopPoints(0, sampleRate, -1)
            track.setVolume(0f)
            track.play()
            silentTrack = track
        } catch (_: Exception) {}
    }

    private fun stopSilentAudio() {
        try {
            silentTrack?.stop()
            silentTrack?.release()
        } catch (_: Exception) {}
        silentTrack = null
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        try { unregisterReceiver(visibilityReceiver) } catch (_: Exception) {}
        running = false
        timerThread?.let { t ->
            t.join(3000)
            if (t.isAlive) t.interrupt()
        }
        timerThread = null
        try { ttsRef?.stop(); ttsRef?.shutdown() } catch (_: Exception) {}
        ttsRef = null
        synchronized(cachePlayerLock) {
            try { cachedMediaPlayer?.release() } catch (_: Exception) {}
            cachedMediaPlayer = null
        }
        cachedToneBuffer = null
        stopSilentAudio()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "RaundTimer"
        private const val CHANNEL_ID = "raund_timer_visible"
        private const val NOTIFICATION_ID = 1
        const val ACTION_WARMUP = "com.raund.app.TimerService.WARMUP"
        const val ACTION_START = "com.raund.app.TimerService.START"
        const val ACTION_PAUSE = "com.raund.app.TimerService.PAUSE"
        const val ACTION_RESUME = "com.raund.app.TimerService.RESUME"
        const val ACTION_TIMER_STATE = "com.raund.app.TIMER_STATE"
        const val ACTION_TIMER_VISIBLE = "com.raund.app.TIMER_VISIBLE"
        const val ACTION_TIMER_HIDDEN = "com.raund.app.TIMER_HIDDEN"
        const val EXTRA_PROFILE = "profile"
        const val EXTRA_PROFILE_ID = "profileId"
        const val EXTRA_LANG = "lang"
        const val EXTRA_FINISHED_TEXT = "finishedText"
        const val EXTRA_REMAINING = "remaining"
        const val EXTRA_ROUND_NAME = "roundName"
        const val EXTRA_ROUND_TOTAL = "roundTotal"
        const val EXTRA_ROUND_INDEX = "roundIndex"
        const val EXTRA_TOTAL_ROUNDS = "totalRounds"
        const val EXTRA_IS_RUNNING = "isRunning"

        fun warmup(context: Context) {
            context.startForegroundService(Intent(context, TimerService::class.java).apply {
                action = ACTION_WARMUP
            })
        }

        fun start(context: Context, profileId: String, profile: TimerProfile, languageTag: String, finishedText: String) {
            context.startForegroundService(Intent(context, TimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PROFILE_ID, profileId)
                putExtra(EXTRA_PROFILE, profile)
                putExtra(EXTRA_LANG, languageTag)
                putExtra(EXTRA_FINISHED_TEXT, finishedText)
            })
        }

        fun pause(context: Context) {
            context.startService(Intent(context, TimerService::class.java).apply { action = ACTION_PAUSE })
        }

        fun resume(context: Context) {
            context.startService(Intent(context, TimerService::class.java).apply { action = ACTION_RESUME })
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TimerService::class.java))
        }
    }
}
