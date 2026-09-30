package com.example.ui.components

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.NotificationPermissionHelper

@Composable
fun MandatorySmsPermissionDialog(
    onAllowNowClick: () -> Unit,
    onExitClick: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Prevent bypassing via back button - exit app if back is pressed
    BackHandler {
        if (activity != null) {
            NotificationPermissionHelper.terminateApp(activity)
        } else {
            onExitClick()
        }
    }

    AlertDialog(
        onDismissRequest = {
            // Non-dismissable by tapping outside
        },
        icon = {
            Icon(
                imageVector = Icons.Default.Sms,
                contentDescription = "SMS Permission Required",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = "এসএমএস পারমিশন প্রয়োজন (SMS Permission Required)",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "এসএমএস পড়ার পারমিশন দেওয়া লাগবে, তা না হলে অ্যাপ ঠিকভাবে কাজ করতে পারবে না।",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "SMS read permission is required, otherwise the app cannot work properly.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAllowNowClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Allow Now (এখনই পারমিশন দিন)")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onExitClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Exit (অ্যাপ থেকে বের হন)")
            }
        },
        modifier = Modifier.padding(16.dp)
    )
}
