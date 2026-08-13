package com.example.smsmirror.work

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.smsmirror.core.AppContainer
import com.example.smsmirror.core.Notifications
import com.example.smsmirror.data.OutboxEntity
import com.example.smsmirror.mail.EmailFormatter
import java.util.concurrent.TimeUnit
import javax.mail.AuthenticationFailedException
import javax.mail.internet.AddressException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Drains the outbox to email. Runs under WorkManager so it survives Doze, offline
 * stretches and process death, and gets exponential backoff for free.
 *
 * On success the row is **deleted immediately** — that deletion is the app's retention
 * policy, not a cleanup detail.
 */
class ForwardWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val container = AppContainer.get(applicationContext)
        val config = container.settings.smtp
        val password = container.settings.smtpPassword

        if (!config.isComplete || password.isNullOrEmpty()) {
            // Nothing to send with. Rows stay queued until Setup is finished.
            Log.w(TAG, "SMTP not configured; leaving ${container.outbox.pendingCount()} row(s) queued")
            return@withContext Result.success()
        }

        var shouldRetry = false

        for (row in container.outbox.pending()) {
            try {
                container.mailer.send(
                    config = config,
                    password = password,
                    subject = EmailFormatter.subject(row),
                    body = EmailFormatter.body(row),
                )
                // Delivered — the body must not outlive it.
                container.outbox.deleteById(row.id)
                container.settings.recordSent()
            } catch (e: AuthenticationFailedException) {
                // Retrying can't fix a wrong app-password; surface it and stop trying.
                markFailed(container, row, "Sign-in to ${config.host} was rejected. Check the app password in Setup.")
                notifyFailure(container, "Email sign-in was rejected. Check the app password in Setup.")
            } catch (e: AddressException) {
                markFailed(container, row, "The account address ${config.email} isn't a valid email address.")
                notifyFailure(container, "The account address in Setup isn't valid.")
            } catch (e: Exception) {
                // Transient (no network, DNS, timeout, server busy): keep it queued.
                Log.w(TAG, "Transient send failure for row ${row.id}", e)
                container.outbox.updateAttempt(
                    id = row.id,
                    attempts = row.attempts + 1,
                    error = e.message ?: e.javaClass.simpleName,
                    state = OutboxEntity.STATE_PENDING,
                )
                shouldRetry = true
            }
        }

        if (shouldRetry) Result.retry() else Result.success()
    }

    /**
     * The failure still lands in Home's "Needs attention" list either way — switching
     * notifications off silences the interruption, it doesn't hide the problem.
     */
    private fun notifyFailure(container: AppContainer, reason: String) {
        if (container.settings.notifyOnFailure) {
            Notifications.postFailure(applicationContext, reason)
        }
    }

    private suspend fun markFailed(container: AppContainer, row: OutboxEntity, reason: String) {
        container.outbox.updateAttempt(
            id = row.id,
            attempts = row.attempts + 1,
            error = reason,
            state = OutboxEntity.STATE_FAILED,
        )
        container.settings.recordFailed()
    }

    companion object {
        private const val TAG = "ForwardWorker"
        private const val WORK_NAME = "forward_outbox"

        /**
         * Unique work, appended rather than replaced: replacing would cancel a send
         * already in flight. Each run drains whatever is queued at that moment.
         */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<ForwardWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
