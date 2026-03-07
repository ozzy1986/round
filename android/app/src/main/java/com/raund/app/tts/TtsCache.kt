package com.raund.app.tts

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.raund.app.timer.TimerProfile
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

object TtsCache {

    private const val TAG = "TtsCache"
    private const val PERF = "PerfFix"
    private const val CACHE_DIR_NAME = "tts"
    private const val PARALLEL_INSTANCES = 3

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
        val missing = mutableListOf<String>()
        val result = phrases.all { phrase ->
            val ex = exists(context, locale, phrase)
            if (!ex) missing.add(phrase)
            ex
        }
        Log.d(TAG, "allExist locale=$locale phrases=${phrases.size} result=$result missing=$missing")
        return result
    }

    fun buildPhraseList(profile: TimerProfile, finishedText: String): List<String> =
        profile.rounds.map { it.name } + profile.name + finishedText

    private fun ttsLocale(locale: String): Locale = when (locale) {
        "ru", "kk", "tg", "tt" -> Locale.forLanguageTag("ru")
        "az", "uz" -> Locale.forLanguageTag("tr")
        "zh" -> Locale.CHINESE
        else -> Locale.forLanguageTag(locale)
    }

    suspend fun ensureCache(context: Context, locale: String, phrases: List<String>): Boolean {
        val startMs = SystemClock.elapsedRealtime()
        val missing = phrases.filter { !exists(context, locale, it) }
        Log.i(PERF, "ensureCache START: ${missing.size} missing out of ${phrases.size} phrases")
        if (missing.isEmpty()) {
            Log.i(PERF, "ensureCache DONE: 0ms (all cached)")
            return true
        }

        val instanceCount = minOf(PARALLEL_INSTANCES, missing.size)
        val chunks = missing.chunked((missing.size + instanceCount - 1) / instanceCount)
        Log.i(PERF, "ensureCache: splitting ${missing.size} phrases into ${chunks.size} chunks")

        val successCount = AtomicInteger(0)
        val results = coroutineScope {
            chunks.mapIndexed { i, chunk ->
                async { synthesizeChunk(context, locale, chunk, i, successCount) }
            }.awaitAll()
        }

        val totalMs = SystemClock.elapsedRealtime() - startMs
        val allOk = results.all { it }
        Log.i(PERF, "ensureCache DONE: ${totalMs}ms, ${successCount.get()}/${missing.size} synthesized, ok=$allOk")
        return allOk
    }

    private suspend fun synthesizeChunk(
        context: Context,
        locale: String,
        chunk: List<String>,
        instanceIndex: Int,
        successCount: AtomicInteger
    ): Boolean = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            var ttsRef: TextToSpeech? = null
            cont.invokeOnCancellation {
                try { ttsRef?.shutdown() } catch (_: Exception) {}
            }
            ttsRef = TextToSpeech(context) { status ->
                if (status != TextToSpeech.SUCCESS) {
                    Log.w(PERF, "TTS instance #$instanceIndex init failed")
                    ttsRef?.shutdown()
                    if (cont.isActive) cont.resume(false)
                    return@TextToSpeech
                }
                val tts = ttsRef ?: return@TextToSpeech
                tts.setLanguage(ttsLocale(locale))

                val queue = ArrayDeque(chunk)
                fun doNext() {
                    if (queue.isEmpty()) {
                        tts.shutdown()
                        cont.resume(true)
                        return
                    }
                    val text = queue.removeFirst()
                    val phraseStart = SystemClock.elapsedRealtime()
                    val file = cacheFile(context, locale, text)
                    file.parentFile?.mkdirs()
                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            val elapsed = SystemClock.elapsedRealtime() - phraseStart
                            Log.i(PERF, "synthesize '${text.take(15)}' took ${elapsed}ms on instance #$instanceIndex")
                            successCount.incrementAndGet()
                            doNext()
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            Log.w(PERF, "synthesize error '${text.take(15)}' on instance #$instanceIndex")
                            doNext()
                        }
                        override fun onError(utteranceId: String?, errorCode: Int) {
                            Log.w(PERF, "synthesize error '${text.take(15)}' code=$errorCode on instance #$instanceIndex")
                            doNext()
                        }
                    })
                    val result = tts.synthesizeToFile(text, null, file, "gen_${instanceIndex}_${text.hashCode()}")
                    if (result != TextToSpeech.SUCCESS) {
                        Log.w(PERF, "synthesizeToFile failed '${text.take(15)}' result=$result on instance #$instanceIndex")
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
