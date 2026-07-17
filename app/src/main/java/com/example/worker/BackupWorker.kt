package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.DataStoreManager
import com.example.data.TelemetryRepository

class BackupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val dataStoreManager = DataStoreManager(applicationContext)
        val repository = TelemetryRepository(applicationContext, db.appDao(), dataStoreManager)

        return try {
            repository.log("BackupWorker", "WorkManager backup task initiated.", "INFO")
            repository.processOfflineQueue()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
