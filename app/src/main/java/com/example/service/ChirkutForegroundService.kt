package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.DataStoreManager
import com.example.data.TelemetryRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

class ChirkutForegroundService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var repository: TelemetryRepository
    private lateinit var dataStoreManager: DataStoreManager

    private val channelId = "chirkut_service_channel"
    private val notificationId = 1001

    private var locationJob: Job? = null
    private var queueJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getDatabase(this)
        dataStoreManager = DataStoreManager(this)
        repository = TelemetryRepository(this, db.appDao(), dataStoreManager)

        createNotificationChannel()

        serviceScope.launch {
            repository.log("ForegroundService", "Chirkut Foreground Service started.", "INFO")
        }

        startPeriodicTasks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_SERVICE") {
            stopSelf()
            return START_NOT_STICKY
        }
        
        startForegroundServiceCompat()
        return START_STICKY
    }

    private fun startPeriodicTasks() {
        // Task 1: Periodic Location Sharing (every 10 minutes)
        locationJob = serviceScope.launch {
            while (isActive) {
                try {
                    val locationEnabled = dataStoreManager.locationSharing.first()
                    if (locationEnabled) {
                        fetchAndSendLocation()
                    }
                } catch (e: Exception) {
                    repository.log("Location", "Failed to share location: ${e.message}", "ERROR")
                }
                delay(600 * 1000L) // 10 minutes
            }
        }

        // Task 2: Periodic Offline Queue Retry (every 2 minutes)
        queueJob = serviceScope.launch {
            while (isActive) {
                try {
                    repository.processOfflineQueue()
                } catch (e: Exception) {
                    repository.log("Queue", "Failed to process offline queue: ${e.message}", "ERROR")
                }
                delay(120 * 1000L) // 2 minutes
            }
        }
    }

    private suspend fun fetchAndSendLocation() {
        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            val hasFine = checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasCoarse = checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasFine && !hasCoarse) {
                repository.log("Location", "Location permission not granted for periodic sharing.", "WARN")
                return
            }

            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        serviceScope.launch {
                            val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(location.time))
                            val message = """
                                📍 *Location Update*
                                *Latitude:* ${location.latitude}
                                *Longitude:* ${location.longitude}
                                *Accuracy:* ${location.accuracy}m
                                *Time:* $timeStr
                                [Google Maps](https://maps.google.com/maps?q=${location.latitude},${location.longitude})
                            """.trimIndent()
                            repository.sendTelegramMessage(message)
                        }
                    } else {
                        serviceScope.launch {
                            repository.log("Location", "Fetched location is null.", "WARN")
                        }
                    }
                }
                .addOnFailureListener { e ->
                    serviceScope.launch {
                        repository.log("Location", "Failed to fetch current location: ${e.message}", "ERROR")
                    }
                }
        } catch (e: Exception) {
            repository.log("Location", "Exception in fetchAndSendLocation: ${e.message}", "ERROR")
        }
    }

    private fun startForegroundServiceCompat() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Chirkut Background Service")
            .setContentText("Waiting for new chirkut")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasFine = checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasCoarse = checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasLocation = hasFine || hasCoarse

            var serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            if (hasLocation) {
                serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    // Android 14+ requires precise handling. If datasync is declared, we can use it.
                    startForeground(
                        notificationId,
                        notification,
                        serviceType
                    )
                } else {
                    startForeground(
                        notificationId,
                        notification,
                        serviceType
                    )
                }
            } catch (e: Exception) {
                try {
                    startForeground(
                        notificationId,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } catch (e2: Exception) {
                    try {
                        startForeground(notificationId, notification)
                    } catch (e3: Exception) {
                        // Completely safe fallback
                    }
                }
            }
        } else {
            startForeground(notificationId, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                channelId,
                "Chirkut Persistent Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Chirkut background forwarding tasks running securely."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        locationJob?.cancel()
        queueJob?.cancel()
        serviceJob.cancel()
        
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            val enabled = dataStoreManager.foregroundServiceEnabled.first()
            if (enabled) {
                repository.log("ForegroundService", "Service destroyed, attempting auto-restart.", "WARN")
                val restartIntent = Intent(applicationContext, ChirkutForegroundService::class.java)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(restartIntent)
                    } else {
                        startService(restartIntent)
                    }
                } catch (e: Exception) {
                    repository.log("ForegroundService", "Failed to auto-restart service: ${e.message}", "WARN")
                }
            } else {
                repository.log("ForegroundService", "Service destroyed and will not restart as it was disabled.", "INFO")
            }
        }
    }
}
