package com.example.pepperapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GameResponseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(response: GameResponse)

    @Query("SELECT * FROM game_responses ORDER BY id DESC")
    suspend fun getAll(): List<GameResponse>

    @Query("DELETE FROM game_responses")
    suspend fun clearAll()
}
