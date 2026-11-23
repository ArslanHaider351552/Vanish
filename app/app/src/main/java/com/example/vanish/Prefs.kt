
package com.example.vanish

import android.content.Context
import java.util.UUID

/**
 * Settings only (name, relay address, device id, clock offset).
 * Never message text.
 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences(
        "vanish",
        Context.MODE_PRIVATE
    )

    var name: String
        get() = sp.getString("name", "") ?: ""
        set(v) = sp.edit().putString("name", v).apply()

    var relayUrl: String
        get() = sp.getString(
            "relayUrl",
            Protocol.DEFAULT_RELAY_URL
        ) ?: Protocol.DEFAULT_RELAY_URL
        set(v) = sp.edit().putString("relayUrl", v).apply()

    var joined: Boolean
        get() = sp.getBoolean("joined", false)
        set(v) = sp.edit().putBoolean("joined", v).apply()

    /**
     * Relay time minus phone time, in milliseconds.
     */
    var clockOffset: Long
        get() = sp.getLong("clockOffset", 0L)
        set(v) = sp.edit().putLong("clockOffset", v).apply()

    val deviceId: String
        get() = sp.getString("deviceId", null)
            ?: UUID.randomUUID().toString().also {
                sp.edit().putString("deviceId", it).apply()
            }
}
