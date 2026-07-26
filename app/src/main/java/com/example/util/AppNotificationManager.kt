package com.example.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity

object AppNotificationManager {

    const val CHANNEL_SERVICE_ID = "chirkut_service_channel"
    const val CHANNEL_ALERTS_ID = "chirkut_alerts_channel"
    const val CHANNEL_GENERAL_ID = "chirkut_general_channel"

    const val FOREGROUND_SERVICE_NOTIFICATION_ID = 1001

    /**
     * Initializes all required notification channels for the application.
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // Service Channel
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                "Chirkut Persistent Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Chirkut background forwarding tasks running securely."
                setShowBadge(false)
            }

            // Alerts Channel
            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS_ID,
                "Chirkut Alert Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Important alerts and status updates for message forwarding."
                enableVibration(true)
            }

            // General Channel
            val generalChannel = NotificationChannel(
                CHANNEL_GENERAL_ID,
                "Chirkut General Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General notifications and activity logs."
            }

            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(alertsChannel)
            manager.createNotificationChannel(generalChannel)
        }
    }

    /**
     * Builds standard persistent notification for ChirkutForegroundService.
     */
    fun buildForegroundNotification(
        context: Context,
        title: String = "Chirkut Background Service",
        contentText: String = "Waiting for new chirkut"
    ): Notification {
        createNotificationChannels(context)

        val notificationIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, CHANNEL_SERVICE_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /**
     * Posts a standard notification to the specified channel.
     */
    fun showNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        channelId: String = CHANNEL_GENERAL_ID
    ) {
        createNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (channelId == CHANNEL_ALERTS_ID) {
            builder.setPriority(NotificationCompat.PRIORITY_HIGH)
        } else {
            builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        try {
            manager?.notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Missing POST_NOTIFICATIONS runtime permission on Android 13+
        }
    }

    /**
     * Cancels an active notification by ID.
     */
    fun cancelNotification(context: Context, notificationId: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.cancel(notificationId)
    }

    /**
     * Cancels all posted notifications.
     */
    fun cancelAllNotifications(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.cancelAll()
    }
}
