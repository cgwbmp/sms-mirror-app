package com.example.smsmirror.filter

/**
 * Decides whether an incoming message gets forwarded. Pure and Android-free so the
 * whole matrix is covered by JVM unit tests — this is the one component where a bug
 * either leaks a private message or silently drops the OTP you were waiting for.
 */
object FilterEngine {

    sealed interface Decision {
        data class Forward(val because: String) : Decision
        data class Drop(val because: String) : Decision

        val shouldForward: Boolean get() = this is Forward
    }

    /**
     * Precedence, highest first:
     *  1. a matching block rule — a blocklist the "forward everything" switch could
     *     override wouldn't be a blocklist
     *  2. [forwardAll]
     *  3. any matching allow rule (allow-sender / keyword / regex / OTP)
     *  4. otherwise drop
     */
    fun evaluate(
        sender: String,
        body: String,
        forwardAll: Boolean,
        rules: List<Rule>,
    ): Decision {
        val active = rules.filter { it.enabled && it.isValid }

        active.firstOrNull { it.type == RuleType.SENDER_BLOCK && matchesSender(it.pattern, sender) }
            ?.let { return Decision.Drop("blocked sender ${it.pattern}") }

        if (forwardAll) return Decision.Forward("forwarding everything")

        val allow = active.firstOrNull { rule ->
            when (rule.type) {
                RuleType.SENDER_ALLOW -> matchesSender(rule.pattern, sender)
                RuleType.KEYWORD -> body.contains(rule.pattern, ignoreCase = true)
                RuleType.REGEX -> runCatching { Regex(rule.pattern).containsMatchIn(body) }
                    .getOrDefault(false)
                RuleType.OTP -> looksLikeOtp(body)
                RuleType.SENDER_BLOCK -> false
            }
        }

        return when {
            allow != null -> Decision.Forward(describe(allow))
            active.isEmpty() -> Decision.Drop("no rules configured")
            else -> Decision.Drop("no rule matched")
        }
    }

    /**
     * Phone numbers arrive in whatever format the network felt like — `+15551234567`,
     * `05551234567`, `5551234567` are all the same phone. Compares by digits and lets
     * the shorter one match as a suffix of the longer, which is how national vs
     * international forms differ. Alphanumeric senders ("GOOGLE") compare as text.
     */
    fun matchesSender(pattern: String, sender: String): Boolean {
        val patternDigits = pattern.filter(Char::isDigit)
        val senderDigits = sender.filter(Char::isDigit)

        val numeric = patternDigits.isNotEmpty() &&
            senderDigits.isNotEmpty() &&
            pattern.none { it.isLetter() } &&
            sender.none { it.isLetter() }

        return if (numeric) {
            // Guard against a 2-digit rule matching half the country.
            val shorter = minOf(patternDigits.length, senderDigits.length)
            shorter >= MIN_NUMBER_DIGITS &&
                (senderDigits.endsWith(patternDigits) || patternDigits.endsWith(senderDigits))
        } else {
            pattern.trim().equals(sender.trim(), ignoreCase = true)
        }
    }

    /**
     * Heuristic: a short digit run plus a word suggesting authentication. Requiring
     * both keeps ordinary messages containing a number ("see you at 1900") out, at the
     * cost of missing a bare-code message with no explanatory word.
     */
    fun looksLikeOtp(body: String): Boolean {
        if (!CODE.containsMatchIn(body)) return false
        val lower = body.lowercase()
        return OTP_WORDS.any { lower.contains(it) }
    }

    private fun describe(rule: Rule): String = when (rule.type) {
        RuleType.SENDER_ALLOW -> "sender ${rule.pattern}"
        RuleType.KEYWORD -> "keyword \"${rule.pattern}\""
        RuleType.REGEX -> "regex ${rule.pattern}"
        RuleType.OTP -> "looks like a passcode"
        RuleType.SENDER_BLOCK -> "blocked"
    }

    /** Standalone 4–8 digit run: long enough to be a code, short enough not to be an IBAN. */
    private val CODE = Regex("""(?<!\d)\d{4,8}(?!\d)""")

    private val OTP_WORDS = listOf(
        "code", "otp", "one-time", "one time", "verification", "verify", "verifier",
        "passcode", "password", "pin", "2fa", "token", "authenticat", "login",
        "log in", "sign in", "sign-in", "confirm", "tan",
    )

    private const val MIN_NUMBER_DIGITS = 4
}
