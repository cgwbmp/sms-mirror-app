package com.example.smsmirror.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.smsmirror.BuildConfig
import com.example.smsmirror.core.SettingsState
import com.example.smsmirror.mail.MailProvider
import com.example.smsmirror.mail.MailProviders
import com.example.smsmirror.mail.SmtpSupport
import com.example.smsmirror.security.AppLock
import com.example.smsmirror.security.LockAvailability
import com.example.smsmirror.ui.theme.DataStyle
import com.example.smsmirror.ui.theme.LocalStatusColors

@Composable
fun SetupScreen(
    settings: SettingsState,
    testState: TestState,
    onSaveAndTest: (host: String, port: Int, email: String, password: String?) -> Unit,
    onClearCredentials: () -> Unit,
    onSetRequireUnlock: (Boolean) -> Unit,
    onSetNotifyOnFailure: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var email by rememberSaveable(settings.smtp.email) { mutableStateOf(settings.smtp.email) }
    var host by rememberSaveable(settings.smtp.host) { mutableStateOf(settings.smtp.host) }
    var port by rememberSaveable(settings.smtp.port) { mutableStateOf(settings.smtp.port.toString()) }
    // Left blank when a password is already stored — it can't be read back out of the
    // Keystore for display, and typing nothing means "keep the existing one".
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val provider = remember(email) { MailProviders.forEmail(email) }

    // The form survives leaving the screen (rememberSaveable), so what's on screen can
    // differ from what's actually in use. Without saying so, an edited-but-unsaved
    // address reads as the live configuration.
    val hasUnsavedChanges = email.trim() != settings.smtp.email || password.isNotEmpty()

    // Case-insensitive: recapitalising your own address is not a change of destination.
    val isChangingAddress = settings.isConfigured &&
        email.contains('@') &&
        !email.trim().equals(settings.smtp.email, ignoreCase = true)

    // The address determines the server, so every edit re-derives it. For a known
    // provider that value is also what gets sent, since the fields are read-only.
    fun applyProviderDefaults(newEmail: String) {
        val derived = MailProviders.configFor(newEmail)
        host = derived.host
        port = if (derived.port > 0) derived.port.toString() else ""
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(title = "Account") {
            Text(
                text = "One account does both jobs: messages are sent from it to itself. " +
                    "Sign this inbox in on your other phone to read them there.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    applyProviderDefaults(it)
                },
                label = { Text("Email address") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(if (settings.hasPassword) "App password (saved)" else "App password") },
                placeholder = {
                    Text(
                        if (settings.hasPassword) {
                            "Leave blank to keep the saved one"
                        } else {
                            "16 characters, no spaces"
                        }
                    )
                },
                singleLine = true,
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                ),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Rounded.VisibilityOff
                            } else {
                                Icons.Rounded.Visibility
                            },
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = accountHelpText(provider),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            provider?.appPasswordUrl?.let { url ->
                AppPasswordLink(providerName = provider.displayName, url = url)
            }
        }

        if (provider != null && provider.support != SmtpSupport.APP_PASSWORD) {
            ProviderWarningCard(provider)
        }

        SectionCard(title = "Server") {
            if (provider != null && provider.support != SmtpSupport.REQUIRES_BRIDGE) {
                // Known provider: the address already determines these, so they're shown
                // for confirmation rather than offered as a choice.
                ServerSummary(
                    providerName = provider.displayName,
                    host = provider.host,
                    port = provider.port,
                    startTls = provider.usesStartTls,
                )
            } else if (provider == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("SMTP host") },
                        singleLine = true,
                        modifier = Modifier.weight(2f),
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { new -> port = new.filter { it.isDigit() }.take(5) },
                        label = { Text("Port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = if (email.contains('@')) {
                        "We don't recognise this provider, so the host above is a guess. " +
                            "465 for TLS, 587 for STARTTLS. Unencrypted connections are refused."
                    } else {
                        "465 for TLS, 587 for STARTTLS. Unencrypted connections are refused."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "${provider.displayName} has no SMTP server this phone can reach.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(title = "Test") {
            Text(
                text = "Send a test email before relying on this — it proves the credentials and " +
                    "encryption work.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (hasUnsavedChanges && settings.isConfigured) {
                Text(
                    text = "Not saved yet — forwarding still uses ${settings.smtp.email}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalStatusColors.current.queued,
                )
            }
            // Sets the expectation before the button is pressed, so the extra step and the
            // possible refusal don't look like a malfunction.
            if (isChangingAddress) {
                Text(
                    text = "Changing the address will email ${settings.smtp.email} to say so. " +
                        "Nothing is saved unless both that and the test email go through.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = {
                    onSaveAndTest(
                        host,
                        port.toIntOrNull() ?: 0,
                        email,
                        password.takeIf { it.isNotEmpty() },
                    )
                    password = ""
                },
                enabled = !testState.isBusy,
            ) {
                if (testState.isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Save and send test email")
                }
            }
            TestResultLine(testState)
        }

        SectionCard(title = "Notifications") {
            // Re-read after the system dialog returns; permission state lives outside
            // Compose so it needs a nudge to recompose.
            var permissionEpoch by remember { mutableIntStateOf(0) }
            val granted = remember(permissionEpoch) {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { permissionEpoch++ }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tell me when a forward fails",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (settings.notifyOnFailure && !granted) {
                            "Android is blocking notifications for this app. Allow them in " +
                                "system settings, or turn this off."
                        } else {
                            "A message that couldn't be sent still appears under \"Needs " +
                                "attention\" on Home either way. Notifications never include " +
                                "message text."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (settings.notifyOnFailure && !granted) {
                            LocalStatusColors.current.queued
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Switch(
                    checked = settings.notifyOnFailure,
                    onCheckedChange = { enabled ->
                        onSetNotifyOnFailure(enabled)
                        // Asking only when switched on keeps the prompt tied to an action
                        // the user just took, rather than appearing unprompted.
                        if (enabled && !granted) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
            }
        }

        SectionCard(title = "Security") {
            val lockAvailability = remember { AppLock.availability(context) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Require unlock to open",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = when (lockAvailability) {
                            LockAvailability.AVAILABLE ->
                                "Your fingerprint or screen lock is needed to change these " +
                                    "settings. Forwarding carries on either way."
                            // A toggle that silently does nothing would be worse than an
                            // honest explanation of why it's off.
                            LockAvailability.NO_LOCK_SCREEN ->
                                "Set a screen lock on this phone to use this."
                            LockAvailability.UNAVAILABLE ->
                                "This phone can't offer a lock right now."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.requireUnlock && lockAvailability == LockAvailability.AVAILABLE,
                    onCheckedChange = onSetRequireUnlock,
                    enabled = lockAvailability == LockAvailability.AVAILABLE,
                )
            }
        }

        SectionCard(title = "Privacy") {
            Text(
                text = "The app password is encrypted with a key held in the Android Keystore. " +
                    "A message is stored on this phone only until it's emailed, then deleted — " +
                    "no history, no sent log.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (settings.hasPassword) {
                // Confirmed rather than immediate: this is the one action that switches
                // forwarding off and can't be undone from inside the app — the password
                // can't be read back out of the Keystore, so re-entering it means going
                // to the provider for a new one.
                var confirmingClear by remember { mutableStateOf(false) }

                TextButton(
                    onClick = { confirmingClear = true },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text("Forget saved password", color = MaterialTheme.colorScheme.error)
                }

                if (confirmingClear) {
                    ClearCredentialsDialog(
                        onConfirm = {
                            confirmingClear = false
                            onClearCredentials()
                        },
                        onDismiss = { confirmingClear = false },
                    )
                }
            }
        }

        // Last line on the last screen: not worth a card, but a bug report is unusable
        // without it, and CONTRIBUTING.md tells people to look here for it.
        Text(
            text = "SMS Mirror ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = DataStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Confirms clearing the stored app password.
 *
 * The copy names both consequences, because neither is obvious from the button: it turns
 * forwarding off, and anything still queued stays queued until a working account is set
 * up again. The confirming action says what it does rather than "OK", so a mistaken tap
 * on the destructive button is easy to back out of.
 */
@Composable
private fun ClearCredentialsDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forget the saved password?") },
        text = {
            Text(
                text = "Forwarding switches off, and messages waiting to be sent stay on " +
                    "this phone until you set up an account again.\n\nThe password can't " +
                    "be shown again, so you'll need a new app password from your provider " +
                    "to start forwarding.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Forget password", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Keep it") }
        },
    )
}

/**
 * Guidance for getting a password for this account.
 *
 * A provider that can't work at all gets no app-password advice — following it would be
 * wasted effort — and its [MailProvider.note] is left to [ProviderWarningCard], since
 * printing the same sentence twice reads as a bug.
 */
private fun accountHelpText(provider: MailProvider?): String {
    val generic = "Use a provider app password rather than your main password, so it " +
        "stays scoped to this app and you can revoke it."

    if (provider == null) return generic
    if (provider.support == SmtpSupport.REQUIRES_BRIDGE) return generic

    return buildString {
        append("Use an app password from ${provider.displayName}, never your main password. ")
        append("It's scoped to this app and you can revoke it.")
        // The URL itself is not spelled out here — AppPasswordLink renders it as a tap
        // target instead, which is both easier to hit and easier to read.
        provider.note?.let { append("\n\n$it") }
    }
}

/**
 * Opens the provider's app-password page. A button rather than a raw URL in the body
 * text: a 16-character password has to be created somewhere else and pasted back, so
 * this is the one step worth making a real tap target.
 */
@Composable
private fun AppPasswordLink(providerName: String, url: String) {
    val context = LocalContext.current
    var failed by remember(url) { mutableStateOf(false) }

    Column {
        TextButton(
            onClick = {
                // A device with no browser would otherwise crash on ActivityNotFound.
                failed = runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }.isFailure
            },
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Create an app password in $providerName")
        }
        if (failed) {
            Text(
                text = "Couldn't open a browser. The page is: $url",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Read-only server details for a recognized provider. Kept on screen rather than hidden
 * so the encryption in use stays visible — that reassurance was the point of showing
 * the port at all.
 */
@Composable
private fun ServerSummary(providerName: String, host: String, port: Int, startTls: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = providerName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "$host:$port  ·  ${if (startTls) "STARTTLS" else "TLS"}",
            style = DataStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Set automatically from your email address. Unencrypted connections " +
                "are refused.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Shown for a provider with no SMTP server this device can reach. Informational only — it
 * never disables saving or testing, since the test email is the real authority.
 */
@Composable
private fun ProviderWarningCard(provider: MailProvider) {
    val accent = LocalStatusColors.current.failed

    SectionCard {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "${provider.displayName} can't be used here",
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                )
                provider.note?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TestResultLine(state: TestState) {
    val status = LocalStatusColors.current
    when (state) {
        TestState.Idle, TestState.Sending -> Unit
        // Explains the extra wait — this send has to succeed before anything is saved.
        TestState.NotifyingPrevious -> Text(
            text = "New account works. Letting the current address know about the change…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TestState.Sent -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Test email sent — check the inbox on your other device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        is TestState.Failed -> Text(
            text = state.reason,
            style = MaterialTheme.typography.bodyMedium,
            color = status.failed,
        )
    }
}
