package com.example.smsmirror.mail

import java.util.Date
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * The app is its own SMTP client: it talks straight to the mail server over TLS. No
 * Gmail app to launch, no third-party email API in the path.
 *
 * Blocking by design — callers run it on [kotlinx.coroutines.Dispatchers.IO].
 */
class SmtpClient {

    /** @throws javax.mail.MessagingException on any transport or auth failure. */
    fun send(config: SmtpConfig, password: String, subject: String, body: String) {
        val session = Session.getInstance(propertiesFor(config), authenticatorFor(config, password))
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(config.email))
            // Send-to-self: the configured account is also the recipient.
            setRecipient(Message.RecipientType.TO, InternetAddress(config.email))
            setSubject(subject, "UTF-8")
            setText(body, "UTF-8")
            sentDate = Date()
        }
        Transport.send(message)
    }

    private fun propertiesFor(config: SmtpConfig) = Properties().apply {
        put("mail.smtp.host", config.host)
        put("mail.smtp.port", config.port.toString())
        put("mail.smtp.auth", "true")

        if (config.useStartTls) {
            // Plaintext is never acceptable, so require the upgrade rather than
            // merely enabling it — `required` aborts if the server won't do TLS.
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.starttls.required", "true")
        } else {
            put("mail.smtp.ssl.enable", "true")
            put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
            put("mail.smtp.socketFactory.fallback", "false")
        }
        put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3")
        // Guard against a MITM presenting a valid cert for another host.
        put("mail.smtp.ssl.checkserveridentity", "true")

        // Without timeouts a stalled connection would hold the worker open until the
        // system kills it, and the retry would never be scheduled.
        put("mail.smtp.connectiontimeout", TIMEOUT_MS)
        put("mail.smtp.timeout", TIMEOUT_MS)
        put("mail.smtp.writetimeout", TIMEOUT_MS)
    }

    private fun authenticatorFor(config: SmtpConfig, password: String) = object : Authenticator() {
        override fun getPasswordAuthentication() = PasswordAuthentication(config.email, password)
    }

    private companion object {
        const val TIMEOUT_MS = "20000"
    }
}
