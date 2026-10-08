package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Exam
import com.example.data.model.ExamWithSubject
import kotlinx.coroutines.flow.Flow

@Dao
interface ExamDao {

    @Query("SELECT * FROM exams ORDER BY id DESC")
    fun getAllExams(): Flow<List<Exam>>

    @Query(
        """
        SELECT 
            e.id AS id,
            e.title AS title,
            e.subjectId AS subjectId,
            COALESCE(s.name, 'مادة عامة') AS subjectName,
            COALESCE(s.color, '#2563EB') AS subjectColor,
            e.date AS date,
            e.maxGrade AS maxGrade,
            e.notes AS notes,
            e.createdAt AS createdAt,
            (SELECT COUNT(*) FROM exam_grades eg WHERE eg.examId = e.id) AS studentsCount
        FROM exams e
        LEFT JOIN subjects s ON e.subjectId = s.id
        ORDER BY e.id DESC
        """
    )
    fun getAllExamsWithSubject(): Flow<List<ExamWithSubject>>

    @Query("SELECT * FROM exams WHERE id = :id LIMIT 1")
    suspend fun getExamById(id: Long): Exam?

    @Query("SELECT * FROM exams WHERE subjectId = :subjectId ORDER BY id DESC")
    fun getExamsBySubject(subjectId: Long): Flow<List<Exam>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExam(exam: Exam): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExams(exams: List<Exam>)

    @Update
    suspend fun updateExam(exam: Exam)

    @Delete
    suspend fun deleteExam(exam: Exam)

    @Query("DELETE FROM exams WHERE id = :id")
    suspend fun deleteExamById(id: Long)

    @Query("SELECT COUNT(*) FROM exams")
    suspend fun getCount(): Int
}
