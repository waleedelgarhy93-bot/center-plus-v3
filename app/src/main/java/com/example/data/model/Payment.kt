package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = Student::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Subject::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("studentId"),
        Index("subjectId")
    ]
)
data class Payment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val studentId: Long,
    val subjectId: Long,
    val amount: Double,
    val month: String,      // e.g. "2026-10"
    val status: String,     // "paid", "unpaid", "late"
    val paidAt: String? = null,
    val createdAt: String = ""
)

data class PaymentWithSubject(
    val id: Long,
    val studentId: Long,
    val subjectId: Long,
    val subjectName: String,
    val subjectColor: String,
    val amount: Double,
    val month: String,
    val status: String,
    val paidAt: String?,
    val createdAt: String
)
