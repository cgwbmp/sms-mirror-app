package com.example.smsmirror.filter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterEngineTest {

    private fun forwards(
        sender: String = "+15551234567",
        body: String = "hello",
        forwardAll: Boolean = false,
        rules: List<Rule> = emptyList(),
    ): Boolean = FilterEngine.evaluate(sender, body, forwardAll, rules).shouldForward

    // --- precedence ---------------------------------------------------------------

    @Test
    fun `forward-all forwards anything`() {
        assertTrue(forwards(forwardAll = true))
    }

    @Test
    fun `no rules and no forward-all drops everything`() {
        assertFalse(forwards())
    }

    @Test
    fun `block rule beats forward-all`() {
        val block = Rule(type = RuleType.SENDER_BLOCK, pattern = "+15551234567")
        assertFalse(forwards(forwardAll = true, rules = listOf(block)))
    }

    @Test
    fun `block rule beats a matching allow rule`() {
        val rules = listOf(
            Rule(id = 1, type = RuleType.SENDER_ALLOW, pattern = "+15551234567"),
            Rule(id = 2, type = RuleType.SENDER_BLOCK, pattern = "+15551234567"),
        )
        assertFalse(forwards(rules = rules))
    }

    @Test
    fun `disabled rules are ignored`() {
        val disabledAllow = Rule(type = RuleType.KEYWORD, pattern = "hello", enabled = false)
        assertFalse(forwards(body = "hello there", rules = listOf(disabledAllow)))

        val disabledBlock = Rule(type = RuleType.SENDER_BLOCK, pattern = "+15551234567", enabled = false)
        assertTrue(forwards(forwardAll = true, rules = listOf(disabledBlock)))
    }

    // --- keyword and regex --------------------------------------------------------

    @Test
    fun `keyword matching ignores case`() {
        val rule = Rule(type = RuleType.KEYWORD, pattern = "Delivery")
        assertTrue(forwards(body = "your delivery is here", rules = listOf(rule)))
        assertFalse(forwards(body = "unrelated message", rules = listOf(rule)))
    }

    @Test
    fun `regex rule matches on the body`() {
        val rule = Rule(type = RuleType.REGEX, pattern = """\bcode:? \d{6}\b""")
        assertTrue(forwards(body = "Your code: 123456 expires soon", rules = listOf(rule)))
        assertFalse(forwards(body = "Your code is coming", rules = listOf(rule)))
    }

    @Test
    fun `an invalid regex never matches instead of crashing`() {
        val broken = Rule(type = RuleType.REGEX, pattern = "([unclosed")
        assertFalse(broken.isValid)
        assertFalse(forwards(body = "([unclosed", rules = listOf(broken)))
    }

    @Test
    fun `a blank pattern never matches`() {
        assertFalse(Rule(type = RuleType.KEYWORD, pattern = "").isValid)
        assertFalse(forwards(body = "anything", rules = listOf(Rule(type = RuleType.KEYWORD, pattern = ""))))
    }

    // --- sender matching ----------------------------------------------------------

    @Test
    fun `national and international forms of a number match`() {
        assertTrue(FilterEngine.matchesSender("+15551234567", "5551234567"))
        assertTrue(FilterEngine.matchesSender("5551234567", "+1 555 123 4567"))
        assertTrue(FilterEngine.matchesSender("+1-555-123-4567", "+15551234567"))
    }

    @Test
    fun `different numbers do not match`() {
        assertFalse(FilterEngine.matchesSender("+15551234567", "+15559999999"))
    }

    @Test
    fun `a too-short numeric pattern cannot match everything`() {
        // Otherwise "7" would forward every number ending in 7.
        assertFalse(FilterEngine.matchesSender("7", "+15551234567"))
    }

    @Test
    fun `alphanumeric sender ids compare as text`() {
        assertTrue(FilterEngine.matchesSender("GOOGLE", "Google"))
        assertFalse(FilterEngine.matchesSender("GOOGLE", "G00gle"))
    }

    // --- OTP heuristic ------------------------------------------------------------

    @Test
    fun `recognises typical passcode messages`() {
        assertTrue(FilterEngine.looksLikeOtp("Your verification code is 123456"))
        assertTrue(FilterEngine.looksLikeOtp("847213 is your one-time password"))
        assertTrue(FilterEngine.looksLikeOtp("Use PIN 4821 to sign in"))
        assertTrue(FilterEngine.looksLikeOtp("G-558212 is your Google verification code"))
    }

    @Test
    fun `ignores ordinary messages containing numbers`() {
        assertFalse(FilterEngine.looksLikeOtp("See you at 1900 by the station"))
        assertFalse(FilterEngine.looksLikeOtp("Your parcel 12345678 was delivered"))
        assertFalse(FilterEngine.looksLikeOtp("Call me back"))
    }

    @Test
    fun `a long digit run is not a passcode`() {
        // Account and card numbers should not trip the OTP rule.
        assertFalse(FilterEngine.looksLikeOtp("Your code relates to account 1234567890123"))
    }

    @Test
    fun `otp rule needs no pattern to be valid`() {
        val rule = Rule(type = RuleType.OTP)
        assertTrue(rule.isValid)
        assertTrue(forwards(body = "Your login code is 998877", rules = listOf(rule)))
        assertFalse(forwards(body = "Lunch tomorrow?", rules = listOf(rule)))
    }
}
