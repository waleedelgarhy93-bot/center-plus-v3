package com.example.ui.screens.exams

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.Exam
import com.example.data.model.StudentExamGradeItem
import com.example.data.repository.ExamGradeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StudentGradeUiItem(
    val studentId: Long,
    val studentName: String,
    val groupName: String,
    val gradeText: String,
    val isSaved: Boolean,
    val isError: Boolean = false
)

class ExamGradesViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = ExamGradeRepository(database.examGradeDao())

    private var currentExamId: Long = 0L
    private var currentSubjectId: Long = 0L
    private var maxGrade: Int = 20

    private val _studentsGrades = MutableStateFlow<List<StudentGradeUiItem>>(emptyList())
    val studentsGrades: StateFlow<List<StudentGradeUiItem>> = _studentsGrades.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    fun initExam(examId: Long, subjectId: Long, examMaxGrade: Int) {
        currentExamId = examId
        currentSubjectId = subjectId
        maxGrade = examMaxGrade

        viewModelScope.launch {
            _isLoading.value = true
            // First attempt to load students filtered by subject
            repository.getStudentsWithGrades(examId, subjectId).collectLatest { items ->
                val listToUse = if (items.isNotEmpty()) {
                    items
                } else {
                    // Fallback to all students if no specific group match
                    withContext(Dispatchers.IO) {
                        database.examGradeDao().getAllStudentsWithGradesForExam(examId)
                    }.let { flow ->
                        var fallbackList = emptyList<StudentExamGradeItem>()
                        flow.collectLatest { fallbackList = it }
                        fallbackList
                    }
                }

                // Preserve user-edited text in progress if present
                val currentMap = _studentsGrades.value.associateBy { it.studentId }
                val updatedUiList = listToUse.map { item ->
                    val existing = currentMap[item.studentId]
                    val gradeText = if (existing != null && existing.gradeText.isNotEmpty()) {
                        existing.gradeText
                    } else if (item.grade != null) {
                        if (item.grade % 1.0 == 0.0) item.grade.toInt().toString() else item.grade.toString()
                    } else {
                        ""
                    }
                    val isSaved = item.grade != null && (existing == null || existing.gradeText == gradeText)
                    StudentGradeUiItem(
                        studentId = item.studentId,
                        studentName = item.studentName,
                        groupName = item.groupName,
                        gradeText = gradeText,
                        isSaved = isSaved,
                        isError = false
                    )
                }
                _studentsGrades.value = updatedUiList
                _isLoading.value = false
            }
        }
    }

    fun onGradeChange(studentId: Long, newText: String) {
        // Filter input to allow digits and at most one decimal point
        val cleanText = newText.filter { it.isDigit() || it == '.' }
        if (cleanText.count { it == '.' } > 1) return

        val score = cleanText.toDoubleOrNull()
        val isError = score != null && score > maxGrade

        _studentsGrades.value = _studentsGrades.value.map { item ->
            if (item.studentId == studentId) {
                item.copy(
                    gradeText = cleanText,
                    isSaved = false,
                    isError = isError
                )
            } else {
                item
            }
        }
    }

    fun autoSaveStudentGrade(studentId: Long, onSaved: () -> Unit) {
        val item = _studentsGrades.value.find { it.studentId == studentId } ?: return
        val score = item.gradeText.toDoubleOrNull() ?: return
        if (score < 0 || score > maxGrade) return

        viewModelScope.launch(Dispatchers.IO) {
            repository.saveGrade(currentExamId, studentId, score)
            withContext(Dispatchers.Main) {
                _studentsGrades.value = _studentsGrades.value.map {
                    if (it.studentId == studentId) it.copy(isSaved = true, isError = false) else it
                }
                onSaved()
            }
        }
    }

    fun saveAllGrades(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val validGrades = mutableListOf<Pair<Long, Double>>()
        var hasOutOfRange = false

        for (item in _studentsGrades.value) {
            if (item.gradeText.isNotBlank()) {
                val score = item.gradeText.toDoubleOrNull()
                if (score == null) {
                    onError("درجة غير صالحة للطالب ${item.studentName}")
                    return
                }
                if (score < 0 || score > maxGrade) {
                    hasOutOfRange = true
                    onError("الدرجة يجب ألا تتجاوز $maxGrade للطالب ${item.studentName}")
                    return
                }
                validGrades.add(Pair(item.studentId, score))
            }
        }

        if (hasOutOfRange) return

        if (validGrades.isEmpty()) {
            onError("الرجاء إدخال درجة واحدة على الأقل قبل الحفظ")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isSaving.value = true
            try {
                repository.saveGrades(validGrades, currentExamId)
                withContext(Dispatchers.Main) {
                    _studentsGrades.value = _studentsGrades.value.map {
                        if (it.gradeText.isNotBlank()) it.copy(isSaved = true, isError = false) else it
                    }
                    _isSaving.value = false
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isSaving.value = false
                    onError(e.message ?: "حدث خطأ أثناء الحفظ")
                }
            }
        }
    }
}
