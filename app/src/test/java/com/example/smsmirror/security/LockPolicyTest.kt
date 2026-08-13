package com.example.smsmirror.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockPolicyTest {

    private val grace = LockPolicy.DEFAULT_GRACE_MILLIS

    @Test
    fun `staying in the app never relocks`() {
        assertFalse(LockPolicy.shouldRelock(backgroundedAtElapsed = null, nowElapsed = 10_000_000))
    }

    @Test
    fun `a short trip away does not relock`() {
        // The app-password copy flow: leave, copy, come straight back.
        assertFalse(LockPolicy.shouldRelock(backgroundedAtElapsed = 1_000, nowElapsed = 1_000 + 30_000))
    }

    @Test
    fun `returning exactly at the grace boundary relocks`() {
        assertTrue(LockPolicy.shouldRelock(backgroundedAtElapsed = 1_000, nowElapsed = 1_000 + grace))
    }

    @Test
    fun `a long absence relocks`() {
        assertTrue(LockPolicy.shouldRelock(backgroundedAtElapsed = 1_000, nowElapsed = 1_000 + grace * 10))
    }

    @Test
    fun `a counter that went backwards relocks rather than trusting it`() {
        // elapsedRealtime restarts at reboot; "can't tell how long" must mean "lock".
        assertTrue(LockPolicy.shouldRelock(backgroundedAtElapsed = 9_000_000, nowElapsed = 5_000))
    }

    @Test
    fun `a custom grace is honoured`() {
        assertFalse(LockPolicy.shouldRelock(0, 4_000, graceMillis = 5_000))
        assertTrue(LockPolicy.shouldRelock(0, 6_000, graceMillis = 5_000))
    }
}
