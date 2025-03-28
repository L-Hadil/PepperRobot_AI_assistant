package com.example.pepperapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.pepperapp.model.UserProfile

@Database(entities = [UserProfile::class], version = 2)
abstract class PepperDatabase : RoomDatabase() {

    abstract fun userProfileDao(): UserProfileDao

    companion object {
        @Volatile
        private var INSTANCE: PepperDatabase? = null

        fun getDatabase(context: Context): PepperDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PepperDatabase::class.java,
                    "pepper_database",
                ).fallbackToDestructiveMigrationFrom(1).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
