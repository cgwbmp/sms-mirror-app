package com.example.smsmirror.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smsmirror.core.AppContainer
import com.example.smsmirror.core.Notifications
import com.example.smsmirror.core.SettingsState
import com.example.smsmirror.data.OutboxEntity
import com.example.smsmirror.data.RuleEntity
import com.example.smsmirror.filter.Rule
import com.example.smsmirror.filter.RuleType
import com.example.smsmirror.mail.EmailFormatter
import com.example.smsmirror.mail.SmtpConfig
import com.example.smsmirror.security.LockPolicy
import com.example.smsmirror.work.ForwardWorker
import java.net.UnknownHostException
import javax.mail.AuthenticationFailedException
import javax.mail.internet.AddressException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Result of "Send test email", shown inline under the button. */
sealed interface TestState {
    data object Idle : TestState

    /** Warning the outgoing address before a change of destination is allowed. */
    data object NotifyingPrevious : TestState
    data object Sending : TestState
    data object Sent : TestState
    data class Failed(val reason: String) : TestState

    /** True while a network round-trip is in flight, so the button stays disabled. */
    val isBusy: Boolean get() = this is NotifyingPrevious || this is Sending
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = AppContainer.get(application)

    val settings: StateFlow<SettingsState> = container.settings.state

    /** Drives the "Needs attention" list; empties itself as sends succeed. */
    val outbox: StateFlow<List<OutboxEntity>> = container.outbox.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rules: StateFlow<List<Rule>> = container.rules.observeAll()
        .map { entities -> entities.mapNotNull(RuleEntity::toRule) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _testState = MutableStateFlow<TestState>(TestState.Idle)
    val testState: StateFlow<TestState> = _testState.asStateFlow()

    // Lock state lives here rather than in the Activity so rotating the phone doesn't
    // throw up a fresh prompt. False on a cold start: the process beginning means the
    // user has not authenticated yet.
    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private var backgroundedAtElapsed: Long? = null

    fun onUnlocked() {
        _isUnlocked.value = true
        backgroundedAtElapsed = null
    }

    /** Called when the app goes to the background; starts the grace countdown. */
    fun onEnteredBackground(nowElapsed: Long) {
        if (_isUnlocked.value) backgroundedAtElapsed = nowElapsed
    }

    /** Called when the app returns; re-locks if it was away long enough. */
    fun onReturnedToForeground(nowElapsed: Long) {
        if (LockPolicy.shouldRelock(backgroundedAtElapsed, nowElapsed)) {
            _isUnlocked.value = false
        }
        backgroundedAtElapsed = null
    }

    fun setRequireUnlock(enabled: Boolean) = container.settings.setRequireUnlock(enabled)

    fun setNotifyOnFailure(enabled: Boolean) {
        container.settings.setNotifyOnFailure(enabled)
        // Switching it off should also take down anything already showing, otherwise a
        // stale notification sits there contradicting the setting.
        if (!enabled) Notifications.clearFailure(getApplication())
    }

    fun setForwardingEnabled(enabled: Boolean) {
        container.settings.setForwardingEnabled(enabled)
        if (enabled) {
            // Flush anything that queued up while forwarding was off.
            ForwardWorker.enqueue(getApplication())
        }
    }

    /**
     * Validates the form, proves it works, then saves. Passing null for [password] keeps
     * whatever is already in the Keystore.
     *
     * **Nothing is persisted until the test email is accepted**, so a failed attempt can
     * never damage a working configuration.
     *
     * **Changing the destination address is additionally gated**: the outgoing address is
     * notified, using its own credentials, and the change is only applied if that email is
     * accepted too. Otherwise someone with a few seconds on an unlocked phone could
     * redirect every future passcode and leave no trace. Note this does nothing about
     * clearing the app's data from Android settings — see [clearCredentials].
     */
    fun saveAndSendTest(host: String, port: Int, email: String, password: String?) {
        val config = SmtpConfig(host.trim(), port, email.trim())
        val previous = container.settings.smtp
        val previousPassword = container.settings.smtpPassword

        // Nothing is written until the checks below pass, so a rejected change leaves the
        // working configuration exactly as it was.
        if (!config.isComplete) {
            _testState.value = TestState.Failed("Fill in the account address and server first.")
            return
        }

        val redirecting = previous.isComplete &&
            !previousPassword.isNullOrEmpty() &&
            !previous.email.equals(config.email, ignoreCase = true)

        // A password belongs to one account, so a new address needs its own. Reusing the
        // old one would save a configuration that can't sign in, and forwarding would
        // break silently.
        val secret = when {
            redirecting && password.isNullOrEmpty() -> {
                _testState.value =
                    TestState.Failed("Enter the app password for ${config.email} to change the address.")
                return
            }
            else -> password?.takeIf { it.isNotEmpty() } ?: previousPassword
        }
        if (secret.isNullOrEmpty()) {
            _testState.value = TestState.Failed("Enter the app password for this account.")
            return
        }

        val wasConfigured = previous.isComplete && !previousPassword.isNullOrEmpty()

        _testState.value = TestState.Sending
        viewModelScope.launch {
            // 1. Prove the new credentials work before a single value is written. Saving
            // first would mean one mistyped character in an app password overwrites a
            // working one, and forwarding then fails silently on the next message.
            val failure = withContext(Dispatchers.IO) {
                runCatching {
                    container.mailer.send(
                        config = config,
                        password = secret,
                        subject = EmailFormatter.testSubject(),
                        body = EmailFormatter.testBody(config, System.currentTimeMillis()),
                    )
                }.exceptionOrNull()
            }
            if (failure != null) {
                _testState.value = TestState.Failed(
                    describe(failure) +
                        if (wasConfigured) " Your existing settings are unchanged." else ""
                )
                return@launch
            }

            // 2. Only now warn the outgoing address — doing it before the test would cry
            // wolf every time you mistyped your own password.
            if (redirecting) {
                _testState.value = TestState.NotifyingPrevious
                val notified = withContext(Dispatchers.IO) {
                    runCatching {
                        container.mailer.send(
                            config = previous,
                            password = previousPassword!!,
                            subject = EmailFormatter.addressChangeSubject(),
                            body = EmailFormatter.addressChangeBody(
                                previousEmail = previous.email,
                                newEmail = config.email,
                                nowMillis = System.currentTimeMillis(),
                            ),
                        )
                    }.isSuccess
                }
                if (!notified) {
                    _testState.value = TestState.Failed(
                        "Can't change the address right now — ${previous.email} has to be told " +
                            "about it first. Check your internet connection and try again. " +
                            "Your existing settings are unchanged."
                    )
                    return@launch
                }
            }

            // 3. Both sides confirmed, so commit — and only then wake the worker, which
            // reads the stored configuration rather than these local values.
            container.settings.saveSmtp(config, password)
            ForwardWorker.enqueue(getApplication())
            _testState.value = TestState.Sent
        }
    }

    fun retry(row: OutboxEntity) {
        viewModelScope.launch {
            container.outbox.requeue(row.id)
            Notifications.clearFailure(getApplication())
            ForwardWorker.enqueue(getApplication())
        }
    }

    fun retryAll() {
        viewModelScope.launch {
            container.outbox.requeueAll()
            Notifications.clearFailure(getApplication())
            ForwardWorker.enqueue(getApplication())
        }
    }

    /** Drops a message without sending it — the only way content leaves unsent. */
    fun discard(row: OutboxEntity) {
        viewModelScope.launch { container.outbox.discard(row.id) }
    }

    fun setForwardAll(enabled: Boolean) = container.settings.setForwardAll(enabled)

    fun addRule(type: RuleType, pattern: String) {
        viewModelScope.launch {
            container.rules.upsert(
                RuleEntity.from(
                    rule = Rule(type = type, pattern = pattern),
                    createdAt = System.currentTimeMillis(),
                )
            )
        }
    }

    fun setRuleEnabled(rule: Rule, enabled: Boolean) {
        viewModelScope.launch { container.rules.setEnabled(rule.id, enabled) }
    }

    fun deleteRule(rule: Rule) {
        viewModelScope.launch { container.rules.delete(rule.id) }
    }

    /**
     * The deliberate way out of the address-change gate: if the current account is no
     * longer reachable, it can never be notified, and without this the address could never
     * be changed again. Clearing also switches forwarding off, so it can't be used to
     * redirect messages quietly — but it does mean the gate is not a defence against
     * someone determined who has your unlocked phone.
     */
    fun clearCredentials() {
        container.settings.clearCredentials()
        _testState.value = TestState.Idle
    }

    /** SMTP exceptions are technical; these are the four cases that actually happen. */
    private fun describe(e: Throwable): String = when (e) {
        is AuthenticationFailedException ->
            "The server rejected the sign-in. For Gmail, use an App Password, not your normal password."
        is AddressException -> "That doesn't look like a valid email address."
        is UnknownHostException -> "Couldn't reach the server. Check the host name and your connection."
        else -> e.message ?: "Sending failed (${e.javaClass.simpleName})."
    }
}
