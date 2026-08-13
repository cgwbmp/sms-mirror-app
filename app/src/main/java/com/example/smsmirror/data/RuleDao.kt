package com.example.smsmirror.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: RuleEntity): Long

    @Query("SELECT * FROM rules ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<RuleEntity>>

    /** Read by the receiver on the message path, so it must not be a Flow. */
    @Query("SELECT * FROM rules")
    suspend fun all(): List<RuleEntity>

    @Query("UPDATE rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun delete(id: Long)
}
