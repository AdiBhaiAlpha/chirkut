package com.example

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.service.ChirkutForegroundService
import com.example.ui.screens.MainAppScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start Foreground Service automatically if enabled in settings
        startForegroundServiceIfEnabled()

        // Request clean binding/rebinding of NotificationListenerService if already authorized
        requestRebindService()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppScreen(viewModel = viewModel)
                }
            }
        }
    }

    private fun isNotificationListenerServiceEnabled(): Boolean {
        val cn = ComponentName(this, com.example.service.NotificationForwarderService::class.java)
        val flat = android.provider.Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }

    private fun requestRebindService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                if (isNotificationListenerServiceEnabled()) {
                    val componentName = ComponentName(this, com.example.service.NotificationForwarderService::class.java)
                    NotificationListenerService.requestRebind(componentName)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun startForegroundServiceIfEnabled() {
        val intent = Intent(this, ChirkutForegroundService::class.java)
        // By default on first launch we start it to match auto-start behavior
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            // Ignore if permission denied prior to user interaction
        }
    }
}
