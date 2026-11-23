
package com.example.vanish

object Protocol {
    const val VERSION = 1
    const val TTL_MS = 30_000L
    const val MAX_QUEUE_AGE_MS = TTL_MS
    const val MAX_TEXT = 500
    const val MAX_NAME = 30

    const val PING_MS = 15_000L
    const val PONG_TIMEOUT_MS = 30_000L

    const val SEEN_GRACE_MS = 60_000L

    const val DEFAULT_RELAY_URL =
        "wss://vanish-relay-sawaira.bonto.page/ws"
}

// App -> relay
data class Join(
    val protocol: Int,
    val device_id: String,
    val name: String,
    val type: String = "join"
)

data class OutgoingMessage(
    val id: String,
    val sender: String,
    val text: String,
    val type: String = "message"
)

data class Ping(
    val type: String = "ping"
)

// Relay -> app
data class Incoming(
    val type: String? = null,
    val protocol: Int? = null,
    val id: String? = null,
    val sender: String? = null,
    val text: String? = null,
    val sent_at: Long? = null,
    val expires_at: Long? = null,
    val server_time: Long? = null,
    val count: Int? = null,
    val members: List<String>? = null,
    val code: String? = null,
    val message: String? = null
)
