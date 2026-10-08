package com.example.data.repository

import com.example.data.dao.AttendanceDao
import com.example.data.dao.StudentDao
import com.example.data.model.Attendance
import com.example.data.model.AttendanceRecord
import com.example.data.model.Student
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface ScanResult {
    data class Success(val student: Student, val time: String, val status: String) : ScanResult
    data class AlreadyRecorded(val student: Student, val time: String) : ScanResult
    data class NotFound(val barcode: String) : ScanResult
    data class Error(val message: String) : ScanResult
}

class AttendanceRepository(
    private val studentDao: StudentDao,
    private val attendanceDao: AttendanceDao
) {
    val last5Attendances: Flow<List<AttendanceRecord>> = attendanceDao.getLast5AttendanceRecords()

    suspend fun processBarcode(rawBarcode: String): ScanResult = withContext(Dispatchers.IO) {
        val trimmed = rawBarcode.trim()
        if (trimmed.isEmpty()) {
            return@withContext ScanResult.Error("الباركود فارغ")
        }

        val student = studentDao.getStudentByBarcode(trimmed)
            ?: return@withContext ScanResult.NotFound(trimmed)

        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH).format(Date())

        val existing = attendanceDao.getAttendanceForStudentOnDate(student.id, currentDate)
        if (existing != null) {
            return@withContext ScanResult.AlreadyRecorded(student, existing.time)
        }

        val attendance = Attendance(
            studentId = student.id,
            date = currentDate,
            time = currentTime,
            status = "حاضر"
        )
        attendanceDao.insertAttendance(attendance)

        ScanResult.Success(student = student, time = currentTime, status = "حاضر")
    }

    suspend fun registerNewStudent(
        name: String,
        barcode: String,
        groupName: String,
        parentPhone: String
    ): Student = withContext(Dispatchers.IO) {
        val student = Student(
            name = name,
            barcode = barcode.trim(),
            groupName = groupName,
            parentPhone = parentPhone
        )
        val id = studentDao.insertStudent(student)
        student.copy(id = id)
    }

    suspend fun getStudentByBarcode(barcode: String): Student? = withContext(Dispatchers.IO) {
        studentDao.getStudentByBarcode(barcode.trim())
    }
}
