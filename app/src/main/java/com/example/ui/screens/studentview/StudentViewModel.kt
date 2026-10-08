package com.example.ui.screens.studentview

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.QuizWithVideoTitle
import com.example.data.model.Student
import com.example.data.model.SubjectWithGroupCount
import com.example.data.model.VideoWithQuizCount
import com.example.data.repository.CodeVerificationResult
import com.example.data.repository.ContentRepository
import com.example.data.repository.StudentRepository
import com.example.data.repository.SubjectRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StudentViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val studentRepository = StudentRepository(database.studentDao())
    private val subjectRepository = SubjectRepository(database.subjectDao())
    private val contentRepository = ContentRepository(
        videoDao = database.videoDao(),
        quizDao = database.quizDao(),
        accessCodeDao = database.accessCodeDao(),
        studentSubjectAccessDao = database.studentSubjectAccessDao()
    )

    val students: StateFlow<List<Student>> = studentRepository.getAllStudents()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val subjects: StateFlow<List<SubjectWithGroupCount>> = subjectRepository.getAllSubjectsWithGroupCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _currentStudentId = MutableStateFlow<Long>(1L)
    val currentStudentId: StateFlow<Long> = _currentStudentId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val unlockedSubjectIds: StateFlow<List<Long>> = _currentStudentId
        .flatMapLatest { studentId ->
            contentRepository.getUnlockedSubjectIds(studentId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedSubjectId = MutableStateFlow<Long?>(null)
    val selectedSubjectId: StateFlow<Long?> = _selectedSubjectId.asStateFlow()

    fun selectSubject(subjectId: Long) {
        _selectedSubjectId.value = subjectId
    }

    fun selectStudent(studentId: Long) {
        _currentStudentId.value = studentId
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentVideos: StateFlow<List<VideoWithQuizCount>> = _selectedSubjectId
        .flatMapLatest { id ->
            if (id != null) contentRepository.getVideosWithQuizCount(id)
            else flowOf(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentQuizzes: StateFlow<List<QuizWithVideoTitle>> = _selectedSubjectId
        .flatMapLatest { id ->
            if (id != null) contentRepository.getQuizzesWithVideoTitle(id)
            else flowOf(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun isSubjectUnlocked(subjectId: Long): Boolean {
        return unlockedSubjectIds.value.contains(subjectId)
    }

    suspend fun verifyAndApplyCode(
        code: String,
        subjectId: Long? = null
    ): CodeVerificationResult {
        val result = contentRepository.verifyAndApplyCode(
            rawCode = code,
            studentId = _currentStudentId.value,
            targetSubjectId = subjectId
        )

        when (result) {
            is CodeVerificationResult.Success -> {
                Toast.makeText(getApplication(), result.message, Toast.LENGTH_SHORT).show()
            }
            is CodeVerificationResult.InvalidCode -> {
                Toast.makeText(getApplication(), result.message, Toast.LENGTH_SHORT).show()
            }
            is CodeVerificationResult.AlreadyUsed -> {
                Toast.makeText(getApplication(), result.message, Toast.LENGTH_SHORT).show()
            }
            is CodeVerificationResult.WrongSubject -> {
                Toast.makeText(getApplication(), result.message, Toast.LENGTH_SHORT).show()
            }
        }
        return result
    }
}
