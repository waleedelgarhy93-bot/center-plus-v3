package com.example.data.model

data class GroupWithSubject(
    val id: Long,
    val subjectId: Long,
    val subjectName: String,
    val subjectColor: String,
    val subjectIcon: String,
    val grade: String,
    val center: String,
    val days: String,
    val time: String,
    val price: Double,
    val notes: String,
    val studentCount: Int = 0
)
