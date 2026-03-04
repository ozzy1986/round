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
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.content.ContextCompat
import com.raund.app.timer.TimerProfile
import com.raund.app.tts.TtsCache
import kotlin.math.PI
import kotlin.math.sin
import java.util.Locale
import java.util.concurrent.Executors

class TimerService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaSession: MediaSessionCompat? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var keepAliveTrack: AudioTrack? = null
    private var keepAliveThread: Thread? = null
    @Volatile private var keepAliveRunning = false

    @Volatile private var paused = false
    @Volatile private var autoPausedByScreenOff = false
    @Volatile private var autoPausedByLeavingApp = false
    @Volatile private var screenIsOff = false
    @Volatile private var ttsRef: TextToSpeech? = null
    @Volatile private var ttsReady = false
    @Volatile private var running = false
    @Volatile private var timerScreenVisible = false
    @Volatile private var currentProfileId: String? = null
    @Volatile private var lastNotifText: String = ""

    private var timerThread: Thread? = null
    private var useCacheOnly = false
    private var langTag = "en"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "raund-audio").apply { isDaemon = true } }

    private val leaveAppCheckRunnable = object : Runnable {
        override fun run() {
            if (!running || paused || screenIsOff) return
            if (!SettingsManager.isKeepRunningWhenLeavingApp(this@TimerService)) {
                Log.i(TAG, "leaveAppCheck: user left app -> auto-pause")
                paused = true
                autoPausedByLeavingApp = true
            }
        }
    }

    private val visibilityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_TIMER_VISIBLE -> {
                    Log.i(TAG, "visibilityReceiver: TIMER_VISIBLE running=$running wakeLock.isHeld=${wakeLock?.isHeld}")
                    timerScreenVisible = true
                    mainHandler.removeCallbacks(leaveAppCheckRunnable)
                    if (autoPausedByScreenOff || autoPausedByLeavingApp) {
                        Log.i(TAG, "visibilityReceiver: auto-resume (screenOff=$autoPausedByScreenOff leaveApp=$autoPausedByLeavingApp)")
                        paused = false
                        autoPausedByScreenOff = false
                        autoPausedByLeavingApp = false
                    }
                }
                ACTION_TIMER_HIDDEN -> {
                    Log.i(TAG, "visibilityReceiver: TIMER_HIDDEN running=$running wakeLock.isHeld=${wakeLock?.isHeld}")
                    timerScreenVisible = false
                    mainHandler.removeCallbacks(leaveAppCheckRunnable)
                    if (running && !paused) {
                        mainHandler.postDelayed(leaveAppCheckRunnable, 300)
                    }
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

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenIsOff = true
                    mainHandler.removeCallbacks(leaveAppCheckRunnable)
                    if (running && !paused && !SettingsManager.isKeepRunningOnScreenOff(this@TimerService)) {
                        Log.i(TAG, "screenStateReceiver: SCREEN_OFF -> auto-pause")
                        paused = true
                        autoPausedByScreenOff = true
                    }
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenIsOff = false
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")
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

        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenStateReceiver, screenFilter)
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
        Log.i(TAG, "onStartCommand action=${intent?.action} flags=$flags startId=$startId")
        when (intent?.action) {
            ACTION_WARMUP -> {
                val n = buildBasicNotification()
                doStartForeground(n)
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                paused = true
                autoPausedByScreenOff = false
                autoPausedByLeavingApp = false
                TimerStateHolder.update(paused = true)
                return START_NOT_STICKY
            }
            ACTION_RESUME -> {
                paused = false
                autoPausedByScreenOff = false
                autoPausedByLeavingApp = false
                TimerStateHolder.update(paused = false)
                return START_NOT_STICKY
            }
        }

        stopPreviousTimer()

        if (!(wakeLock?.isHeld == true)) {
            wakeLock?.acquire()
            Log.i(TAG, "wakeLock.acquire() -> isHeld=${wakeLock?.isHeld}")
        } else {
            Log.i(TAG, "wakeLock already held, skip acquire")
        }
        startMediaSession()

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
        autoPausedByScreenOff = false
        autoPausedByLeavingApp = false

        Log.i(TAG, "Starting timer: ${profile.name}, rounds=${profile.rounds.size}, useCacheOnly=$useCacheOnly")

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

        timerThread = Thread {
            runTimer(profile, useCacheOnly, langTag, finishedText)
        }.apply {
            name = "raund-timer"
            start()
            Log.i(TAG, "timerThread started")
        }

        return START_NOT_STICKY
    }

    private fun runTimer(profile: TimerProfile, useCacheOnly: Boolean, langTag: String, finishedText: String) {
        if (!useCacheOnly) {
            var waited = 0
            while (!ttsReady && waited < 5000 && running) {
                Thread.sleep(100)
                waited += 100
            }
        }

        val rounds = profile.rounds
        if (rounds.isEmpty()) {
            running = false
            return
        }

        val ttsParams = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }
        val tts = ttsRef
        var alarmTone: ToneGenerator? = null
        try { alarmTone = ToneGenerator(AudioManager.STREAM_ALARM, 100) } catch (_: Exception) {}
        val tickTone = ToneGenerator.TONE_CDMA_PIP
        val tickMs = 200
        val prolongedMs = 1200
        val totalRounds = rounds.size

        var roundIndex = 0
        var round = rounds[0]
        var roundStartRealtimeMs = SystemClock.elapsedRealtime()
        var roundDurationMs = round.durationSeconds * 1000L
        var wasPaused = false
        var remainingMsWhenPaused = 0L

        updateStateAndNotification(round.name, round.durationSeconds, round.durationSeconds, 1, totalRounds)
        if (roundIndex == 0) {
            playProlongedTone(prolongedMs)
        }
        if (useCacheOnly) {
            playCacheFile(round.name, langTag, null)
        } else {
            tts?.speak(round.name, TextToSpeech.QUEUE_FLUSH, ttsParams, null)
        }

        var lastBroadcastRemainingSeconds = round.durationSeconds
        while (running) {
            if (paused) {
                if (!wasPaused) {
                    remainingMsWhenPaused = roundStartRealtimeMs + roundDurationMs - SystemClock.elapsedRealtime()
                    wasPaused = true
                }
                Thread.sleep(500)
                continue
            }
            if (wasPaused) {
                roundStartRealtimeMs = SystemClock.elapsedRealtime() - remainingMsWhenPaused
                wasPaused = false
            }

            val now = SystemClock.elapsedRealtime()
            val remainingMs = roundStartRealtimeMs + roundDurationMs - now
            val remainingSeconds = (remainingMs / 1000).coerceAtLeast(0).toInt()

            if (remainingMs <= 0) {
                var skippedRounds = 0
                var rm = remainingMs
                while (rm <= 0) {
                    roundStartRealtimeMs += roundDurationMs
                    roundIndex++
                    skippedRounds++
                    if (roundIndex >= totalRounds) break
                    round = rounds[roundIndex]
                    roundDurationMs = round.durationSeconds * 1000L
                    rm = roundStartRealtimeMs + roundDurationMs - now
                }
                if (skippedRounds > 1) {
                    Log.i(TAG, "freeze skip: jumped $skippedRounds rounds at once")
                }
                playProlongedTone(prolongedMs)
                if (roundIndex >= totalRounds) {
                    updateStateAndNotification("", 0, 0, 0, 0, isRunning = false)
                    running = false
                    if (useCacheOnly) {
                        playCacheFile(profile.name, langTag) {
                            playCacheFile(finishedText, langTag) {
                                mainHandler.postDelayed({ stopSelf() }, 6000)
                            }
                        }
                    } else {
                        tts?.speak(profile.name, TextToSpeech.QUEUE_FLUSH, ttsParams, null)
                        tts?.speak(finishedText, TextToSpeech.QUEUE_ADD, ttsParams, null)
                        mainHandler.postDelayed({ stopSelf() }, 6000)
                    }
                    try { alarmTone?.release() } catch (_: Exception) {}
                    return
                }
                val currentRemaining = ((roundStartRealtimeMs + roundDurationMs - now) / 1000).coerceAtLeast(0).toInt()
                lastBroadcastRemainingSeconds = currentRemaining
                updateStateAndNotification(round.name, currentRemaining, round.durationSeconds, roundIndex + 1, totalRounds)
                if (useCacheOnly) {
                    playCacheFile(round.name, langTag, null)
                } else {
                    tts?.speak(round.name, TextToSpeech.QUEUE_FLUSH, ttsParams, null)
                }
                val secondsElapsed = (now - roundStartRealtimeMs) / 1000
                val nextMs = roundStartRealtimeMs + (secondsElapsed + 1) * 1000
                val sleepMs = (nextMs - SystemClock.elapsedRealtime()).coerceIn(1, 1000)
                Thread.sleep(sleepMs)
                continue
            }

            if (remainingSeconds != lastBroadcastRemainingSeconds) {
                lastBroadcastRemainingSeconds = remainingSeconds
                updateStateAndNotification(round.name, remainingSeconds, round.durationSeconds, roundIndex + 1, totalRounds)
                if (round.warn10sec && round.durationSeconds >= 10 && remainingSeconds in 1..10) {
                    try { alarmTone?.startTone(tickTone, tickMs) } catch (_: Exception) {}
                }
            }

            val secondsElapsedInRound = (now - roundStartRealtimeMs) / 1000
            val nextTickRealtimeMs = roundStartRealtimeMs + (secondsElapsedInRound + 1) * 1000
            val sleepMs = (nextTickRealtimeMs - SystemClock.elapsedRealtime()).coerceIn(1, 1000)
            Thread.sleep(sleepMs)
        }
        Log.i(TAG, "runTimer loop exited running=$running")

        try { alarmTone?.release() } catch (_: Exception) {}
    }

    private var cachedMediaPlayer: MediaPlayer? = null
    private val cachePlayerLock = Object()
    private var cachedToneBuffer: ShortArray? = null
    private var cachedToneDurationMs: Int = 0
    private var prolongedToneTrack: AudioTrack? = null
    private val prolongedToneLock = Object()

    private fun playCacheFile(text: String, langTag: String, onDone: (() -> Unit)? = null) {
        audioExecutor.execute {
            try {
                val file = TtsCache.cacheFile(applicationContext, langTag, text)
                if (!file.exists()) {
                    mainHandler.post { onDone?.invoke() }
                    return@execute
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
            } catch (e: Exception) {
                Log.w(TAG, "playCacheFile failed", e)
                mainHandler.post { onDone?.invoke() }
            }
        }
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
        audioExecutor.execute {
            try {
                synchronized(prolongedToneLock) {
                    var track = prolongedToneTrack
                    if (track == null) {
                        val buffer = getToneBuffer(durationMs)
                        val sampleRate = 44100
                        val vol = 0.55f
                        val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                        track = AudioTrack.Builder()
                            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                            .setBufferSizeInBytes((buffer.size * 2).coerceAtLeast(minBuf))
                            .setTransferMode(AudioTrack.MODE_STATIC)
                            .build()
                        track.setVolume(vol)
                        track.write(buffer, 0, buffer.size)
                        prolongedToneTrack = track
                    }
                    track.stop()
                    track.reloadStaticData()
                    track.setPlaybackHeadPosition(0)
                    track.play()
                }
                Thread.sleep(durationMs.toLong())
                prolongedToneTrack?.stop()
            } catch (e: Exception) {
                Log.w(TAG, "playProlongedTone failed", e)
            }
        }
    }

    private var cachedNotifBuilder: Notification.Builder? = null
    private var lastForegroundMs = 0L

    private fun updateStateAndNotification(roundName: String, remaining: Int, roundTotal: Int, roundIndex: Int, totalRounds: Int, isRunning: Boolean = true) {
        TimerStateHolder.update(
            remaining = remaining,
            roundName = roundName,
            roundTotal = roundTotal,
            roundIndex = roundIndex,
            totalRounds = totalRounds,
            isRunning = isRunning,
            paused = paused
        )
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
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startMediaSession() {
        if (mediaSession != null) return

        val mediaAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        try {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            val focusReq = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(mediaAttrs)
                .setOnAudioFocusChangeListener {}
                .build()
            am.requestAudioFocus(focusReq)
            audioFocusRequest = focusReq
            Log.i(TAG, "AudioFocus requested AUDIOFOCUS_GAIN")
        } catch (e: Exception) {
            Log.e(TAG, "AudioFocus request failed", e)
        }

        startKeepAliveAudio(mediaAttrs)

        try {
            val session = MediaSessionCompat(this, "RaundTimer")
            session.isActive = true
            session.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE)
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f)
                    .build()
            )
            mediaSession = session
            Log.i(TAG, "MediaSession started, isActive=true")
        } catch (e: Exception) {
            Log.e(TAG, "MediaSession start failed", e)
        }
    }

    private fun startKeepAliveAudio(attrs: AudioAttributes) {
        if (keepAliveRunning) return
        try {
            val sampleRate = 44100
            val bufSize = AudioTrack.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)
            val track = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track.play()
            keepAliveTrack = track
            keepAliveRunning = true

            val buf = ShortArray(sampleRate / 10)
            val amp = 30.toShort()
            val freq = 1.0
            for (i in buf.indices) {
                buf[i] = (sin(2.0 * PI * freq * i / sampleRate) * amp).toInt().toShort()
            }

            keepAliveThread = Thread {
                Log.i(TAG, "keepAliveAudio started (stream USAGE_MEDIA, amp=$amp)")
                while (keepAliveRunning) {
                    try {
                        track.write(buf, 0, buf.size)
                    } catch (_: Exception) { break }
                }
                Log.i(TAG, "keepAliveAudio stopped")
            }.apply {
                name = "raund-keepalive"
                isDaemon = true
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "keepAliveAudio start failed", e)
        }
    }

    private fun stopKeepAliveAudio() {
        keepAliveRunning = false
        try { keepAliveTrack?.stop() } catch (_: Exception) {}
        try { keepAliveTrack?.release() } catch (_: Exception) {}
        keepAliveTrack = null
        keepAliveThread?.join(2000)
        keepAliveThread = null
    }

    private fun stopMediaSession() {
        stopKeepAliveAudio()

        try {
            mediaSession?.isActive = false
            mediaSession?.release()
        } catch (_: Exception) {}
        mediaSession = null

        try {
            val afr = audioFocusRequest
            if (afr != null) {
                val am = getSystemService(AUDIO_SERVICE) as AudioManager
                am.abandonAudioFocusRequest(afr)
            }
        } catch (_: Exception) {}
        audioFocusRequest = null
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy running=$running timerThread.alive=${timerThread?.isAlive} wakeLock.isHeld=${wakeLock?.isHeld}")
        try { unregisterReceiver(visibilityReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(screenStateReceiver) } catch (_: Exception) {}
        mainHandler.removeCallbacks(leaveAppCheckRunnable)
        running = false
        autoPausedByScreenOff = false
        autoPausedByLeavingApp = false
        timerThread?.let { t ->
            t.join(3000)
            if (t.isAlive) {
                Log.i(TAG, "onDestroy: timerThread still alive after 3s, interrupting")
                t.interrupt()
            }
        }
        timerThread = null
        try { ttsRef?.stop(); ttsRef?.shutdown() } catch (_: Exception) {}
        ttsRef = null
        synchronized(cachePlayerLock) {
            try { cachedMediaPlayer?.release() } catch (_: Exception) {}
            cachedMediaPlayer = null
        }
        cachedToneBuffer = null
        synchronized(prolongedToneLock) {
            try { prolongedToneTrack?.release() } catch (_: Exception) {}
            prolongedToneTrack = null
        }
        audioExecutor.shutdown()
        stopMediaSession()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            Log.i(TAG, "wakeLock.release()")
        }
        wakeLock = null
        TimerStateHolder.reset()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "onTaskRemoved running=$running")
        running = false
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "RaundTimer"
        private const val CHANNEL_ID = "raund_timer_v3"
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
