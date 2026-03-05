package com.raund.app

import android.app.Application
import android.os.SystemClock
import android.util.Log
import io.sentry.android.core.SentryAndroid
import com.raund.app.data.db.AppDatabase
import com.raund.app.data.local.DataConsentPrefs
import com.raund.app.data.local.SyncPrefs
import com.raund.app.data.local.TokenStore
import com.raund.app.data.remote.ApiService
import com.raund.app.data.remote.AuthAuthenticator
import com.raund.app.data.remote.AuthService
import com.raund.app.data.remote.TokenInterceptor
import com.raund.app.data.repository.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class RaundApplication : Application() {
    val database by lazy { AppDatabase.get(this) }
    val tokenStore by lazy { TokenStore(this) }
    private val syncPrefs by lazy { SyncPrefs(this) }
    val dataConsentPrefs by lazy { DataConsentPrefs(this) }

    @Volatile
    var repositoryReady = false
        private set

    private val _repositoryReadyFlow = MutableStateFlow(false)
    val repositoryReadyFlow: StateFlow<Boolean> = _repositoryReadyFlow.asStateFlow()

    private val initScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val authRetrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    val authService by lazy { authRetrofit.create(AuthService::class.java) }

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(5, 30, TimeUnit.SECONDS))
            .addInterceptor(TokenInterceptor(tokenStore))
            .addInterceptor { chain ->
                val request = chain.request()
                val startMs = System.currentTimeMillis()
                try {
                    val response = chain.proceed(request)
                    val elapsed = System.currentTimeMillis() - startMs
                    Log.i("PerfFix", "HTTP ${request.method} ${request.url.encodedPath}: ${response.code} in ${elapsed}ms")
                    response
                } catch (e: Exception) {
                    val elapsed = System.currentTimeMillis() - startMs
                    Log.i("PerfFix", "HTTP ${request.method} ${request.url.encodedPath}: FAILED in ${elapsed}ms: ${e.message}")
                    throw e
                }
            }
            .authenticator(AuthAuthenticator(tokenStore, authService))
            .build()
    }
    val api by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
    val profileRepository by lazy {
        ProfileRepository(
            profileDao = database.profileDao(),
            roundDao = database.roundDao(),
            api = api,
            tokenStore = tokenStore,
            authService = authService,
            syncPrefs = syncPrefs,
            dataConsentPrefs = dataConsentPrefs,
            database = database
        )
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.SENTRY_DSN.isNotEmpty()) {
            SentryAndroid.init(this) { options ->
                options.dsn = BuildConfig.SENTRY_DSN
                options.isDebug = false
            }
        }
        val appStartMs = SystemClock.elapsedRealtime()
        Log.i("PerfFix", "App onCreate start")
        initScope.launch {
            val start = SystemClock.elapsedRealtime()
            try {
                profileRepository.preloadCache()
            } catch (e: Exception) {
                Log.e("PerfFix", "preloadCache failed", e)
            } finally {
                repositoryReady = true
                _repositoryReadyFlow.value = true
            }
            val preloadMs = SystemClock.elapsedRealtime() - start
            val totalMs = SystemClock.elapsedRealtime() - appStartMs
            Log.i("PerfFix", "App ready in ${totalMs}ms (preload=${preloadMs}ms)")
        }
        initScope.launch {
            try { profileRepository.requestSync() } catch (_: Exception) {}
        }
    }
}
