package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.AppDatabase
import com.example.data.DataStoreManager
import com.example.data.TelemetryRepository
import com.example.service.ChirkutForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            val db = AppDatabase.getDatabase(context)
            val dataStoreManager = DataStoreManager(context)
            val repository = TelemetryRepository(context, db.appDao(), dataStoreManager)

            receiverScope.launch {
                try {
                    val enabled = dataStoreManager.foregroundServiceEnabled.first()
                    if (enabled) {
                        repository.log("BootReceiver", "Boot completed. Restarting foreground service.", "INFO")
                        val serviceIntent = Intent(context, ChirkutForegroundService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    } else {
                        repository.log("BootReceiver", "Boot completed, but foreground service is disabled in settings.", "INFO")
                    }
                } catch (e: Exception) {
                    repository.log("BootReceiver", "Failed to restart service on boot: ${e.message}", "ERROR")
                }
            }
        }
    }
}
