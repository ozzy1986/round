package com.raund.app.tts

import android.content.Context
import android.util.Log
import com.raund.app.timer.TimerProfile
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlin.coroutines.resume

object TtsCache {

    private const val TAG = "TtsCache"
    private const val CACHE_DIR_NAME = "tts"

    fun cacheDir(context: Context): File =
        File(context.cacheDir, CACHE_DIR_NAME)

    fun cacheFile(context: Context, locale: String, text: String): File {
        val dir = File(cacheDir(context), locale)
        dir.mkdirs()
        return File(dir, "${hash(text)}.wav")
    }

    fun exists(context: Context, locale: String, text: String): Boolean =
        cacheFile(context, locale, text).exists()

    fun allExist(context: Context, locale: String, phrases: List<String>): Boolean {
        val result = phrases.all { exists(context, locale, it) }
        Log.d(TAG, "allExist locale=$locale phrases=${phrases.size} result=$result missing=${phrases.filter { !exists(context, locale, it) }}")
        return result
    }

    fun buildPhraseList(profile: TimerProfile, finishedText: String): List<String> =
        profile.rounds.map { it.name } + profile.name + finishedText

    suspend fun ensureCache(context: Context, locale: String, phrases: List<String>): Boolean =
        withContext(Dispatchers.Main) {
            val missing = phrases.filter { !exists(context, locale, it) }
            Log.d(TAG, "ensureCache locale=$locale total=${phrases.size} missing=${missing.size} missing=$missing")
            if (missing.isEmpty()) {
                Log.d(TAG, "ensureCache skip, all exist")
                return@withContext true
            }
            suspendCancellableCoroutine { cont ->
                var ttsRef: TextToSpeech? = null
                ttsRef = TextToSpeech(context) { status ->
                    Log.d(TAG, "ensureCache TTS init status=$status")
                    if (status != TextToSpeech.SUCCESS) {
                        ttsRef?.shutdown()
                        Log.w(TAG, "ensureCache TTS init failed")
                        cont.resume(false)
                        return@TextToSpeech
                    }
                    val tts = ttsRef ?: return@TextToSpeech
                    val loc = when (locale) {
                        "ru", "kk", "tg", "tt" -> Locale.forLanguageTag("ru")
                        "az", "uz" -> Locale.forLanguageTag("tr")
                        "zh" -> Locale.CHINESE
                        else -> Locale.forLanguageTag(locale)
                    }
                    tts.setLanguage(loc)
                    val queue = ArrayDeque(missing)
                    fun doNext() {
                        if (queue.isEmpty()) {
                            Log.d(TAG, "ensureCache done, all ${missing.size} files written")
                            tts.shutdown()
                            cont.resume(true)
                            return
                        }
                        val text = queue.removeFirst()
                        val file = cacheFile(context, locale, text)
                        Log.d(TAG, "ensureCache synthesizing '${text.take(20)}...' -> ${file.name}")
                        file.parentFile?.mkdirs()
                        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {}
                            override fun onDone(utteranceId: String?) {
                                doNext()
                            }
                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                doNext()
                            }
                            override fun onError(utteranceId: String?, errorCode: Int) {
                                doNext()
                            }
                        })
                        val result = tts.synthesizeToFile(text, null, file, "gen")
                        if (result != TextToSpeech.SUCCESS) {
                            Log.w(TAG, "ensureCache synthesizeToFile failed for '${text.take(20)}' result=$result")
                            doNext()
                        }
                    }
                    doNext()
                }
            }
        }

    private fun hash(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return bytes.take(8).joinToString("") { "%02x".format(it) }
    }
}
