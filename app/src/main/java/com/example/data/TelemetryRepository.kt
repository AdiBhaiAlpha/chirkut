package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class TelemetryRepository(
    private val context: Context,
    private val appDao: AppDao,
    private val dataStoreManager: DataStoreManager
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val allLogs: Flow<List<LogEntry>> = appDao.getAllLogs()

    fun searchLogs(query: String): Flow<List<LogEntry>> {
        return appDao.searchLogs("%$query%")
    }

    suspend fun log(tag: String, message: String, type: String = "INFO") {
        withContext(Dispatchers.IO) {
            val entry = LogEntry(tag = tag, message = message, type = type)
            appDao.insertLog(entry)
            Log.d(tag, "[$type] $message")
        }
    }

    suspend fun clearLogs() {
        withContext(Dispatchers.IO) {
            appDao.clearLogs()
        }
    }

    suspend fun sendTelegramMessage(text: String, isRetry: Boolean = false) {
        withContext(Dispatchers.IO) {
            val enabled = dataStoreManager.telegramEnabled.first()
            if (!enabled) {
                log("Telemetry", "Telegram forwarding is disabled. Message ignored.", "WARN")
                return@withContext
            }

            val token = dataStoreManager.telegramToken.first()
            val chatId = dataStoreManager.telegramChatId.first()

            if (token.isBlank() || chatId.isBlank()) {
                log("Telemetry", "Telegram configuration is incomplete. Saved offline.", "WARN")
                if (!isRetry) {
                    appDao.insertFailedMessage(FailedMessage(messageText = text))
                }
                return@withContext
            }

            val markdownEnabled = dataStoreManager.markdownEnabled.first()

            try {
                val url = "https://api.telegram.org/bot$token/sendMessage"
                val json = JSONObject().apply {
                    put("chat_id", chatId)
                    put("text", text)
                    if (markdownEnabled) {
                        put("parse_mode", "Markdown")
                    }
                }

                val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        log("Telemetry", "Message sent successfully to Telegram.", "SENT")
                        if (!isRetry) {
                            // Process offline queue on successful send
                            processOfflineQueue()
                        }
                    } else {
                        val errorBody = response.body?.string() ?: "No body"
                        log("Telemetry", "Failed to send to Telegram (HTTP ${response.code}): $errorBody", "ERROR")
                        if (!isRetry) {
                            appDao.insertFailedMessage(FailedMessage(messageText = text))
                        }
                    }
                }
            } catch (e: Exception) {
                log("Telemetry", "Error sending message: ${e.message}", "ERROR")
                if (!isRetry) {
                    appDao.insertFailedMessage(FailedMessage(messageText = text))
                }
            }
        }
    }

    suspend fun processOfflineQueue() {
        withContext(Dispatchers.IO) {
            val failed = appDao.getFailedMessages()
            if (failed.isEmpty()) return@withContext

            log("Telemetry", "Processing offline queue: ${failed.size} pending messages", "INFO")
            for (msg in failed) {
                val token = dataStoreManager.telegramToken.first()
                val chatId = dataStoreManager.telegramChatId.first()
                val markdownEnabled = dataStoreManager.markdownEnabled.first()

                try {
                    val url = "https://api.telegram.org/bot$token/sendMessage"
                    val json = JSONObject().apply {
                        put("chat_id", chatId)
                        put("text", msg.messageText)
                        if (markdownEnabled) {
                            put("parse_mode", "Markdown")
                        }
                    }

                    val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                    val request = Request.Builder().url(url).post(body).build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            appDao.deleteFailedMessage(msg)
                            log("Telemetry", "Offline message sent and deleted from queue.", "SENT")
                        } else {
                            log("Telemetry", "Retry failed (HTTP ${response.code}). Retaining in queue.", "WARN")
                            appDao.updateFailedMessage(msg.copy(retryCount = msg.retryCount + 1))
                        }
                    }
                } catch (e: Exception) {
                    log("Telemetry", "Network error during offline queue retry: ${e.message}", "WARN")
                    break
                }
            }
        }
    }
}
