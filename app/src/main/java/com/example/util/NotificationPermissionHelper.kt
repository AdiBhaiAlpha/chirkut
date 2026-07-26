package com.example.util

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import com.example.service.NotificationForwarderService
import kotlin.system.exitProcess

object NotificationPermissionHelper {

    /**
     * Checks if notification listener access (notification read permission) is granted for NotificationForwarderService.
     */
    fun isNotificationListenerGranted(context: Context): Boolean {
        val cn = ComponentName(context, NotificationForwarderService::class.java)
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }

    /**
     * Opens system Notification Access / Listener settings screen so the user can grant permission.
     */
    fun openNotificationListenerSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) { }
        }
    }

    /**
     * Terminates the app process completely if user refuses or chooses to exit.
     */
    fun terminateApp(activity: Activity) {
        try {
            activity.finishAndRemoveTask()
        } catch (e: Exception) {
            try {
                activity.finish()
            } catch (_: Exception) { }
        }
        Process.killProcess(Process.myPid())
        exitProcess(0)
    }
}
