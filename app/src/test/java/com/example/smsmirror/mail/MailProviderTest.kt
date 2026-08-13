package com.example.smsmirror.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MailProviderTest {

    // --- domain extraction --------------------------------------------------------

    @Test
    fun `domain is lowercased and trimmed`() {
        assertEquals("gmail.com", MailProviders.domainOf("  example@GMAIL.com  "))
    }

    @Test
    fun `plus addressing does not affect the domain`() {
        assertEquals("gmail.com", MailProviders.domainOf("example+sms@gmail.com"))
    }

    @Test
    fun `the last at sign wins`() {
        // Quoted local parts may legally contain '@'; mail servers split on the last one.
        assertEquals("fastmail.com", MailProviders.domainOf("\"odd@name\"@fastmail.com"))
    }

    @Test
    fun `an address without a domain resolves to null instead of throwing`() {
        assertNull(MailProviders.domainOf("not-an-email"))
        assertNull(MailProviders.domainOf(""))
        assertNull(MailProviders.domainOf("trailing@"))
        assertNull(MailProviders.forEmail("not-an-email"))
    }

    // --- known providers ----------------------------------------------------------

    @Test
    fun `gmail resolves to its server on both domains`() {
        listOf("me@gmail.com", "me@googlemail.com").forEach { address ->
            val config = MailProviders.configFor(address)
            assertEquals("smtp.gmail.com", config.host)
            assertEquals(465, config.port)
            assertTrue("465 should be implicit TLS", !config.useStartTls)
        }
    }

    @Test
    fun `icloud uses starttls on 587`() {
        val config = MailProviders.configFor("me@icloud.com")
        assertEquals("smtp.mail.me.com", config.host)
        assertEquals(587, config.port)
        assertTrue("587 should be STARTTLS", config.useStartTls)
    }

    @Test
    fun `case and whitespace do not prevent a match`() {
        assertNotNull(MailProviders.forEmail("  Me@Yahoo.COM "))
        assertEquals("smtp.mail.yahoo.com", MailProviders.configFor(" Me@Yahoo.COM ").host)
    }

    @Test
    fun `the resolved config keeps the address as both sender and recipient`() {
        assertEquals("me@fastmail.com", MailProviders.configFor("me@fastmail.com").email)
    }

    // --- unknown providers --------------------------------------------------------

    @Test
    fun `an unknown domain guesses the smtp subdomain`() {
        val config = MailProviders.configFor("me@example.org")
        assertEquals("smtp.example.org", config.host)
        assertEquals(SmtpConfig.DEFAULT_PORT, config.port)
    }

    @Test
    fun `an address with no domain yields no host to guess`() {
        assertEquals("", MailProviders.configFor("nonsense").host)
    }

    // --- support status -----------------------------------------------------------

    @Test
    fun `outlook is usable with an app password and offers the link`() {
        // Microsoft requires modern auth generally but documents app passwords as the
        // exception for apps that can't do OAuth — which is this one.
        val outlook = MailProviders.forEmail("me@hotmail.com")
        assertEquals(SmtpSupport.APP_PASSWORD, outlook?.support)
        assertNotNull("Outlook should link to Microsoft's app-password page", outlook?.appPasswordUrl)
    }

    @Test
    fun `proton is flagged as needing a bridge and offers no host`() {
        val proton = MailProviders.forEmail("me@proton.me")
        assertEquals(SmtpSupport.REQUIRES_BRIDGE, proton?.support)
        assertEquals("", proton?.host)
        // Nothing should be prefilled that would only produce a connection error.
        assertEquals("", MailProviders.configFor("me@proton.me").host)
    }

    // --- table integrity ----------------------------------------------------------

    @Test
    fun `every sendable provider has a usable host and port`() {
        MailProviders.all()
            .filter { it.support != SmtpSupport.REQUIRES_BRIDGE }
            .forEach { provider ->
                assertTrue("${provider.displayName} has a blank host", provider.host.isNotBlank())
                assertTrue("${provider.displayName} has an invalid port", provider.port in 1..65535)
            }
    }

    @Test
    fun `help links are https`() {
        // Odd to ship a plaintext help link from an app that refuses plaintext SMTP.
        MailProviders.all().mapNotNull { it.appPasswordUrl }.forEach { url ->
            assertTrue("Not an https link: $url", url.startsWith("https://"))
        }
    }

    @Test
    fun `a provider that cannot send offers no app-password link`() {
        MailProviders.all()
            .filter { it.support == SmtpSupport.REQUIRES_BRIDGE }
            .forEach { provider ->
                assertNull(
                    "${provider.displayName} can't send, so an app password is pointless",
                    provider.appPasswordUrl,
                )
            }
    }

    @Test
    fun `domains are stored lowercase so matching cannot silently fail`() {
        MailProviders.all().forEach { provider ->
            provider.domains.forEach { domain ->
                assertEquals("${provider.displayName} has a non-lowercase domain", domain.lowercase(), domain)
            }
        }
    }

    @Test
    fun `no domain is claimed by two providers`() {
        // A duplicate would be shadowed by whichever entry comes first in the list.
        val all = MailProviders.all().flatMap { it.domains }
        val duplicates = all.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertTrue("Domains claimed more than once: $duplicates", duplicates.isEmpty())
    }
}
