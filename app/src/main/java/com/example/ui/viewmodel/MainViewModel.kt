package com.example.ui.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.DataStoreManager
import com.example.data.LogEntry
import com.example.data.TelemetryRepository
import com.example.service.ChirkutForegroundService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dataStoreManager = DataStoreManager(application)
    val repository = TelemetryRepository(application, db.appDao(), dataStoreManager)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    val logs: StateFlow<List<LogEntry>> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                repository.allLogs
            } else {
                repository.searchLogs(query)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val telegramToken = dataStoreManager.telegramToken.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val telegramChatId = dataStoreManager.telegramChatId.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val notificationForward = dataStoreManager.notificationForward.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val smsForward = dataStoreManager.smsForward.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val callForward = dataStoreManager.callForward.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val locationSharing = dataStoreManager.locationSharing.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val foregroundServiceEnabled = dataStoreManager.foregroundServiceEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val telegramEnabled = dataStoreManager.telegramEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val markdownEnabled = dataStoreManager.markdownEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    init {
        viewModelScope.launch {
            repository.log("MainViewModel", "Chirkut UI Initialized.", "INFO")
            repository.processOfflineQueue()
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setNotificationForward(enabled: Boolean) {
        viewModelScope.launch { dataStoreManager.updateNotificationForward(enabled) }
    }

    fun setSmsForward(enabled: Boolean) {
        viewModelScope.launch { dataStoreManager.updateSmsForward(enabled) }
    }

    fun setCallForward(enabled: Boolean) {
        viewModelScope.launch { dataStoreManager.updateCallForward(enabled) }
    }

    fun setLocationSharing(enabled: Boolean) {
        viewModelScope.launch { dataStoreManager.updateLocationSharing(enabled) }
    }

    fun setForegroundServiceEnabled(enabled: Boolean) {
        viewModelScope.launch { 
            dataStoreManager.updateForegroundServiceEnabled(enabled)
            toggleForegroundService(enabled)
        }
    }

    fun setTelegramEnabled(enabled: Boolean) {
        viewModelScope.launch { dataStoreManager.updateTelegramEnabled(enabled) }
    }

    fun setMarkdownEnabled(enabled: Boolean) {
        viewModelScope.launch { dataStoreManager.updateMarkdownEnabled(enabled) }
    }

    fun setTelegramToken(token: String) {
        viewModelScope.launch { dataStoreManager.updateTelegramToken(token) }
    }

    fun setTelegramChatId(chatId: String) {
        viewModelScope.launch { dataStoreManager.updateTelegramChatId(chatId) }
    }

    fun sendTestMessage() {
        viewModelScope.launch {
            repository.log("MainViewModel", "Sending test message to Telegram.", "INFO")
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val testMsg = """
                🧪 *Chirkut Test Message*
                *Status:* Active & Configured
                *Version:* 1.0.0
                *Platform:* Android ${Build.VERSION.RELEASE}
                *Time:* $timestamp
                
                This is a manual verification message to confirm your Bot Token and Chat ID parameters are integrated correctly.
            """.trimIndent()
            repository.sendTelegramMessage(testMsg)
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            repository.clearLogs()
            repository.log("MainViewModel", "Local log database cleared.", "INFO")
        }
    }

    fun copyLogsToClipboard() {
        viewModelScope.launch {
            val currentLogs = logs.value
            if (currentLogs.isEmpty()) return@launch

            val text = currentLogs.joinToString("\n") { log ->
                val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
                "[${log.type}] [$time] ${log.tag}: ${log.message}"
            }

            val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Chirkut Logs", text)
            clipboard.setPrimaryClip(clip)
            repository.log("MainViewModel", "Copied logs to clipboard.", "INFO")
        }
    }

    fun exportLogs() {
        viewModelScope.launch {
            val currentLogs = logs.value
            if (currentLogs.isEmpty()) return@launch

            val text = currentLogs.joinToString("\n") { log ->
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
                "[${log.type}] [$time] ${log.tag}: ${log.message}"
            }

            try {
                val context = getApplication<Application>()
                val cacheDir = context.cacheDir
                val file = File(cacheDir, "chirkut_logs.txt")
                FileOutputStream(file).use { out ->
                    out.write(text.toByteArray())
                }

                val authority = "${context.packageName}.fileprovider"
                val uri = FileProvider.getUriForFile(context, authority, file)

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Chirkut Logs")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(intent, "Export Chirkut Logs").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
                repository.log("MainViewModel", "Logs exported successfully.", "INFO")
            } catch (e: Exception) {
                repository.log("MainViewModel", "Failed to export logs: ${e.message}", "ERROR")
            }
        }
    }

    private fun toggleForegroundService(enabled: Boolean) {
        val context = getApplication<Application>()
        val serviceIntent = Intent(context, ChirkutForegroundService::class.java)
        try {
            if (enabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } else {
                context.stopService(serviceIntent)
            }
        } catch (e: Exception) {
            viewModelScope.launch {
                repository.log("MainViewModel", "Failed to start or stop foreground service: ${e.message}", "WARN")
            }
        }
    }
}
