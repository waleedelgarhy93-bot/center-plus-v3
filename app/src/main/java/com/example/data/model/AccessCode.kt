package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "access_codes",
    foreignKeys = [
        ForeignKey(
            entity = Subject::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Student::class,
            parentColumns = ["id"],
            childColumns = ["usedByStudentId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["code"], unique = true),
        Index(value = ["subjectId"]),
        Index(value = ["usedByStudentId"])
    ]
)
data class AccessCode(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val code: String,                // e.g. "RWD-3118" or "ONL-RWD-5500" or "A7B9K2M4"
    val cardNumber: String? = null,          // e.g. "3118" or "5500", null for online
    val subjectId: Long,
    val usedByStudentId: Long? = null,
    val usedAt: String? = null,
    val isOnline: Boolean = false
)
