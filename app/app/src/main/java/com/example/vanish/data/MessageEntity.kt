package com.example.vanish.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A delivered message. Stores the relay's expires_at, never a "seconds left" counter. */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val sender: String,
    val text: String,
    val sentAt: Long,
    val expiresAt: Long,
    val mine: Boolean,
)

/** A message written but not yet confirmed by the relay. */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey val id: String,
    val sender: String,
    val text: String,
    val createdAt: Long,
)

/** Ids already processed. Holds no text, so a replayed message can be recognised and ignored. */
@Entity(tableName = "seen")
data class SeenEntity(
    @PrimaryKey val id: String,
    val forgetAt: Long,
)
