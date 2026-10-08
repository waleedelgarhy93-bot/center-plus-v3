package com.example.data.model

data class AttendanceRecord(
    val attendanceId: Long,
    val studentId: Long,
    val studentName: String,
    val barcode: String,
    val groupName: String,
    val date: String,
    val time: String,
    val status: String
)
