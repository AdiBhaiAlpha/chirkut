package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "failed_messages")
data class FailedMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val retryCount: Int = 0
)
