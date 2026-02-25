package com.raund.app

import android.app.Application
import com.raund.app.data.db.AppDatabase
import com.raund.app.data.remote.ApiService
import com.raund.app.data.repository.ProfileRepository
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RaundApplication : Application() {
    val database by lazy { AppDatabase.get(this) }
    val api by lazy {
        Retrofit.Builder()
            .baseUrl("http://10.0.2.2:3001/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
    val profileRepository by lazy {
        ProfileRepository(
            profileDao = database.profileDao(),
            roundDao = database.roundDao(),
            api = api
        )
    }
}
