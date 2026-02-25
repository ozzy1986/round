package com.raund.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rounds",
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("profileId")]
)
data class Round(
    @PrimaryKey
    val id: String,
    val profileId: String,
    val name: String,
    val durationSeconds: Int,
    val warn10sec: Boolean,
    val position: Int
)
