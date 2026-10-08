package com.example.ui.screens.students

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.Student
import com.example.data.model.User
import com.example.data.repository.StudentRepository
import com.example.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SaveStudentResult {
    data class Success(val message: String) : SaveStudentResult
    data class Error(val message: String) : SaveStudentResult
}

class StudentsViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = StudentRepository(database.studentDao())
    private val userRepository = UserRepository(database.userDao())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val students: StateFlow<List<Student>> = _searchQuery
        .flatMapLatest { query ->
            repository.searchStudents(query)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _uiEvents = MutableSharedFlow<String>()
    val uiEvents: SharedFlow<String> = _uiEvents.asSharedFlow()

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    fun deleteStudent(student: Student) {
        viewModelScope.launch {
            repository.deleteStudent(student)
            val msg = "تم حذف الطالب: ${student.name}"
            Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
            _uiEvents.emit(msg)
        }
    }

    suspend fun getStudentUser(studentId: Long): User? = withContext(Dispatchers.IO) {
        userRepository.getUserByStudentIdAndRole(studentId, "student")
    }

    suspend fun saveStudent(
        id: Long = 0L,
        name: String,
        barcode: String,
        parentPhone: String,
        groupName: String,
        notes: String,
        studentUsername: String = "",
        studentPassword: String = "",
        type: String = "center"
    ): SaveStudentResult {
        val trimmedName = name.trim()
        val effectiveBarcode = if (type == "online" && barcode.trim().isEmpty()) {
            "ONL_${System.currentTimeMillis() % 10000000}"
        } else {
            barcode.trim()
        }

        if (trimmedName.isEmpty()) {
            return SaveStudentResult.Error("اسم الطالب مطلوب")
        }

        if (effectiveBarcode.isEmpty()) {
            return SaveStudentResult.Error("رقم الباركود مطلوب لطالب السنتر")
        }

        val isTaken = repository.isBarcodeTaken(effectiveBarcode, id)
        if (isTaken) {
            return SaveStudentResult.Error("رقم الباركود مستخدم بالفعل لطالب آخر!")
        }

        val student = Student(
            id = id,
            name = trimmedName,
            barcode = effectiveBarcode,
            parentPhone = parentPhone.trim(),
            groupName = if (type == "online") "طالب أونلاين" else groupName.trim(),
            notes = notes.trim(),
            type = type
        )

        val savedId = if (id == 0L) {
            repository.insertStudent(student)
        } else {
            repository.updateStudent(student)
            id
        }

        // Create or update login account for the student
        val uName = studentUsername.trim()
        val uPass = studentPassword.trim()
        val userRole = if (type == "online") "student_online" else "student"
        if (uName.isNotEmpty() && uPass.isNotEmpty()) {
            userRepository.createOrUpdateStudentUser(
                studentId = savedId,
                username = uName,
                password = uPass,
                fullName = trimmedName,
                phone = parentPhone.trim(),
                role = userRole
            )
        }

        val msg = if (id == 0L) "تم إضافة الطالب بنجاح" else "تم تعديل بيانات الطالب بنجاح"
        Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
        return SaveStudentResult.Success(msg)
    }
}
