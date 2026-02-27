package com.raund.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.raund.app.data.entity.Round
import kotlinx.coroutines.flow.Flow

data class RoundStats(
    val profileId: String,
    val roundsCount: Long,
    val totalDurationSeconds: Long
)

@Dao
interface RoundDao {
    @Query("SELECT * FROM rounds WHERE profileId = :profileId ORDER BY position ASC")
    fun getByProfileId(profileId: String): Flow<List<Round>>

    @Query("SELECT * FROM rounds WHERE profileId = :profileId ORDER BY position ASC")
    suspend fun getByProfileIdOnce(profileId: String): List<Round>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rounds: List<Round>)

    @Query("DELETE FROM rounds WHERE profileId = :profileId")
    suspend fun deleteByProfileId(profileId: String)

    @Query(
        """
        SELECT
            profileId AS profileId,
            COUNT(*) AS roundsCount,
            COALESCE(SUM(durationSeconds), 0) AS totalDurationSeconds
        FROM rounds
        GROUP BY profileId
        """
    )
    fun getStatsByProfile(): Flow<List<RoundStats>>
}
