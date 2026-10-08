package com.example.ui.screens.grades

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.data.database.AppDatabase
import com.example.data.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

data class ExamGradeUiModel(
    val examId: Long,
    val examTitle: String,
    val subjectName: String,
    val date: String,
    val score: Double,
    val maxScore: Int,
    val percentage: Int,
    val isPassed: Boolean
) {
    val scoreFormatted: String
        get() {
            val scoreStr = if (score % 1.0 == 0.0) score.toInt().toString() else score.toString()
            return "$scoreStr / $maxScore"
        }
    val percentageFormatted: String
        get() = "$percentage%"
}

class GradesViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val sessionManager = SessionManager(application)

    private val _grades = MutableStateFlow<List<ExamGradeUiModel>>(emptyList())
    val grades: StateFlow<List<ExamGradeUiModel>> = _grades.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _studentName = MutableStateFlow("")
    val studentName: StateFlow<String> = _studentName.asStateFlow()

    fun loadGradesForStudent(studentIdOverride: Long? = null, studentNameHint: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            var targetStudentId = studentIdOverride ?: sessionManager.studentId

            // If studentId from session is invalid, try to match by name or fallback
            if (targetStudentId <= 0 && !studentNameHint.isNullOrBlank()) {
                try {
                    val cur = database.openHelper.readableDatabase.query(
                        SimpleSQLiteQuery("SELECT id FROM students WHERE name LIKE ? LIMIT 1", arrayOf("%$studentNameHint%"))
                    )
                    cur.use {
                        if (it.moveToNext()) {
                            targetStudentId = it.getLong(0)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (targetStudentId <= 0) {
                targetStudentId = 1L
            }

            val studentObj = database.studentDao().getStudentById(targetStudentId)
            _studentName.value = studentObj?.name ?: studentNameHint ?: "الطالب"

            val query = SimpleSQLiteQuery(
                """
                SELECT 
                    e.id AS examId,
                    e.title AS examTitle,
                    COALESCE(s.name, 'مادة عامة') AS subjectName,
                    e.date AS examDate,
                    eg.grade AS studentGrade,
                    e.maxGrade AS maxGrade
                FROM exam_grades eg
                INNER JOIN exams e ON eg.examId = e.id
                LEFT JOIN subjects s ON e.subjectId = s.id
                WHERE eg.studentId = ?
                ORDER BY e.date DESC, eg.id DESC
                """,
                arrayOf(targetStudentId)
            )

            val resultList = mutableListOf<ExamGradeUiModel>()
            try {
                val cursor = database.openHelper.readableDatabase.query(query)
                cursor.use { c ->
                    val examIdIdx = c.getColumnIndex("examId")
                    val examTitleIdx = c.getColumnIndex("examTitle")
                    val subjectNameIdx = c.getColumnIndex("subjectName")
                    val examDateIdx = c.getColumnIndex("examDate")
                    val studentGradeIdx = c.getColumnIndex("studentGrade")
                    val maxGradeIdx = c.getColumnIndex("maxGrade")

                    while (c.moveToNext()) {
                        val eId = if (examIdIdx >= 0) c.getLong(examIdIdx) else 0L
                        val eTitle = if (examTitleIdx >= 0) c.getString(examTitleIdx) else ""
                        val sName = if (subjectNameIdx >= 0) c.getString(subjectNameIdx) else ""
                        val eDate = if (examDateIdx >= 0) c.getString(examDateIdx) else ""
                        val sGrade = if (studentGradeIdx >= 0) c.getDouble(studentGradeIdx) else 0.0
                        val mGrade = if (maxGradeIdx >= 0) c.getInt(maxGradeIdx) else 20

                        val percentage = if (mGrade > 0) ((sGrade / mGrade.toDouble()) * 100.0).roundToInt() else 0
                        val isPassed = percentage >= 50

                        resultList.add(
                            ExamGradeUiModel(
                                examId = eId,
                                examTitle = eTitle,
                                subjectName = sName,
                                date = eDate,
                                score = sGrade,
                                maxScore = mGrade,
                                percentage = percentage,
                                isPassed = isPassed
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            withContext(Dispatchers.Main) {
                _grades.value = resultList
                _isLoading.value = false
            }
        }
    }
}
