package com.avengeeker.floatingtype.history

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [InputHistoryEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}
