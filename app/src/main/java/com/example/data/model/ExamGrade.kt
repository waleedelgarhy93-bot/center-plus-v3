package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exam_grades",
    foreignKeys = [
        ForeignKey(
            entity = Exam::class,
            parentColumns = ["id"],
            childColumns = ["examId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Student::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["examId", "studentId"], unique = true),
        Index(value = ["examId"]),
        Index(value = ["studentId"])
    ]
)
data class ExamGrade(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val examId: Long,
    val studentId: Long,
    val grade: Double,
    val createdAt: String
)

data class StudentExamGradeItem(
    val studentId: Long,
    val studentName: String,
    val groupName: String,
    val examId: Long,
    val grade: Double?,
    val gradeId: Long?
)
