package com.example

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.service.ChirkutForegroundService
import com.example.ui.components.MandatoryNotificationPermissionDialog
import com.example.ui.components.MandatorySmsPermissionDialog
import com.example.ui.components.MandatoryCallPermissionDialog
import com.example.ui.screens.MainAppScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.util.AppNotificationManager
import com.example.util.CallPermissionHelper
import com.example.util.NotificationPermissionHelper
import com.example.util.SmsPermissionHelper

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var isNotificationPermissionGranted by mutableStateOf(false)
    private var isSmsPermissionGranted by mutableStateOf(false)
    private var isCallPermissionGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Create notification channels
        AppNotificationManager.createNotificationChannels(this)

        // Initial check for permissions
        checkPermissions()

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
                    val smsPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions()
                    ) { permissions ->
                        checkPermissions()
                        val allGranted = permissions.values.all { it }
                        if (!allGranted) {
                            SmsPermissionHelper.openAppSettings(this)
                        }
                    }

                    val callPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions()
                    ) { permissions ->
                        checkPermissions()
                        val allGranted = permissions.values.all { it }
                        if (!allGranted) {
                            CallPermissionHelper.openAppSettings(this)
                        }
                    }

                    if (!isNotificationPermissionGranted) {
                        MandatoryNotificationPermissionDialog(
                            onAllowNowClick = {
                                NotificationPermissionHelper.openNotificationListenerSettings(this)
                            },
                            onExitClick = {
                                NotificationPermissionHelper.terminateApp(this)
                            }
                        )
                    } else if (!isSmsPermissionGranted) {
                        MandatorySmsPermissionDialog(
                            onAllowNowClick = {
                                smsPermissionLauncher.launch(SmsPermissionHelper.REQUIRED_SMS_PERMISSIONS)
                            },
                            onExitClick = {
                                NotificationPermissionHelper.terminateApp(this)
                            }
                        )
                    } else if (!isCallPermissionGranted) {
                        MandatoryCallPermissionDialog(
                            onAllowNowClick = {
                                callPermissionLauncher.launch(CallPermissionHelper.REQUIRED_CALL_PERMISSIONS)
                            },
                            onExitClick = {
                                NotificationPermissionHelper.terminateApp(this)
                            }
                        )
                    } else {
                        MainAppScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
        if (isNotificationPermissionGranted) {
            requestRebindService()
        }
    }

    private fun checkPermissions() {
        isNotificationPermissionGranted = NotificationPermissionHelper.isNotificationListenerGranted(this)
        isSmsPermissionGranted = SmsPermissionHelper.hasSmsPermissions(this)
        isCallPermissionGranted = CallPermissionHelper.hasCallPermissions(this)
    }

    private fun isNotificationListenerServiceEnabled(): Boolean {
        return NotificationPermissionHelper.isNotificationListenerGranted(this)
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


