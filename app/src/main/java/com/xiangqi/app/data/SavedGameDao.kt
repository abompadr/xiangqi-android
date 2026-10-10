package com.xiangqi.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedGameDao {
    @Query("SELECT * FROM saved_games ORDER BY savedAt DESC")
    fun getAll(): Flow<List<SavedGame>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(game: SavedGame): Long

    @Delete
    suspend fun delete(game: SavedGame)
}
