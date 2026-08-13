package com.example.smsmirror.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.smsmirror.core.AppContainer
import com.example.smsmirror.data.OutboxEntity
import com.example.smsmirror.data.RuleEntity
import com.example.smsmirror.filter.FilterEngine
import com.example.smsmirror.work.ForwardWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Catches incoming SMS without being the default SMS app — `RECEIVE_SMS` and this
 * broadcast are enough. The message is never aborted, so the real messaging app still
 * receives and stores it as usual.
 *
 * The receiver does no network work: it writes the message to the durable outbox and
 * hands off to WorkManager. A direct send here would be lost whenever the phone had no
 * signal, and would be killed mid-flight once `onReceive` returned.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val container = AppContainer.get(context)
        if (!container.settings.forwardingEnabled) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val first = messages.first()
        val sender = first.originatingAddress?.takeIf { it.isNotBlank() } ?: "unknown sender"
        // A long SMS arrives as several parts; concatenating them restores the original.
        val body = messages.joinToString(separator = "") { it.displayMessageBody.orEmpty() }
        if (body.isBlank()) return

        val receivedAt = first.timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
        val subscriptionId = intent.getIntExtra(EXTRA_SUBSCRIPTION, -1)
        val receiver = SimInfo.describe(context, subscriptionId)

        val row = OutboxEntity(
            sender = sender,
            receiver = receiver,
            body = body,
            receivedAt = receivedAt,
        )

        // goAsync keeps the broadcast alive across the rule lookup and DB write; without
        // it the process could be killed before the row is committed.
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        scope.launch {
            try {
                // Rules live in the DB, so this has to happen off the main thread —
                // hence inside the coroutine rather than in the early-return block above.
                val rules = container.rules.all().mapNotNull(RuleEntity::toRule)
                val decision = FilterEngine.evaluate(
                    sender = sender,
                    body = body,
                    forwardAll = container.settings.forwardAll,
                    rules = rules,
                )
                if (!decision.shouldForward) {
                    // Log the reason but never the message: a dropped message should
                    // leave no trace, and logcat is readable by adb.
                    Log.d(TAG, "Dropped message from $sender: ${(decision as FilterEngine.Decision.Drop).because}")
                    return@launch
                }
                container.outbox.insert(row)
                ForwardWorker.enqueue(appContext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to queue incoming SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SmsReceiver"

        /** Which SIM took the message. Not in the public API surface, but stable. */
        const val EXTRA_SUBSCRIPTION = "subscription"

        // Outlives any single onReceive call; the receiver instance itself does not.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
