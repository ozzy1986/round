package com.raund.app

import android.app.Application
import com.raund.app.data.db.AppDatabase
import com.raund.app.data.local.SyncPrefs
import com.raund.app.data.local.TokenStore
import com.raund.app.data.remote.ApiService
import com.raund.app.data.remote.AuthAuthenticator
import com.raund.app.data.remote.AuthService
import com.raund.app.data.remote.TokenInterceptor
import com.raund.app.data.repository.ProfileRepository
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RaundApplication : Application() {
    val database by lazy { AppDatabase.get(this) }
    val tokenStore by lazy { TokenStore(this) }
    private val syncPrefs by lazy { SyncPrefs(this) }

    private val authRetrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    val authService by lazy { authRetrofit.create(AuthService::class.java) }

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(TokenInterceptor(tokenStore))
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
            syncPrefs = syncPrefs
        )
    }
}
