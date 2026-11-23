
package com.example.vanish

import com.example.vanish.data.ChatDao
import com.example.vanish.data.MessageEntity
import com.example.vanish.data.OutboxEntity
import com.example.vanish.data.SeenEntity
import com.example.vanish.net.ConnState
import com.example.vanish.net.RelayClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Protocol v1 message lifecycle:
 * waiting -> sent -> delivered to joined apps -> expired
 *
 * Offline outbox messages are dropped after 30 seconds.
 */
class ChatRepository(
    private val dao: ChatDao,
    private val relay: RelayClient,
    private val clock: ServerClock,
    private val prefs: Prefs,
    private val scope: CoroutineScope,
) {
    val messages = dao.observeMessages()
    val outbox = dao.observeOutbox()

    val state: StateFlow<ConnState> = relay.state
    val notice = MutableStateFlow<String?>(null)

    private val _onlineMembers = MutableStateFlow<List<String>>(emptyList())
    val onlineMembers: StateFlow<List<String>> = _onlineMembers

    private val _onlineCount = MutableStateFlow(0)
    val onlineCount: StateFlow<Int> = _onlineCount

    private val _lastDeliveredCount = MutableStateFlow<Int?>(null)
    val lastDeliveredCount: StateFlow<Int?> = _lastDeliveredCount

    private val flushLock = Mutex()

    init {
        scope.launch {
            relay.frames.collect { onFrame(it) }
        }

        scope.launch {
            relay.state.collect {
                if (it == ConnState.ONLINE) flushOutbox()
                else {
                    _onlineMembers.value = emptyList()
                    _onlineCount.value = 0
                }
            }
        }

        // Remove expired messages from local storage.
        scope.launch {
            while (true) {
                purge()
                delay(250)
            }
        }
    }

    fun now() = clock.now()

    fun connect() {
        if (!relay.running) {
            relay.start(
                prefs.relayUrl,
                prefs.name,
                prefs.deviceId
            )
        }
    }

    fun disconnect() = relay.stop()

    fun leave() {
        relay.stop()
        scope.launch {
            dao.wipe()
        }
    }

    suspend fun send(raw: String) {
        val text = raw.trim().take(Protocol.MAX_TEXT)
        if (text.isEmpty()) return

        // Keep the same ID when retrying a queued message.
        dao.addOutbox(
            OutboxEntity(
                UUID.randomUUID().toString(),
                prefs.name,
                text,
                clock.now()
            )
        )

        flushOutbox()
    }

    private suspend fun flushOutbox() = flushLock.withLock {
        if (relay.state.value != ConnState.ONLINE) return

        for (o in dao.outbox()) {

            // Do not send messages that waited 30 seconds.
            if (clock.now() - o.createdAt >= Protocol.MAX_QUEUE_AGE_MS) {
                dao.removeOutbox(o.id)

                showNotice(
                    "Message discarded: it waited longer than 30 seconds."
                )
                continue
            }

            if (!relay.sendMessage(o.id, o.sender, o.text)) {
                break
            }
        }
    }

    private suspend fun onFrame(f: Incoming) {
        when (f.type) {

            // Protocol v1: MESSAGE
            "message" -> {
                val id = f.id ?: return
                val text = f.text ?: return
                val sentAt = f.sent_at ?: return
                val expiresAt = f.expires_at ?: return

                val forgetAt = expiresAt + Protocol.SEEN_GRACE_MS

                if (expiresAt <= clock.now()) {
                    dao.insertSeen(
                        SeenEntity(id, forgetAt)
                    )
                    return
                }

                val sender = f.sender ?: "?"

                val message = MessageEntity(
                    id,
                    sender,
                    text,
                    sentAt,
                    expiresAt,
                    sender == prefs.name
                )

                dao.acceptIncoming(message, forgetAt)
            }

            // Protocol v1: DELIVERED
            "delivered" -> {
                f.id?.let {
                    dao.removeOutbox(it)
                }

                _lastDeliveredCount.value = f.count
            }

            // Protocol v1: EXPIRED
            "expired" -> {
                f.id?.let {
                    dao.deleteMessage(it)
                }
            }

            // Protocol v1: PRESENCE
            "presence" -> {
                val members = f.members ?: emptyList()

                _onlineMembers.value = members
                _onlineCount.value = f.count ?: members.size
            }

            // Protocol v1: ERROR
            "error" -> {
                showNotice(
                    f.message ?: "Relay error: ${f.code ?: "unknown"}"
                )
            }
        }
    }

    private suspend fun purge() {
        val currentTime = clock.now()

        dao.purgeExpired(currentTime)
        dao.purgeSeen(currentTime)
    }

    private fun showNotice(text: String) {
        notice.value = text

        scope.launch {
            delay(4_000)

            if (notice.value == text) {
                notice.value = null
            }
        }
    }
}
