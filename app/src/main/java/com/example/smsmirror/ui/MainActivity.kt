package com.example.smsmirror.ui

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smsmirror.security.AppLock
import com.example.smsmirror.security.LockAvailability
import com.example.smsmirror.ui.theme.SmsMirrorTheme

/**
 * A FragmentActivity rather than a ComponentActivity because androidx's BiometricPrompt
 * requires one. It extends ComponentActivity, so Compose setup is unaffected.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keeps the account address and queued sender numbers out of screenshots and the
        // recent-apps thumbnail.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            SmsMirrorTheme {
                SmsMirrorRoot(activity = this)
            }
        }
    }
}

private enum class Destination(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Rounded.Home),
    Rules("Rules", Icons.Rounded.FilterAlt),
    Setup("Setup", Icons.Rounded.Tune),
}

@Composable
private fun SmsMirrorRoot(activity: FragmentActivity, viewModel: MainViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val queue by viewModel.outbox.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val testState by viewModel.testState.collectAsStateWithLifecycle()
    val isUnlocked by viewModel.isUnlocked.collectAsStateWithLifecycle()

    val lockAvailability = remember { AppLock.availability(activity) }
    // Nothing worth locking before there are credentials to protect, so a first run isn't
    // interrupted by a prompt that guards nothing.
    val lockEnabled = settings.requireUnlock &&
        settings.isConfigured &&
        lockAvailability == LockAvailability.AVAILABLE

    // Start the grace countdown on leaving, and re-lock on return if it expired.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onEnteredBackground(SystemClock.elapsedRealtime())
                Lifecycle.Event.ON_START -> viewModel.onReturnedToForeground(SystemClock.elapsedRealtime())
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (lockEnabled && !isUnlocked) {
        LockScreen(activity = activity, onUnlocked = viewModel::onUnlocked)
        return
    }

    var destination by rememberSaveable { mutableStateOf(Destination.Home) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Destination.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = destination == entry,
                        onClick = { destination = entry },
                        icon = { Icon(entry.icon, contentDescription = null) },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier.fillMaxSize().padding(innerPadding)
        when (destination) {
            Destination.Home -> HomeScreen(
                settings = settings,
                queue = queue,
                onToggleForwarding = viewModel::setForwardingEnabled,
                onRetry = viewModel::retry,
                onDiscard = viewModel::discard,
                onGoToSetup = { destination = Destination.Setup },
                modifier = contentModifier,
            )

            Destination.Rules -> RulesScreen(
                forwardAll = settings.forwardAll,
                rules = rules,
                onSetForwardAll = viewModel::setForwardAll,
                onToggleRule = viewModel::setRuleEnabled,
                onDeleteRule = viewModel::deleteRule,
                onAddRule = viewModel::addRule,
                modifier = contentModifier,
            )

            Destination.Setup -> SetupScreen(
                settings = settings,
                testState = testState,
                onSaveAndTest = viewModel::saveAndSendTest,
                onClearCredentials = viewModel::clearCredentials,
                onSetRequireUnlock = viewModel::setRequireUnlock,
                onSetNotifyOnFailure = viewModel::setNotifyOnFailure,
                modifier = contentModifier,
            )
        }
    }
}
