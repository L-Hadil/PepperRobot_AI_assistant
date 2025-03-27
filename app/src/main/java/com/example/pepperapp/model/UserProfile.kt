package com.example.pepperapp.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey val id: String,
    val name: String,
    val age: Int,
    val photoBase64: String,
    val threadId: String
)
