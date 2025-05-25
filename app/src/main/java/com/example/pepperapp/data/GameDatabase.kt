package com.example.pepperapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [GameResponse::class], version = 6, exportSchema = false)
abstract class GameDatabase : RoomDatabase() {
    abstract fun gameResponseDao(): GameResponseDao

    companion object {
        @Volatile private var INSTANCE: GameDatabase? = null
        fun getInstance(context: Context): GameDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    GameDatabase::class.java,
                    "pepper_game_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
