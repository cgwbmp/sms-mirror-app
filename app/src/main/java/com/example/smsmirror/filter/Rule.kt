package com.example.smsmirror.filter

/**
 * What a rule tests. Kept as a stable string in the database rather than an ordinal, so
 * reordering this enum can't silently reinterpret saved rules.
 */
enum class RuleType(val id: String) {
    /** Forward messages from this sender. */
    SENDER_ALLOW("sender_allow"),

    /** Never forward messages from this sender. Wins over everything else. */
    SENDER_BLOCK("sender_block"),

    /** Forward when the body contains this text, case-insensitively. */
    KEYWORD("keyword"),

    /** Forward when the body matches this regular expression. */
    REGEX("regex"),

    /** Forward when the body looks like a one-time passcode. Needs no pattern. */
    OTP("otp"),
    ;

    /** True when the rule carries no user-supplied pattern. */
    val isPatternless: Boolean get() = this == OTP

    companion object {
        fun fromId(id: String): RuleType? = entries.firstOrNull { it.id == id }
    }
}

data class Rule(
    val id: Long = 0,
    val type: RuleType,
    val pattern: String = "",
    val enabled: Boolean = true,
) {
    /** A regex rule the user mistyped can never match; surfaced in the UI as invalid. */
    val isValid: Boolean
        get() = when (type) {
            RuleType.OTP -> true
            RuleType.REGEX -> pattern.isNotBlank() && runCatching { Regex(pattern) }.isSuccess
            else -> pattern.isNotBlank()
        }
}
