
package com.example.vanish.net

import com.example.vanish.Join
import com.example.vanish.Incoming
import com.example.vanish.OutgoingMessage
import com.example.vanish.Ping
import com.example.vanish.Protocol
import com.example.vanish.ServerClock
import com.google.gson.Gson
import com.google.gson.JsonParseException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

enum class ConnState { OFFLINE, CONNECTING, ONLINE }

class RelayClient(
    private val scope: CoroutineScope,
    private val clock: ServerClock
) {
    private val gson = Gson()

    private val http = OkHttpClient.Builder()
        .pingInterval(0, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow(ConnState.OFFLINE)
    val state: StateFlow<ConnState> = _state

    private val _frames = MutableSharedFlow<Incoming>(extraBufferCapacity = 64)
    val frames: SharedFlow<Incoming> = _frames

    @Volatile private var socket: WebSocket? = null
    @Volatile private var lastSentAt = 0L
    @Volatile private var lastPongAt = 0L

    private var job: Job? = null

    val running: Boolean
        get() = job?.isActive == true

    @Synchronized
    fun start(url: String, name: String, deviceId: String) {
        stop()
        job = scope.launch {
            runLoop(url, name, deviceId)
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        socket?.close(1000, null)
        socket = null
        _state.value = ConnState.OFFLINE
    }

    fun sendMessage(id: String, sender: String, text: String): Boolean {
        if (_state.value != ConnState.ONLINE) return false

        return socket?.send(
            gson.toJson(OutgoingMessage(id, sender, text))
        ) ?: false
    }

    private suspend fun runLoop(
        url: String,
        name: String,
        deviceId: String
    ) {
        val request = try {
            Request.Builder()
                .url(
                    url.replaceFirst("ws://", "http://")
                        .replaceFirst("wss://", "https://")
                )
                .build()
        } catch (e: IllegalArgumentException) {
            _state.value = ConnState.OFFLINE
            return
        }

        var backoff = 1_000L

        while (currentCoroutineContext().isActive) {
            _state.value = ConnState.CONNECTING

            val done = CompletableDeferred<Unit>()
            val live = AtomicBoolean(true)
            val reachedOnline = AtomicBoolean(false)

            val ws = http.newWebSocket(
                request,
                object : WebSocketListener() {

                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response
                    ) {
                        lastSentAt = System.currentTimeMillis()
                        lastPongAt = lastSentAt

                        webSocket.send(
                            gson.toJson(
                                Join(
                                    Protocol.VERSION,
                                    deviceId,
                                    name
                                )
                            )
                        )
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String
                    ) {
                        if (live.get()) {
                            handle(text, reachedOnline, webSocket)
                        }
                    }

                    override fun onClosing(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String
                    ) {
                        webSocket.close(code, reason)
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String
                    ) {
                        done.complete(Unit)
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?
                    ) {
                        done.complete(Unit)
                    }
                }
            )

            socket = ws

            val pinger = scope.launch {
                while (isActive) {
                    delay(Protocol.PING_MS)

                    if (_state.value == ConnState.ONLINE) {
                        val now = System.currentTimeMillis()

                        if (now - lastPongAt >= Protocol.PONG_TIMEOUT_MS) {
                            ws.close(1001, "Pong timeout")
                            break
                        }

                        if (!ws.send(gson.toJson(Ping()))) {
                            ws.close(1001, "Ping failed")
                            break
                        }
                    }
                }
            }

            try {
                done.await()
            } finally {
                live.set(false)
                pinger.cancel()
                ws.cancel()
                socket = null
            }

            _state.value = ConnState.OFFLINE

            delay(backoff)
            backoff = minOf(backoff * 2, 8_000L)
        }
    }

    private fun handle(
        text: String,
        reachedOnline: AtomicBoolean,
        webSocket: WebSocket
    ) {
        val frame: Incoming? = try {
            gson.fromJson(text, Incoming::class.java)
        } catch (e: JsonParseException) {
            null
        }

        if (frame == null) return

        when (frame.type) {
            "welcome" -> {
                if (frame.protocol != Protocol.VERSION) {
                    webSocket.close(1002, "Protocol mismatch")
                    return
                }

                frame.server_time?.let {
                    clock.sync(
                        it,
                        lastSentAt,
                        System.currentTimeMillis()
                    )
                }

                reachedOnline.set(true)
                _state.value = ConnState.ONLINE
            }

            "pong" -> {
                lastPongAt = System.currentTimeMillis()
            }

            else -> {
                _frames.tryEmit(frame)
            }
        }
    }
}
