package com.example.pepperapp.data

import androidx.room.*
import com.example.pepperapp.model.UserProfile

@Dao
interface UserProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: UserProfile)

    @Query("SELECT * FROM user_profiles WHERE id = :id")
    suspend fun getById(id: String): UserProfile?

    @Query("SELECT * FROM user_profiles")
    suspend fun getAll(): List<UserProfile>

    @Delete
    suspend fun delete(profile: UserProfile)

    @Query("DELETE FROM user_profiles")
    suspend fun clearAll()

    @Query("SELECT * FROM user_profiles WHERE id = :id")
    suspend fun getById(id: Int): UserProfile?
    @Query("SELECT * FROM user_profiles WHERE name = :name LIMIT 1")
    suspend fun getUserByName(name: String): UserProfile?

}
