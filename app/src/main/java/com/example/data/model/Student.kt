package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "students",
    indices = [
        Index(value = ["barcode"], unique = true),
        Index(value = ["cardNumber"])
    ]
)
data class Student(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val barcode: String? = null,
    val cardNumber: String? = null,
    val phone: String = "",
    val parentPhone: String = "",
    val groupName: String = "",
    val notes: String = "",
    val type: String = "center" // "center" or "online"
)
