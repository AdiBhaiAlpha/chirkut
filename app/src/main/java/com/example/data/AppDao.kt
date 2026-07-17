package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // Log entries
    @Query("SELECT * FROM app_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<LogEntry>>

    @Query("SELECT * FROM app_logs WHERE message LIKE :query OR tag LIKE :query ORDER BY timestamp DESC")
    fun searchLogs(query: String): Flow<List<LogEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: LogEntry)

    @Query("DELETE FROM app_logs")
    suspend fun clearLogs()

    // Failed messages
    @Query("SELECT * FROM failed_messages ORDER BY timestamp ASC")
    suspend fun getFailedMessages(): List<FailedMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFailedMessage(msg: FailedMessage)

    @Delete
    suspend fun deleteFailedMessage(msg: FailedMessage)

    @Update
    suspend fun updateFailedMessage(msg: FailedMessage)
}
