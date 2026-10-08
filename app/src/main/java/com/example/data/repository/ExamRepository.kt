package com.example.data.repository

import com.example.data.dao.ExamDao
import com.example.data.model.Exam
import com.example.data.model.ExamWithSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ExamRepository(private val examDao: ExamDao) {

    fun getAllExamsFlow(): Flow<List<ExamWithSubject>> {
        return examDao.getAllExamsWithSubject()
    }

    suspend fun getExamById(id: Long): Exam? = withContext(Dispatchers.IO) {
        examDao.getExamById(id)
    }

    suspend fun saveExam(exam: Exam): Long = withContext(Dispatchers.IO) {
        examDao.insertExam(exam)
    }

    suspend fun updateExam(exam: Exam) = withContext(Dispatchers.IO) {
        examDao.updateExam(exam)
    }

    suspend fun deleteExam(exam: Exam) = withContext(Dispatchers.IO) {
        examDao.deleteExam(exam)
    }

    suspend fun deleteExamById(id: Long) = withContext(Dispatchers.IO) {
        examDao.deleteExamById(id)
    }

    suspend fun ensureInitialExams(mathSubjectId: Long) = withContext(Dispatchers.IO) {
        if (examDao.getCount() == 0) {
            val initialExams = listOf(
                Exam(
                    title = "اختبار الجبر والمصفوفات",
                    subjectId = mathSubjectId,
                    date = "2026-10-10",
                    maxGrade = 20,
                    notes = "اختبار الفصل الأول - محددات ومصفوفات",
                    createdAt = System.currentTimeMillis() - 86400000L * 5
                ),
                Exam(
                    title = "امتحان التفاضل وحساب المثلثات",
                    subjectId = mathSubjectId,
                    date = "2026-10-22",
                    maxGrade = 25,
                    notes = "مسائل نهايات واشتقاق",
                    createdAt = System.currentTimeMillis()
                )
            )
            examDao.insertExams(initialExams)
        }
    }
}
