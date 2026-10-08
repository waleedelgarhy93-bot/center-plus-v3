package com.example.ui.screens.subjects

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.Subject
import com.example.data.model.SubjectWithGroupCount
import com.example.data.repository.SubjectRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SubjectsViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = SubjectRepository(database.subjectDao())

    val subjects: StateFlow<List<SubjectWithGroupCount>> = repository.getAllSubjectsWithGroupCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun saveSubject(
        id: Long,
        name: String,
        description: String,
        icon: String,
        color: String,
        onSuccess: () -> Unit
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            Toast.makeText(getApplication(), "يرجى كتابة اسم المادة", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            val subject = Subject(
                id = id,
                name = trimmedName,
                description = description.trim(),
                icon = icon,
                color = color
            )

            if (id == 0L) {
                repository.insertSubject(subject)
                Toast.makeText(getApplication(), "تم إضافة المادة بنجاح", Toast.LENGTH_SHORT).show()
            } else {
                repository.updateSubject(subject)
                Toast.makeText(getApplication(), "تم تعديل المادة بنجاح", Toast.LENGTH_SHORT).show()
            }
            onSuccess()
        }
    }

    fun deleteSubject(subject: SubjectWithGroupCount) {
        viewModelScope.launch {
            repository.deleteSubjectById(subject.id)
            Toast.makeText(getApplication(), "تم حذف مادة ${subject.name}", Toast.LENGTH_SHORT).show()
        }
    }
}
