package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exams",
    foreignKeys = [
        ForeignKey(
            entity = Subject::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("subjectId")
    ]
)
data class Exam(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val subjectId: Long,
    val date: String,
    val maxGrade: Int = 20,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class ExamWithSubject(
    val id: Long,
    val title: String,
    val subjectId: Long,
    val subjectName: String,
    val subjectColor: String,
    val date: String,
    val maxGrade: Int,
    val notes: String?,
    val createdAt: Long,
    val studentsCount: Int = 0
)
