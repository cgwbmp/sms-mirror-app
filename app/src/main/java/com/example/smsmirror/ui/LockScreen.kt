package com.example.smsmirror.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.smsmirror.security.AppLock
import com.example.smsmirror.ui.theme.LocalStatusColors

/**
 * Shown in place of the app until the device credential is presented.
 *
 * Cancelling doesn't close the app — being stranded on a screen with a working Unlock
 * button is friendlier than the process vanishing, and it's what other locked apps do.
 */
@Composable
fun LockScreen(
    activity: FragmentActivity,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var error by remember { mutableStateOf<String?>(null) }

    fun authenticate() {
        error = null
        AppLock.prompt(
            activity = activity,
            onSuccess = onUnlocked,
            onError = { error = it },
        )
    }

    // Prompt once when the lock screen appears, so the common case is a single tap of a
    // fingerprint rather than a button press followed by one.
    LaunchedEffect(Unit) { authenticate() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = "Mirror is locked",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = "Messages keep forwarding while it's locked. Unlocking is only needed to " +
                "change the settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalStatusColors.current.failed,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        Button(
            onClick = { authenticate() },
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Text("Unlock")
        }
    }
}
