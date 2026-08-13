package com.example.smsmirror.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.smsmirror.R

object Notifications {

    private const val CHANNEL_FAILURES = "forward_failures"
    private const val ID_FAILURE = 1001

    fun createChannels(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_FAILURES,
            "Forwarding problems",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Shown when a message could not be emailed."
        }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /**
     * Notifies about a failed forward. Deliberately says nothing about the message
     * itself — the point of the app is that content doesn't linger, and notifications
     * are visible on the lock screen.
     */
    fun postFailure(context: Context, reason: String) {
        if (!canNotify(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_FAILURES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("A message wasn't forwarded")
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_FAILURE, notification)
    }

    fun clearFailure(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_FAILURE)
    }

    /** POST_NOTIFICATIONS only became a runtime permission in Android 13. */
    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
