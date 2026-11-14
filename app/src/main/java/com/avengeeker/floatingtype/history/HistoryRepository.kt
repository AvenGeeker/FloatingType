package com.avengeeker.floatingtype.history

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Local-only history storage. No audio is retained. */
class HistoryRepository(context: Context) {

    private val db = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "floating_type.db",
    ).build()

    private val dao = db.historyDao()

    val records: Flow<List<InputHistoryRecord>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun insert(record: InputHistoryRecord): Long = dao.insert(record.toEntity())

    suspend fun update(record: InputHistoryRecord) = dao.update(record.toEntity())

    suspend fun get(id: Long): InputHistoryRecord? = dao.getById(id)?.toDomain()

    suspend fun delete(id: Long) {
        dao.getById(id)?.let { dao.delete(it) }
    }

    suspend fun clear() = dao.deleteAll()
}
