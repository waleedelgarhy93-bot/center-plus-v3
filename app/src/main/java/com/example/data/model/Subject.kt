package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subjects")
data class Subject(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val icon: String = "calculate", // e.g. "calculate", "science", "biotech", "menu_book", "auto_stories", "functions"
    val color: String = "#EF4444"   // hex color string
)
