package com.example.smsmirror.core

import android.content.Context
import com.example.smsmirror.mail.SmtpConfig
import com.example.smsmirror.security.SecureStore
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Everything the UI needs about configuration and today's tallies. */
data class SettingsState(
    val forwardingEnabled: Boolean = false,
    val forwardAll: Boolean = true,
    val smtp: SmtpConfig = SmtpConfig(),
    val hasPassword: Boolean = false,
    val requireUnlock: Boolean = true,
    val notifyOnFailure: Boolean = true,
    val sentToday: Int = 0,
    val failedToday: Int = 0,
) {
    /** Forwarding can only be armed once there's somewhere to forward to. */
    val isConfigured: Boolean get() = smtp.isComplete && hasPassword
}

/**
 * Preference-backed settings plus the integer counters behind Home's stats row.
 *
 * The counters are the only thing that outlives a message: numbers, never content.
 */
class AppSettings(context: Context, private val secrets: SecureStore) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(snapshot())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    /** Read directly (not via [state]) by the receiver and worker, off the main thread. */
    val forwardingEnabled: Boolean get() = prefs.getBoolean(KEY_ENABLED, false)

    /**
     * Defaults to true so upgrading from the MVP doesn't silently start dropping
     * messages for someone who has no rules yet.
     */
    val forwardAll: Boolean get() = prefs.getBoolean(KEY_FORWARD_ALL, true)

    fun setForwardAll(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FORWARD_ALL, enabled).apply()
        publish()
    }

    /**
     * Defaults on. Safe as a default because the caller also checks that the device can
     * actually authenticate, so a phone with no screen lock is never locked out.
     */
    val requireUnlock: Boolean get() = prefs.getBoolean(KEY_REQUIRE_UNLOCK, true)

    fun setRequireUnlock(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REQUIRE_UNLOCK, enabled).apply()
        publish()
    }

    /**
     * Read by [com.example.smsmirror.work.ForwardWorker] off the main thread. Defaults on:
     * a forward that failed is the one thing worth interrupting someone for, since the
     * whole point is receiving a code you're waiting on.
     */
    val notifyOnFailure: Boolean get() = prefs.getBoolean(KEY_NOTIFY_ON_FAILURE, true)

    fun setNotifyOnFailure(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFY_ON_FAILURE, enabled).apply()
        publish()
    }

    val smtp: SmtpConfig
        get() = SmtpConfig(
            host = prefs.getString(KEY_HOST, SmtpConfig.DEFAULT_HOST).orEmpty(),
            port = prefs.getInt(KEY_PORT, SmtpConfig.DEFAULT_PORT),
            email = prefs.getString(KEY_EMAIL, "").orEmpty(),
        )

    val smtpPassword: String? get() = secrets.smtpPassword

    fun setForwardingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        publish()
    }

    /** A null [password] leaves the stored one untouched, so re-saving host/port is safe. */
    fun saveSmtp(config: SmtpConfig, password: String?) {
        prefs.edit()
            .putString(KEY_HOST, config.host.trim())
            .putInt(KEY_PORT, config.port)
            .putString(KEY_EMAIL, config.email.trim())
            .apply()
        if (!password.isNullOrEmpty()) secrets.smtpPassword = password
        publish()
    }

    fun clearCredentials() {
        secrets.clear()
        prefs.edit().putBoolean(KEY_ENABLED, false).apply()
        publish()
    }

    fun recordSent() = bumpCounter(KEY_SENT)

    fun recordFailed() = bumpCounter(KEY_FAILED)

    /** Call when the process starts so a stale day's numbers aren't shown as today's. */
    fun refresh() = publish()

    // --- counters ---------------------------------------------------------------

    private fun bumpCounter(key: String) {
        synchronized(this) {
            rollDayIfNeeded()
            prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
        }
        publish()
    }

    private fun rollDayIfNeeded() {
        val today = LocalDate.now().toEpochDay()
        if (prefs.getLong(KEY_COUNTER_DAY, 0L) != today) {
            prefs.edit()
                .putLong(KEY_COUNTER_DAY, today)
                .putInt(KEY_SENT, 0)
                .putInt(KEY_FAILED, 0)
                .apply()
        }
    }

    private fun publish() {
        _state.value = snapshot()
    }

    private fun snapshot(): SettingsState {
        rollDayIfNeeded()
        return SettingsState(
            forwardingEnabled = forwardingEnabled,
            forwardAll = forwardAll,
            smtp = smtp,
            hasPassword = secrets.hasPassword,
            requireUnlock = requireUnlock,
            notifyOnFailure = notifyOnFailure,
            sentToday = prefs.getInt(KEY_SENT, 0),
            failedToday = prefs.getInt(KEY_FAILED, 0),
        )
    }

    private companion object {
        const val PREFS = "sms_mirror_settings"
        const val KEY_ENABLED = "forwarding_enabled"
        const val KEY_FORWARD_ALL = "forward_all"
        const val KEY_REQUIRE_UNLOCK = "require_unlock"
        const val KEY_NOTIFY_ON_FAILURE = "notify_on_failure"
        const val KEY_HOST = "smtp_host"
        const val KEY_PORT = "smtp_port"
        const val KEY_EMAIL = "account_email"
        const val KEY_SENT = "counter_sent"
        const val KEY_FAILED = "counter_failed"
        const val KEY_COUNTER_DAY = "counter_day"
    }
}
