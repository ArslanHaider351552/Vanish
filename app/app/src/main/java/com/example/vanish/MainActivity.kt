package com.example.vanish

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.vanish.ui.ChatScreen
import com.example.vanish.ui.JoinScreen

class MainActivity : ComponentActivity() {
    private val app get() = application as VanishApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Root() } }
    }

    // Connected only while visible. Rotation keeps the connection (isChangingConfigurations).
    override fun onStart() {
        super.onStart()
        if (app.prefs.joined) app.repo.connect()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) app.repo.disconnect()
    }

    @Composable
    private fun Root() {
        var joined by remember { mutableStateOf(app.prefs.joined) }
        if (!joined) {
            JoinScreen(app.prefs.name, app.prefs.relayUrl) { name, url ->
                app.prefs.name = name
                app.prefs.relayUrl = url
                app.prefs.joined = true
                app.repo.connect()
                joined = true
            }
        } else {
            ChatScreen(app.repo, app.prefs.name) {
                app.repo.leave()
                app.prefs.joined = false
                joined = false
            }
        }
    }
}
