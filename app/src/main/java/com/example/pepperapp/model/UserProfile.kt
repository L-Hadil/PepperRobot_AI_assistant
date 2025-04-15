package com.example.pepperapp.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val name: String,
    val age: Int,
    val photoBase64: String,

    val threadIdGPT: String? = null

)
