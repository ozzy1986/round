package com.raund.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.raund.app.data.entity.Profile
import com.raund.app.data.entity.Round
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<Profile>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: String): Profile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: Profile)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE profiles SET name = :name, emoji = :emoji, updatedAt = :updatedAt WHERE id = :id")
    suspend fun update(id: String, name: String, emoji: String, updatedAt: Long)
}
