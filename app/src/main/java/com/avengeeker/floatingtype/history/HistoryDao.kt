package com.avengeeker.floatingtype.history

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM input_history ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<InputHistoryEntity>>

    @Query("SELECT * FROM input_history WHERE id = :id")
    suspend fun getById(id: Long): InputHistoryEntity?

    @Insert
    suspend fun insert(entity: InputHistoryEntity): Long

    @Update
    suspend fun update(entity: InputHistoryEntity)

    @Delete
    suspend fun delete(entity: InputHistoryEntity)

    @Query("DELETE FROM input_history")
    suspend fun deleteAll()
}
