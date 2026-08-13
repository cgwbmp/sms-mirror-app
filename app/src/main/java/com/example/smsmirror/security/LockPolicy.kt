package com.example.smsmirror.security

/**
 * Decides whether returning to the app should require authenticating again.
 *
 * Pure and clock-agnostic so the whole matrix is covered by JVM tests. Callers pass
 * `SystemClock.elapsedRealtime()`, deliberately **not** `System.currentTimeMillis()` —
 * wall-clock time can be moved by changing the device clock, which would let someone
 * stretch the grace window at will.
 */
object LockPolicy {

    /**
     * Long enough to fetch a 16-character app password from a browser or password manager
     * and come back, which is the one flow that unavoidably leaves the app mid-task.
     */
    const val DEFAULT_GRACE_MILLIS = 2 * 60 * 1000L

    /**
     * @param backgroundedAtElapsed when the app last went to the background, or null if it
     *   hasn't since being unlocked.
     * @param nowElapsed the current elapsed-realtime reading.
     */
    fun shouldRelock(
        backgroundedAtElapsed: Long?,
        nowElapsed: Long,
        graceMillis: Long = DEFAULT_GRACE_MILLIS,
    ): Boolean {
        if (backgroundedAtElapsed == null) return false
        val awayFor = nowElapsed - backgroundedAtElapsed
        // A negative reading means the counter restarted (a reboot). Re-locking is the
        // safe reading of "we can't tell how long it's been".
        if (awayFor < 0) return true
        return awayFor >= graceMillis
    }
}
