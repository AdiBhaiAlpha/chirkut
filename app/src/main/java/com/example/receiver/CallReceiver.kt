package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.example.data.AppDatabase
import com.example.data.DataStoreManager
import com.example.data.TelemetryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CallReceiver : BroadcastReceiver() {

    companion object {
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var callStartTime: Long = 0
        private var isIncoming = false
        private var savedNumber: String? = null
    }

    private val receiverScope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val db = AppDatabase.getDatabase(context)
        val dataStoreManager = DataStoreManager(context)
        val repository = TelemetryRepository(context, db.appDao(), dataStoreManager)

        @Suppress("DEPRECATION")
        val actionNewOutgoingCall = Intent.ACTION_NEW_OUTGOING_CALL
        if (intent.action == actionNewOutgoingCall) {
            val number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER) ?: return
            savedNumber = number
            isIncoming = false
            callStartTime = System.currentTimeMillis()
            
            receiverScope.launch {
                val enabled = dataStoreManager.callForward.first()
                if (!enabled) return@launch
                logCall(context, repository, "Outgoing Call (Initiated)", number, 0)
            }
            return
        }

        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
            @Suppress("DEPRECATION")
            val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            
            val state = when (stateStr) {
                TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
                TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
                TelephonyManager.EXTRA_STATE_IDLE -> TelephonyManager.CALL_STATE_IDLE
                else -> TelephonyManager.CALL_STATE_IDLE
            }

            if (number != null) {
                savedNumber = number
            }

            receiverScope.launch {
                val enabled = dataStoreManager.callForward.first()
                if (!enabled) return@launch

                onCallStateChanged(context, repository, state)
            }
        }
    }

    private suspend fun onCallStateChanged(context: Context, repository: TelemetryRepository, state: Int) {
        if (lastState == state) {
            return
        }

        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                isIncoming = true
                callStartTime = System.currentTimeMillis()
                logCall(context, repository, "Incoming Call (Ringing)", savedNumber ?: "Private Number", 0)
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (lastState != TelephonyManager.CALL_STATE_RINGING) {
                    isIncoming = false
                }
                callStartTime = System.currentTimeMillis()
                logCall(context, repository, "Call Answered (Off-Hook)", savedNumber ?: "Private Number", 0)
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                val duration = if (callStartTime > 0) (System.currentTimeMillis() - callStartTime) / 1000 else 0
                val number = savedNumber ?: "Private Number"

                if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                    logCall(context, repository, "Missed / Rejected Call", number, 0)
                } else if (isIncoming) {
                    logCall(context, repository, "Incoming Call Ended", number, duration)
                } else {
                    logCall(context, repository, "Outgoing Call Ended", number, duration)
                }
                
                callStartTime = 0
                savedNumber = null
            }
        }
        lastState = state
    }

    private suspend fun logCall(
        context: Context,
        repository: TelemetryRepository,
        type: String,
        number: String,
        duration: Long
    ) {
        val contactName = getContactName(context, number)
        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val message = """
            📞 *Call Event*
            *Event Type:* $type
            *Number:* $number
            *Contact Name:* $contactName
            *Time:* $timeStr
            *Duration:* ${duration}s
        """.trimIndent()

        repository.log("CallReceiver", "$type: $number ($contactName)", "INFO")
        repository.sendTelegramMessage(message)
    }

    private fun getContactName(context: Context, phoneNumber: String): String {
        try {
            val uri = android.net.Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(phoneNumber)
            )
            val projection = arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(0) ?: "Unknown"
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return "Unknown"
    }
}
