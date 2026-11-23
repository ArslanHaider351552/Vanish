package com.example.vanish

/**
 * The relay's clock as seen from this phone.
 * offset = relayTime - phoneTime, re-measured on every hello_ok/pong (midpoint of the round trip).
 * Countdowns use now() so a phone whose clock is minutes wrong still shows the right time left.
 */
class ServerClock(private val prefs: Prefs) {
    @Volatile private var offset: Long = prefs.clockOffset

    fun now(): Long = System.currentTimeMillis() + offset

    fun sync(serverTime: Long, sentAtLocal: Long, receivedAtLocal: Long) {
        offset = serverTime - (sentAtLocal + receivedAtLocal) / 2
        prefs.clockOffset = offset
    }
}
