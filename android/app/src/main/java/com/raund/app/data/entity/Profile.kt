package com.raund.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey
    val id: String,
    val name: String,
    val emoji: String,
    val updatedAt: Long = System.currentTimeMillis()
)
