package com.example.smsmirror.mail

import com.example.smsmirror.data.OutboxEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Turns an outbox row into the email that carries it. Pure functions — unit-tested on
 * the JVM, no device needed.
 */
object EmailFormatter {

    private val TIMESTAMP: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")

    fun subject(row: OutboxEntity): String = "SMS from ${row.sender}"

    fun body(row: OutboxEntity, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        appendLine("From      ${row.sender}")
        appendLine("To        ${row.receiver}")
        appendLine("Received  ${formatTimestamp(row.receivedAt, zone)}")
        appendLine()
        appendLine(row.body)
    }

    fun testSubject(): String = "Mirror test"

    fun addressChangeSubject(): String = "Mirror is switching to a different address"

    /**
     * Sent to the *outgoing* address, with the outgoing account's own credentials, before
     * the change is applied. Its purpose is to make a redirection of your messages
     * impossible to do silently — so it names the new destination, which the person making
     * the change already knows anyway.
     */
    fun addressChangeBody(
        previousEmail: String,
        newEmail: String,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = buildString {
        appendLine("Mirror is being set to forward to a different email address.")
        appendLine("This address will stop receiving your forwarded text messages.")
        appendLine()
        appendLine("Was       $previousEmail")
        appendLine("Now       $newEmail")
        appendLine("Changed   ${formatTimestamp(nowMillis, zone)}")
        appendLine()
        appendLine("If you didn't make this change, someone with access to your phone may be")
        appendLine("redirecting your text messages, including one-time passcodes. Open Mirror")
        appendLine("on the phone and check Setup.")
    }

    fun testBody(config: SmtpConfig, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        buildString {
            appendLine("Mirror can reach your inbox.")
            appendLine()
            appendLine("Account   ${config.email}")
            appendLine("Server    ${config.host}:${config.port} (${if (config.useStartTls) "STARTTLS" else "TLS"})")
            appendLine("Sent      ${formatTimestamp(nowMillis, zone)}")
        }

    private fun formatTimestamp(millis: Long, zone: ZoneId): String =
        TIMESTAMP.format(Instant.ofEpochMilli(millis).atZone(zone))
}
