package com.example.vanish.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.vanish.ChatRepository
import com.example.vanish.Protocol
import com.example.vanish.data.MessageEntity
import com.example.vanish.net.ConnState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil

@Composable
fun JoinScreen(initialName: String, initialUrl: String, onJoin: (name: String, url: String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var url by rememberSaveable { mutableStateOf(initialUrl) }
    val urlOk = url.startsWith("ws://") || url.startsWith("wss://")

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Vanish", style = MaterialTheme.typography.headlineLarge)
        Text("Messages disappear ${Protocol.TTL_MS / 1000} seconds after they are sent.")
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(32) },
            label = { Text("Display name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Relay address") },
            isError = !urlOk,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onJoin(name.trim(), url.trim()) },
            enabled = name.isNotBlank() && urlOk,
        ) { Text("Join") }
    }
}

@Composable
fun ChatScreen(repo: ChatRepository, name: String, onLeave: () -> Unit) {
    val messages by repo.messages.collectAsState(initial = emptyList())
    val outbox by repo.outbox.collectAsState(initial = emptyList())
    val state by repo.state.collectAsState()
    val notice by repo.notice.collectAsState()
    // Ticks 10x per second. Every countdown is computed from expiresAt - relay time, never from a stored counter.
    val now by produceState(initialValue = repo.now()) {
        while (true) {
            value = repo.now()
            delay(100)
        }
    }
    var draft by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val visible = messages.filter { it.expiresAt > now } // belt and braces; the reaper deletes the rows too

    LaunchedEffect(visible.size, outbox.size) {
        val n = visible.size + outbox.size
        if (n > 0) listState.animateScrollToItem(n - 1)
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Vanish · $name", style = MaterialTheme.typography.titleMedium)
                Text(
                    when (state) {
                        ConnState.ONLINE -> "Online"
                        ConnState.CONNECTING -> "Connecting…"
                        ConnState.OFFLINE -> "Offline. Messages will be queued."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onLeave) { Text("Leave") }
        }
        notice?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(visible, key = { it.id }) { m -> MessageBubble(m, now) }
            items(outbox, key = { "out-" + it.id }) { o -> PendingBubble(o.text) }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Message") },
                maxLines = 3,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = draft.isNotBlank(),
                onClick = {
                    val text = draft
                    draft = ""
                    scope.launch { repo.send(text) }
                },
            ) { Text("Send") }
        }
    }
}

@Composable
private fun MessageBubble(m: MessageEntity, now: Long) {
    val remaining = (m.expiresAt - now).coerceAtLeast(0)
    val total = (m.expiresAt - m.sentAt).coerceAtLeast(1)
    val seconds = ceil(remaining / 1000.0).toInt()
    val fraction = (remaining.toFloat() / total).coerceIn(0f, 1f)

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (m.mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Column(Modifier.padding(10.dp)) {
                Text(m.sender, style = MaterialTheme.typography.labelSmall)
                Text(m.text)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                Text("${seconds}s", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun PendingBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Column(Modifier.padding(10.dp)) {
                Text(text)
                Text("Waiting to send…", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
