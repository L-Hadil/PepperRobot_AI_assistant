package com.example.pepperapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_responses")
// Nouvelle entité, on stocke q1…q6 séparément
data class GameResponse(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val childName: String,
    val isKnown: Int,
    val age: Int, // 1 = connaissait, 0 = non
    val q1: Int,       // 1 si bonne réponse à la question 1, 0 sinon
    val q2: Int,
    val q3: Int,
    val q4: Int,
    val q5: Int,
    val q6: Int,
    val score: Int     // total des bonnes réponses (q1+…+q6)
)
