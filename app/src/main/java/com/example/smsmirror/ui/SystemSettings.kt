package com.example.smsmirror.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Permissions are granted on the system's App info page rather than through the runtime
 * dialog: the dialog flow crashed on Android 17, and for a sideloaded app SMS access is a
 * restricted setting that has to be unlocked from App info anyway.
 */
fun openAppSettings(context: Context) {
    launchFirstAvailable(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        Intent(Settings.ACTION_SETTINGS),
    )
}

fun openNotificationSettings(context: Context) {
    val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        null
    }
    launchFirstAvailable(
        context,
        notifications,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )
}

private fun launchFirstAvailable(context: Context, vararg intents: Intent?) {
    for (intent in intents.filterNotNull()) {
        try {
            context.startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
            // Try the next, more general page.
        } catch (_: SecurityException) {
        }
    }
}

/**
 * Bumps every time the screen resumes, so permission checks keyed on it are re-read when
 * the user comes back from system settings.
 */
@Composable
fun rememberResumeEpoch(): Int {
    var epoch by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        epoch++
        onPauseOrDispose { }
    }
    return epoch
}
