package com.example.smsmirror.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.smsmirror.core.SettingsState
import com.example.smsmirror.data.OutboxEntity
import com.example.smsmirror.ui.theme.DataStyle
import com.example.smsmirror.ui.theme.LocalStatusColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

@Composable
fun HomeScreen(
    settings: SettingsState,
    queue: List<OutboxEntity>,
    onToggleForwarding: (Boolean) -> Unit,
    onRetry: (OutboxEntity) -> Unit,
    onDiscard: (OutboxEntity) -> Unit,
    onGoToSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Recomposition trigger: permission state lives outside Compose, so bump a counter
    // when the system dialog returns to force a re-read.
    var permissionEpoch by remember { mutableIntStateOf(0) }
    val smsGranted = remember(permissionEpoch) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED
    }
    // Only a runtime permission from Android 13; below that there is nothing to ask for.
    val notificationsGranted = remember(permissionEpoch) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionEpoch++ }

    // Doze exemption is granted in a system dialog, so it's re-read the same way.
    val batteryExempt = remember(permissionEpoch) {
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    }
    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { permissionEpoch++ }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MasterSwitchCard(
            enabled = settings.forwardingEnabled,
            canEnable = settings.isConfigured && smsGranted,
            onToggle = onToggleForwarding,
        )

        // Inline, dismissible-feeling prompts instead of system dialogs appearing from
        // nowhere — the user sees why before they're asked.
        AnimatedVisibility(visible = !smsGranted) {
            ActionCard(
                title = "Allow SMS access",
                body = "Mirror needs permission to see incoming messages. It never reads your " +
                    "existing conversations.",
                actionLabel = "Grant permission",
                onAction = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.READ_PHONE_NUMBERS,
                        )
                    )
                },
            )
        }

        AnimatedVisibility(visible = !settings.isConfigured) {
            ActionCard(
                title = "Finish setup",
                body = "Add the email account to forward to and send a test message.",
                actionLabel = "Open setup",
                onAction = onGoToSetup,
            )
        }

        AnimatedVisibility(visible = settings.isConfigured && smsGranted && !batteryExempt) {
            ActionCard(
                title = "Allow background sending",
                body = "Android may hold back forwarding while the phone is idle. Exempting " +
                    "Mirror keeps codes arriving while they're still valid.",
                actionLabel = "Allow",
                onAction = {
                    // The direct dialog needs REQUEST_IGNORE_BATTERY_OPTIMIZATIONS; if a
                    // build or OEM blocks it, fall back to the settings list.
                    val direct = Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.fromParts("package", context.packageName, null),
                    )
                    val intent = if (direct.resolveActivity(context.packageManager) != null) {
                        direct
                    } else {
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    }
                    batteryLauncher.launch(intent)
                },
            )
        }

        // Not shown when the user has switched failure notifications off — prompting for a
        // permission they've declined the purpose of is just nagging.
        AnimatedVisibility(visible = smsGranted && settings.notifyOnFailure && !notificationsGranted) {
            ActionCard(
                title = "Turn on notifications",
                body = "So you find out if a message fails to send.",
                actionLabel = "Allow",
                onAction = {
                    permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                },
            )
        }

        if (settings.isConfigured) {
            AccountCard(email = settings.smtp.email)
        }

        CountersCard(sent = settings.sentToday, failed = settings.failedToday)

        AnimatedVisibility(visible = queue.isNotEmpty()) {
            NeedsAttentionCard(queue = queue, onRetry = onRetry, onDiscard = onDiscard)
        }
    }
}

@Composable
private fun MasterSwitchCard(
    enabled: Boolean,
    canEnable: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val active = enabled && canEnable
    val ringColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // A quiet green ring is the at-a-glance "it's running" signal.
            .border(width = if (active) 2.dp else 1.dp, color = ringColor, shape = RoundedCornerShape(24.dp))
    ) {
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // No status line: the switch and the green ring already say whether it's
                // running, and when something blocks it the "Allow SMS access" / "Finish
                // setup" cards immediately below say exactly what.
                Text(
                    text = if (active) "Forwarding on" else "Forwarding off",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    enabled = canEnable || enabled,
                    modifier = Modifier.semantics {
                        stateDescription = if (active) "Forwarding on" else "Forwarding off"
                    },
                )
            }
        }
    }
}

@Composable
private fun AccountCard(email: String) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.MarkEmailRead,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Sending to yourself",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = email, style = DataStyle, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun CountersCard(sent: Int, failed: Int) {
    val status = LocalStatusColors.current
    SectionCard(title = "Today") {
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            StatTile(value = sent, label = "Forwarded", accent = MaterialTheme.colorScheme.primary)
            StatTile(value = failed, label = "Failed", accent = status.failed)
        }
        Text(
            text = "Counts only. Message text is deleted the moment it's sent.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NeedsAttentionCard(
    queue: List<OutboxEntity>,
    onRetry: (OutboxEntity) -> Unit,
    onDiscard: (OutboxEntity) -> Unit,
) {
    SectionCard(title = "Needs attention") {
        queue.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            QueueRow(row = row, onRetry = { onRetry(row) }, onDiscard = { onDiscard(row) })
        }
    }
}

@Composable
private fun QueueRow(row: OutboxEntity, onRetry: () -> Unit, onDiscard: () -> Unit) {
    val status = LocalStatusColors.current
    Column(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Sms,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            // Sender and time only — the body stays out of the UI on purpose.
            Text(
                text = row.sender,
                style = DataStyle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (row.isFailed) {
                StatusChip("Failed", status.failed, status.failedContainer)
            } else {
                StatusChip("Queued", status.queued, status.queuedContainer)
            }
        }
        Text(
            text = TIME_FORMAT.format(Instant.ofEpochMilli(row.receivedAt).atZone(ZoneId.systemDefault())) +
                if (row.attempts > 0) "  ·  ${row.attempts} attempt${if (row.attempts == 1) "" else "s"}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        row.lastError?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = status.failed,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onRetry) { Text("Retry") }
            TextButton(onClick = onDiscard) { Text("Discard") }
        }
    }
}

@Composable
private fun ActionCard(title: String, body: String, actionLabel: String, onAction: () -> Unit) {
    SectionCard(title = title) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onAction) { Text(actionLabel) }
    }
}
