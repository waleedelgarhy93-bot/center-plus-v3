package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.Attendance
import com.example.data.model.AttendanceRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendances WHERE studentId = :studentId AND date = :date LIMIT 1")
    suspend fun getAttendanceForStudentOnDate(studentId: Long, date: String): Attendance?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(attendance: Attendance): Long

    @Query(
        """
        SELECT 
            a.id AS attendanceId,
            s.id AS studentId,
            s.name AS studentName,
            s.barcode AS barcode,
            s.groupName AS groupName,
            a.date AS date,
            a.time AS time,
            a.status AS status
        FROM attendances a
        INNER JOIN students s ON a.studentId = s.id
        ORDER BY a.id DESC
        LIMIT 5
        """
    )
    fun getLast5AttendanceRecords(): Flow<List<AttendanceRecord>>

    @Query("SELECT COUNT(*) FROM attendances WHERE date = :date")
    fun getTodayAttendanceCount(date: String): Flow<Int>
}
