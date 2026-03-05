package com.raund.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.raund.app.data.dao.ProfileDao
import com.raund.app.data.dao.RoundDao
import com.raund.app.data.entity.Profile
import com.raund.app.data.entity.Round

@Database(
    entities = [Profile::class, Round::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun roundDao(): RoundDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "raund.db")
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
