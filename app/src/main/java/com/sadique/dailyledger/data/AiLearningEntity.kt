package com.sadique.dailyledger.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_learning")
data class AiLearningEntity(@PrimaryKey val ownerId: String, val payload: String)
