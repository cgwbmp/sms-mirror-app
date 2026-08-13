package com.example.smsmirror.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {

    @Insert
    suspend fun insert(row: OutboxEntity): Long

    /** Backs the "Needs attention" list on Home — empties itself as sends succeed. */
    @Query("SELECT * FROM outbox ORDER BY receivedAt ASC")
    fun observeAll(): Flow<List<OutboxEntity>>

    /** One worker pass drains up to this many rows; the rest come on the next run. */
    @Query("SELECT * FROM outbox WHERE state = '${OutboxEntity.STATE_PENDING}' ORDER BY receivedAt ASC LIMIT 50")
    suspend fun pending(): List<OutboxEntity>

    @Query("SELECT COUNT(*) FROM outbox WHERE state = '${OutboxEntity.STATE_PENDING}'")
    suspend fun pendingCount(): Int

    /** Called on a successful send: the body must not outlive delivery. */
    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE outbox SET attempts = :attempts, lastError = :error, state = :state WHERE id = :id")
    suspend fun updateAttempt(id: Long, attempts: Int, error: String?, state: String)

    /** Manual Retry from Home: move a failed row back into the queue. */
    @Query(
        "UPDATE outbox SET state = '${OutboxEntity.STATE_PENDING}', lastError = NULL " +
            "WHERE id = :id"
    )
    suspend fun requeue(id: Long)

    @Query("UPDATE outbox SET state = '${OutboxEntity.STATE_PENDING}', lastError = NULL")
    suspend fun requeueAll()

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun discard(id: Long)
}
