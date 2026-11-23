package com.example.vanish

import android.app.Application
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.vanish.data.AppDatabase
import com.example.vanish.net.RelayClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Holds the long-lived objects so rotation (Activity recreation) never loses state. */
class VanishApp : Application() {
    lateinit var prefs: Prefs
    lateinit var repo: ChatRepository

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        val clock = ServerClock(prefs)
        val db = Room.databaseBuilder(this, AppDatabase::class.java, "vanish.db")
            // TRUNCATE journal: no -wal file that could keep old text around, so the .db file is the only place to check.
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    // Overwrite deleted rows with zeros instead of leaving the text in free pages.
                    db.query("PRAGMA secure_delete = ON").close()
                }
            })
            .build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        repo = ChatRepository(db.chatDao(), RelayClient(scope, clock), clock, prefs, scope)
    }
}
