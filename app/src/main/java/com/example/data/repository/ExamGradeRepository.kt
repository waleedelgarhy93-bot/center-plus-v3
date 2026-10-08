package com.example.data.repository

import com.example.data.dao.ExamGradeDao
import com.example.data.model.ExamGrade
import com.example.data.model.StudentExamGradeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExamGradeRepository(private val examGradeDao: ExamGradeDao) {

    fun getStudentsWithGrades(examId: Long, subjectId: Long): Flow<List<StudentExamGradeItem>> {
        return examGradeDao.getStudentsWithGradesForExamAndSubject(examId, subjectId)
    }

    fun getAllStudentsWithGrades(examId: Long): Flow<List<StudentExamGradeItem>> {
        return examGradeDao.getAllStudentsWithGradesForExam(examId)
    }

    suspend fun saveGrade(examId: Long, studentId: Long, grade: Double): Long = withContext(Dispatchers.IO) {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val existing = examGradeDao.getGradeByExamAndStudent(examId, studentId)
        val examGrade = ExamGrade(
            id = existing?.id ?: 0L,
            examId = examId,
            studentId = studentId,
            grade = grade,
            createdAt = existing?.createdAt ?: currentDate
        )
        examGradeDao.insertGrade(examGrade)
    }

    suspend fun saveGrades(grades: List<Pair<Long, Double>>, examId: Long) = withContext(Dispatchers.IO) {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        grades.forEach { (studentId, gradeValue) ->
            val existing = examGradeDao.getGradeByExamAndStudent(examId, studentId)
            val examGrade = ExamGrade(
                id = existing?.id ?: 0L,
                examId = examId,
                studentId = studentId,
                grade = gradeValue,
                createdAt = existing?.createdAt ?: currentDate
            )
            examGradeDao.insertGrade(examGrade)
        }
    }

    suspend fun deleteGrade(examId: Long, studentId: Long) = withContext(Dispatchers.IO) {
        examGradeDao.deleteGradeByExamAndStudent(examId, studentId)
    }

    suspend fun ensureInitialGrades(examId: Long, studentIds: List<Long>) = withContext(Dispatchers.IO) {
        if (examGradeDao.getGradesCountByExam(examId) == 0 && studentIds.isNotEmpty()) {
            val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val sampleGrades = listOf(18.5, 19.0, 16.0)
            val initialGrades = studentIds.take(3).mapIndexed { index, studentId ->
                ExamGrade(
                    id = 0L,
                    examId = examId,
                    studentId = studentId,
                    grade = sampleGrades.getOrElse(index) { 15.0 },
                    createdAt = currentDate
                )
            }
            examGradeDao.insertGrades(initialGrades)
        }
    }
}
