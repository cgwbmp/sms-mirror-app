package com.example.smsmirror.mail

/**
 * Send-to-self configuration: [email] is both FROM and TO, so there is exactly one
 * credential and no separate destination address to mistype.
 */
data class SmtpConfig(
    val host: String = DEFAULT_HOST,
    val port: Int = DEFAULT_PORT,
    val email: String = "",
) {
    /** Port 587 means STARTTLS; anything else is treated as implicit TLS (465). */
    val useStartTls: Boolean get() = port == STARTTLS_PORT

    val isComplete: Boolean
        get() = host.isNotBlank() && port in 1..65535 && email.contains('@')

    companion object {
        /**
         * Blank, not a guess: the host comes from the address's domain via
         * [MailProviders.configFor]. Defaulting to Gmail's server would leave a
         * non-Gmail account looking configured while pointing somewhere wrong.
         */
        const val DEFAULT_HOST = ""
        const val DEFAULT_PORT = 465
        const val STARTTLS_PORT = 587
    }
}
