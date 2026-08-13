package com.example.smsmirror.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A message in flight: it arrived but hasn't been emailed yet.
 *
 * This is the *only* place message text is ever persisted, and only until the send
 * succeeds — [OutboxDao.deleteById] runs the moment SMTP accepts it. Failed rows are
 * kept because a retry needs the body.
 */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Originating address of the SMS. */
    val sender: String,
    /** The receiving SIM: its own number, or "SIM <slot> · <carrier>" when unavailable. */
    val receiver: String,
    val body: String,
    val receivedAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
    val state: String = STATE_PENDING,
) {
    val isFailed: Boolean get() = state == STATE_FAILED

    companion object {
        /** Waiting for a send attempt; WorkManager will pick it up. */
        const val STATE_PENDING = "pending"

        /** A send failed in a way retrying won't fix by itself (e.g. bad credentials). */
        const val STATE_FAILED = "failed"
    }
}
