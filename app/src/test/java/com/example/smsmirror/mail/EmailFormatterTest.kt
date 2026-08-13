package com.example.smsmirror.mail

import com.example.smsmirror.data.OutboxEntity
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailFormatterTest {

    private val utc = ZoneId.of("UTC")

    private val row = OutboxEntity(
        id = 1,
        sender = "+15551234567",
        receiver = "SIM 1 · Testcom",
        body = "Your code is 123456",
        receivedAt = 1_755_100_925_000, // 2025-08-13 16:02:05 UTC
    )

    @Test
    fun `subject names the sender`() {
        assertEquals("SMS from +15551234567", EmailFormatter.subject(row))
    }

    @Test
    fun `body carries sender, receiving SIM, timestamp and text`() {
        val body = EmailFormatter.body(row, utc)

        assertTrue(body.contains("From      +15551234567"))
        assertTrue(body.contains("To        SIM 1 · Testcom"))
        assertTrue(body.contains("2025-08-13 16:02:05"))
        assertTrue(body.contains("Your code is 123456"))
    }

    @Test
    fun `multiline message text survives intact`() {
        val multiline = row.copy(body = "line one\nline two")
        assertTrue(EmailFormatter.body(multiline, utc).contains("line one\nline two"))
    }

    @Test
    fun `address change notice names both addresses and the time`() {
        val body = EmailFormatter.addressChangeBody(
            previousEmail = "old@example.com",
            newEmail = "new@example.org",
            nowMillis = 1_755_100_925_000,
            zone = utc,
        )

        assertTrue(body.contains("Was       old@example.com"))
        assertTrue(body.contains("Now       new@example.org"))
        assertTrue(body.contains("2025-08-13 16:02:05"))
        // The whole point is that a silent redirection is impossible, so it has to say
        // what to do if the change was not yours.
        assertTrue(body.contains("If you didn't make this change"))
    }

    @Test
    fun `test email states which encryption the port implies`() {
        val implicitTls = SmtpConfig(host = "smtp.example.com", port = 465, email = "me@example.com")
        val startTls = implicitTls.copy(port = 587)

        assertTrue(EmailFormatter.testBody(implicitTls, 0L, utc).contains("smtp.example.com:465 (TLS)"))
        assertTrue(EmailFormatter.testBody(startTls, 0L, utc).contains("smtp.example.com:587 (STARTTLS)"))
    }
}
