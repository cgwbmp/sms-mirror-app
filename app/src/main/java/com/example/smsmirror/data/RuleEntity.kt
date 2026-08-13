package com.example.smsmirror.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.smsmirror.filter.Rule
import com.example.smsmirror.filter.RuleType

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [RuleType.id], not the ordinal — see the note there. */
    val type: String,
    val pattern: String,
    val enabled: Boolean,
    val createdAt: Long,
) {
    /** Rows with a type this build doesn't know are skipped rather than crashing. */
    fun toRule(): Rule? = RuleType.fromId(type)?.let { ruleType ->
        Rule(id = id, type = ruleType, pattern = pattern, enabled = enabled)
    }

    companion object {
        fun from(rule: Rule, createdAt: Long) = RuleEntity(
            id = rule.id,
            type = rule.type.id,
            pattern = rule.pattern,
            enabled = rule.enabled,
            createdAt = createdAt,
        )
    }
}
