package com.example.vanish.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ChatDao {
    @Query("SELECT * FROM messages ORDER BY sentAt") abstract fun observeMessages(): Flow<List<MessageEntity>>
    @Query("SELECT * FROM outbox ORDER BY createdAt") abstract fun observeOutbox(): Flow<List<OutboxEntity>>
    @Query("SELECT * FROM outbox ORDER BY createdAt") abstract suspend fun outbox(): List<OutboxEntity>

    @Insert abstract suspend fun addOutbox(o: OutboxEntity)
    @Query("DELETE FROM outbox WHERE id = :id") abstract suspend fun removeOutbox(id: String)

    /** Returns the new row id, or -1 if the id was already there. */
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun insertSeen(s: SeenEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun insertMessage(m: MessageEntity)

    /** Atomic "first time I see this id?" check + store. */
    @Transaction
    open suspend fun acceptIncoming(m: MessageEntity, forgetAt: Long): Boolean {
        if (insertSeen(SeenEntity(m.id, forgetAt)) == -1L) return false
        insertMessage(m)
        return true
    }

    @Query("DELETE FROM messages WHERE id = :id") abstract suspend fun deleteMessage(id: String)
    @Query("DELETE FROM messages WHERE expiresAt <= :now") abstract suspend fun purgeExpired(now: Long): Int
    @Query("DELETE FROM seen WHERE forgetAt <= :now") abstract suspend fun purgeSeen(now: Long)

    @Query("DELETE FROM messages") abstract suspend fun clearMessages()
    @Query("DELETE FROM outbox") abstract suspend fun clearOutbox()
    @Query("DELETE FROM seen") abstract suspend fun clearSeen()

    @Transaction
    open suspend fun wipe() {
        clearMessages(); clearOutbox(); clearSeen()
    }
}
