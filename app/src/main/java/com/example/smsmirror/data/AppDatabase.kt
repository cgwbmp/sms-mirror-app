package com.example.smsmirror.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Holds the in-flight outbox and the forwarding rules. There is no sent-message table
 * by design — see the retention decision in IMPLEMENTATION_PLAN.md.
 */
@Database(
    entities = [OutboxEntity::class, RuleEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun outbox(): OutboxDao

    abstract fun rules(): RuleDao

    companion object {
        /**
         * A real migration rather than destructive fallback: the outbox can be holding a
         * message that hasn't been emailed yet, and dropping the table would lose it.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `rules` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`pattern` TEXT NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "sms_mirror.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
