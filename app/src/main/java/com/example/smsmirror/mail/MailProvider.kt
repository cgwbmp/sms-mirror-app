package com.example.smsmirror.mail

/** Whether a provider lets an app sign in to SMTP with a password. */
enum class SmtpSupport {
    /** Works with a provider-issued app password. */
    APP_PASSWORD,

    /** No reachable SMTP endpoint at all — needs a desktop bridge. */
    REQUIRES_BRIDGE,
}

data class MailProvider(
    val displayName: String,
    val domains: Set<String>,
    val host: String,
    val port: Int,
    val support: SmtpSupport,
    /** Where the user creates an app password, when the provider issues them. */
    val appPasswordUrl: String? = null,
    /** Provider-specific gotcha shown under the account fields. */
    val note: String? = null,
) {
    val usesStartTls: Boolean get() = port == SmtpConfig.STARTTLS_PORT
}

/**
 * Maps an email address to its SMTP server, so the user never types a host or port for a
 * provider we already know.
 *
 * These are **best-known defaults, not guarantees** — providers change SMTP policy, and
 * business or legacy variants of the same domain can behave differently. Anything here
 * is a starting point that "Send test email" then confirms for real.
 */
object MailProviders {

    private val PROVIDERS = listOf(
        MailProvider(
            displayName = "Gmail",
            domains = setOf("gmail.com", "googlemail.com"),
            host = "smtp.gmail.com",
            port = 465,
            support = SmtpSupport.APP_PASSWORD,
            appPasswordUrl = "https://myaccount.google.com/apppasswords",
            note = "App passwords require 2-Step Verification to be switched on first.",
        ),
        MailProvider(
            displayName = "iCloud Mail",
            domains = setOf("icloud.com", "me.com", "mac.com"),
            host = "smtp.mail.me.com",
            port = 587,
            support = SmtpSupport.APP_PASSWORD,
            appPasswordUrl = "https://account.apple.com/account/manage",
            note = "Sign in with your full Apple ID. iCloud rejects mail sent from any " +
                "address other than the account's own.",
        ),
        MailProvider(
            displayName = "Yahoo Mail",
            domains = setOf("yahoo.com", "yahoo.co.uk", "yahoo.co.jp", "ymail.com", "rocketmail.com"),
            host = "smtp.mail.yahoo.com",
            port = 465,
            support = SmtpSupport.APP_PASSWORD,
            appPasswordUrl = "https://login.yahoo.com/account/security",
        ),
        MailProvider(
            displayName = "Fastmail",
            domains = setOf("fastmail.com", "fastmail.fm"),
            host = "smtp.fastmail.com",
            port = 465,
            support = SmtpSupport.APP_PASSWORD,
            appPasswordUrl = "https://app.fastmail.com/settings/security/apps",
        ),
        MailProvider(
            displayName = "Zoho Mail",
            domains = setOf("zoho.com", "zohomail.com"),
            host = "smtp.zoho.com",
            port = 465,
            support = SmtpSupport.APP_PASSWORD,
            appPasswordUrl = "https://accounts.zoho.com/home#security/device",
        ),
        MailProvider(
            displayName = "GMX",
            domains = setOf("gmx.com", "gmx.net", "gmx.de", "gmx.co.uk"),
            host = "mail.gmx.com",
            port = 465,
            support = SmtpSupport.APP_PASSWORD,
            note = "GMX requires POP/IMAP access to be enabled in your account settings.",
        ),
        MailProvider(
            displayName = "AOL Mail",
            domains = setOf("aol.com"),
            host = "smtp.aol.com",
            port = 465,
            support = SmtpSupport.APP_PASSWORD,
            appPasswordUrl = "https://login.aol.com/account/security",
        ),
        MailProvider(
            displayName = "Outlook.com",
            domains = setOf("outlook.com", "hotmail.com", "live.com", "msn.com", "outlook.co.uk"),
            host = "smtp-mail.outlook.com",
            port = 587,
            support = SmtpSupport.APP_PASSWORD,
            // Microsoft's own redirector rather than its current destination
            // (account.live.com/proofs/AppPassword), so the link survives them moving
            // the page.
            appPasswordUrl = "https://go.microsoft.com/fwlink/?linkid=2274139",
            note = "Outlook.com requires modern authentication in general, and an app " +
                "password is Microsoft's documented exception for apps that can't do " +
                "OAuth. Turn on two-step verification first, then create one under " +
                "Advanced security options.",
        ),
        MailProvider(
            displayName = "Proton Mail",
            domains = setOf("proton.me", "protonmail.com", "protonmail.ch", "pm.me"),
            // Bridge listens on desktop loopback, which a phone cannot reach. Left blank
            // deliberately: prefilling a host here would only produce a connection error.
            host = "",
            port = 0,
            support = SmtpSupport.REQUIRES_BRIDGE,
            note = "Proton has no directly reachable SMTP server. It requires Proton " +
                "Bridge, which runs on a desktop computer, so this phone can't send " +
                "through it.",
        ),
    )

    /** The provider owning this address's domain, or null when unrecognized. */
    fun forEmail(email: String): MailProvider? {
        val domain = domainOf(email) ?: return null
        return PROVIDERS.firstOrNull { domain in it.domains }
    }

    /**
     * Server settings for an address. Known domains come from the table; unknown ones get
     * the widespread `smtp.<domain>` convention as a starting guess, which the user can
     * then edit.
     */
    fun configFor(email: String): SmtpConfig {
        val normalized = email.trim()
        val known = forEmail(normalized)
        if (known != null) {
            return SmtpConfig(host = known.host, port = known.port, email = normalized)
        }
        val domain = domainOf(normalized)
            ?: return SmtpConfig(host = "", port = SmtpConfig.DEFAULT_PORT, email = normalized)
        return SmtpConfig(host = "smtp.$domain", port = SmtpConfig.DEFAULT_PORT, email = normalized)
    }

    /**
     * The domain part, lowercased. Takes everything after the *last* `@`, which is what
     * mail servers do; plus-addressing needs no special handling because `+` lives in the
     * local part.
     */
    fun domainOf(email: String): String? {
        val at = email.trim().lastIndexOf('@')
        if (at < 0) return null
        return email.trim().substring(at + 1).lowercase().takeIf { it.isNotBlank() }
    }

    /** Exposed for the table-integrity test. */
    internal fun all(): List<MailProvider> = PROVIDERS
}
