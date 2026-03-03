package com.raund.app

import android.app.AlarmManager
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
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import com.raund.app.timer.TimerEngine
import com.raund.app.timer.TimerEvent
import com.raund.app.timer.TimerProfile
import com.raund.app.tts.TtsCache
import kotlin.math.PI
import kotlin.math.sin
import java.util.Locale

class TimerService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var silentTrack: AudioTrack? = null

    @Volatile private var paused = false
    @Volatile private var ttsRef: TextToSpeech? = null
    @Volatile private var ttsReady = false
    @Volatile private var running = false
    @Volatile private var timerScreenVisible = false
    @Volatile private var currentProfileId: String? = null
    @Volatile private var lastNotifText: String = ""

    private var engine: TimerEngine? = null
    private var alarmTone: ToneGenerator? = null
    private var useCacheOnly = false
    private var langTag = "en"
    private var lastTickRealtimeMs = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    private val visibilityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_TIMER_VISIBLE -> {
                    timerScreenVisible = true
                    stopForeground(STOP_FOREGROUND_REMOVE)
                }
                ACTION_TIMER_HIDDEN -> {
                    timerScreenVisible = false
                    if (running) {
                        showNotification(
                            lastNotifText.ifEmpty { getString(R.string.timer_running) },
                            forceStartForeground = true
                        )
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "raund:timer").apply {
            setReferenceCounted(false)
        }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.deleteNotificationChannel("raund_timer")
        nm.deleteNotificationChannel("raund_timer_visible")
        nm.deleteNotificationChannel("raund_timer_v2")
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = getString(R.string.timer_running)
        channel.setSound(null, null)
        channel.setShowBadge(true)
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        nm.createNotificationChannel(channel)
        val filter = IntentFilter().apply {
            addAction(ACTION_TIMER_VISIBLE)
            addAction(ACTION_TIMER_HIDDEN)
        }
        ContextCompat.registerReceiver(this, visibilityReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun stopPreviousTimer() {
        running = false
        cancelAlarmTick()
        engine = null
        try { alarmTone?.release() } catch (_: Exception) {}
        alarmTone = null
        try { ttsRef?.stop(); ttsRef?.shutdown() } catch (_: Exception) {}
        ttsRef = null
        ttsReady = false
        cachedTickPi = null
        cachedShowPi = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_WARMUP -> {
                val n = buildBasicNotification()
                doStartForeground(n)
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                paused = true
                return START_NOT_STICKY
            }
            ACTION_RESUME -> {
                paused = false
                scheduleAlarmTick()
                return START_NOT_STICKY
            }
            ACTION_TICK -> {
                handleTick()
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
            currentProfileId = null
            doStartForeground(buildBasicNotification())
            return START_NOT_STICKY
        }

        currentProfileId = intent?.getStringExtra(EXTRA_PROFILE_ID)
        langTag = intent?.getStringExtra(EXTRA_LANG) ?: "en"
        val finishedText = intent?.getStringExtra(EXTRA_FINISHED_TEXT) ?: getString(R.string.timer_finished)
        useCacheOnly = TtsCache.allExist(applicationContext, langTag, TtsCache.buildPhraseList(profile, finishedText))

        running = true
        paused = false
        lastTickRealtimeMs = SystemClock.elapsedRealtime()

        doStartForeground(buildContentNotification(getString(R.string.timer_running)))

        if (!useCacheOnly) {
            var ttsHolder: TextToSpeech? = null
            ttsHolder = TextToSpeech(applicationContext) { status ->
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
                    ttsHolder?.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    ttsRef = ttsHolder
                    ttsReady = true
                }
            }
        } else {
            ttsReady = true
        }

        try { alarmTone = ToneGenerator(AudioManager.STREAM_ALARM, 100) } catch (_: Exception) {}
        val ttsParams = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }
        val prolongedMs = 1200
        val tickTone = ToneGenerator.TONE_CDMA_PIP
        val tickMs = 200
        val savedFinishedText = finishedText

        engine = TimerEngine(profile) { event ->
            when (event) {
                is TimerEvent.RoundStart -> {
                    broadcastState(event.round.name, event.round.durationSeconds, event.round.durationSeconds, event.roundIndex + 1, event.totalRounds)
                    if (event.roundIndex == 0) {
                        Thread { playProlongedTone(prolongedMs) }.start()
                    }
                    if (useCacheOnly) {
                        playCacheFile(event.round.name, langTag, null)
                    } else {
                        ttsRef?.speak(event.round.name, TextToSpeech.QUEUE_FLUSH, ttsParams, null)
                    }
                }
                is TimerEvent.Tick -> {
                    broadcastState(event.round.name, event.remainingSeconds, event.round.durationSeconds, event.roundIndex + 1, event.totalRounds)
                    if (event.round.warn10sec && event.round.durationSeconds >= 10 && event.remainingSeconds in 1..10) {
                        try { alarmTone?.startTone(tickTone, tickMs) } catch (_: Exception) {}
                    }
                }
                is TimerEvent.RoundEnd -> {
                    Thread { playProlongedTone(prolongedMs) }.start()
                }
                is TimerEvent.TrainingEnd -> {
                    broadcastState("", 0, 0, 0, 0, isRunning = false)
                    running = false
                    cancelAlarmTick()
                    if (useCacheOnly) {
                        playCacheFile(profile.name, langTag) {
                            playCacheFile(savedFinishedText, langTag) {
                                mainHandler.postDelayed({ stopSelf() }, 6000)
                            }
                        }
                    } else {
                        ttsRef?.speak(profile.name, TextToSpeech.QUEUE_FLUSH, ttsParams, null)
                        ttsRef?.speak(savedFinishedText, TextToSpeech.QUEUE_ADD, ttsParams, null)
                        mainHandler.postDelayed({ stopSelf() }, 6000)
                    }
                }
                else -> {}
            }
        }

        engine?.advance()
        scheduleAlarmTick()

        return START_NOT_STICKY
    }

    private fun handleTick() {
        if (!running) return
        if (paused) {
            lastTickRealtimeMs = SystemClock.elapsedRealtime()
            scheduleAlarmTick()
            return
        }
        try {
            val e = engine ?: return
            val now = SystemClock.elapsedRealtime()
            val elapsedMs = now - lastTickRealtimeMs
            val steps = (elapsedMs / 1000).toInt().coerceAtLeast(1)
            lastTickRealtimeMs = now
            for (i in 0 until steps) {
                if (!e.advance()) return
            }
            scheduleAlarmTick()
        } catch (_: Exception) {
            scheduleAlarmTick()
        }
    }

    private var cachedTickPi: PendingIntent? = null
    private var cachedShowPi: PendingIntent? = null

    private fun getTickPendingIntent(): PendingIntent {
        return cachedTickPi ?: PendingIntent.getService(
            this, ALARM_REQUEST_CODE,
            Intent(this, TimerService::class.java).apply { action = ACTION_TICK },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ).also { cachedTickPi = it }
    }

    private fun getShowPendingIntent(): PendingIntent {
        return cachedShowPi ?: run {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                currentProfileId?.let { putExtra(MainActivity.EXTRA_OPEN_TIMER_PROFILE_ID, it) }
            }
            PendingIntent.getActivity(
                this, ALARM_SHOW_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ).also { cachedShowPi = it }
        }
    }

    private fun scheduleAlarmTick() {
        if (!running) return
        val am = getSystemService(ALARM_SERVICE) as AlarmManager
        try {
            am.setAlarmClock(
                AlarmManager.AlarmClockInfo(System.currentTimeMillis() + 1000, getShowPendingIntent()),
                getTickPendingIntent()
            )
        } catch (_: Exception) {
            mainHandler.postDelayed({
                if (running) handleTick()
            }, 1000)
        }
    }

    private fun cancelAlarmTick() {
        try {
            val am = getSystemService(ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getService(
                this, ALARM_REQUEST_CODE,
                Intent(this, TimerService::class.java).apply { action = ACTION_TICK },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            )
            if (pi != null) am.cancel(pi)
        } catch (_: Exception) {}
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
                    mainHandler.post { onDone?.invoke() }
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
                        mainHandler.post { onDone?.invoke() }
                    }
                    player.start()
                }
            } catch (_: Exception) {
                mainHandler.post { onDone?.invoke() }
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
        lastNotifText = text
        showNotification(text, forceStartForeground = !isRunning)
    }

    private fun showNotification(text: String, forceStartForeground: Boolean = false) {
        if (timerScreenVisible && !forceStartForeground) return
        val builder = cachedNotifBuilder ?: Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOnlyAlertOnce(true)
            .also { cachedNotifBuilder = it }
        val profileId = currentProfileId
        if (profileId != null) {
            builder.setContentIntent(PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(MainActivity.EXTRA_OPEN_TIMER_PROFILE_ID, profileId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
        }
        builder.setContentText(text)
        builder.setStyle(Notification.BigTextStyle().bigText(text))
        val notification = builder.build()

        val now = SystemClock.elapsedRealtime()
        val needFg = forceStartForeground || (now - lastForegroundMs > 5000)
        if (needFg) {
            doStartForeground(notification)
            lastForegroundMs = now
        } else {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildBasicNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.timer_running))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .build()

    private fun buildContentNotification(text: String): Notification {
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        val profileId = currentProfileId
        if (profileId != null) {
            builder.setContentIntent(PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(MainActivity.EXTRA_OPEN_TIMER_PROFILE_ID, profileId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
        }
        return builder.build()
    }

    private fun doStartForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
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
        try { silentTrack?.stop(); silentTrack?.release() } catch (_: Exception) {}
        silentTrack = null
    }

    override fun onDestroy() {
        try { unregisterReceiver(visibilityReceiver) } catch (_: Exception) {}
        running = false
        cancelAlarmTick()
        engine = null
        try { alarmTone?.release() } catch (_: Exception) {}
        alarmTone = null
        try { ttsRef?.stop(); ttsRef?.shutdown() } catch (_: Exception) {}
        ttsRef = null
        synchronized(cachePlayerLock) {
            try { cachedMediaPlayer?.release() } catch (_: Exception) {}
            cachedMediaPlayer = null
        }
        cachedToneBuffer = null
        stopSilentAudio()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        running = false
        cancelAlarmTick()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "raund_timer_v3"
        private const val NOTIFICATION_ID = 1
        private const val ALARM_REQUEST_CODE = 42
        private const val ALARM_SHOW_REQUEST_CODE = 43
        const val ACTION_WARMUP = "com.raund.app.TimerService.WARMUP"
        const val ACTION_START = "com.raund.app.TimerService.START"
        const val ACTION_PAUSE = "com.raund.app.TimerService.PAUSE"
        const val ACTION_RESUME = "com.raund.app.TimerService.RESUME"
        private const val ACTION_TICK = "com.raund.app.TimerService.TICK"
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
