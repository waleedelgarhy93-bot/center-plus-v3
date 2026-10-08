package com.example.ui.screens.groups

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.Group
import com.example.data.model.GroupWithSubject
import com.example.data.model.Subject
import com.example.data.repository.GroupRepository
import com.example.data.repository.SubjectRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GroupsViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val groupRepository = GroupRepository(database.groupDao())
    private val subjectRepository = SubjectRepository(database.subjectDao())

    val groups: StateFlow<List<GroupWithSubject>> = groupRepository.getAllGroupsWithSubject()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val availableSubjects: StateFlow<List<Subject>> = subjectRepository.getAllSubjects()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun saveGroup(
        id: Long,
        subjectId: Long,
        grade: String,
        center: String,
        days: List<String>,
        time: String,
        priceText: String,
        notes: String,
        onSuccess: () -> Unit
    ) {
        val trimmedGrade = grade.trim()
        val trimmedCenter = center.trim()
        val trimmedTime = time.trim()

        if (trimmedGrade.isEmpty()) {
            Toast.makeText(getApplication(), "يرجى تحديد الصف الدراسي", Toast.LENGTH_SHORT).show()
            return
        }
        if (trimmedCenter.isEmpty()) {
            Toast.makeText(getApplication(), "يرجى كتابة اسم السنتر أو القاعة", Toast.LENGTH_SHORT).show()
            return
        }
        if (subjectId <= 0) {
            Toast.makeText(getApplication(), "يرجى اختيار المادة الدراسية", Toast.LENGTH_SHORT).show()
            return
        }
        if (days.isEmpty()) {
            Toast.makeText(getApplication(), "يرجى اختيار يوم واحد على الأقل في الأسبوع", Toast.LENGTH_SHORT).show()
            return
        }
        if (trimmedTime.isEmpty()) {
            Toast.makeText(getApplication(), "يرجى تحديد موعد الحصة", Toast.LENGTH_SHORT).show()
            return
        }

        val price = priceText.toDoubleOrNull() ?: 0.0

        val group = Group(
            id = id,
            subjectId = subjectId,
            grade = trimmedGrade,
            center = trimmedCenter,
            days = days.joinToString(", "),
            time = trimmedTime,
            price = price,
            notes = notes.trim()
        )

        viewModelScope.launch {
            if (id == 0L) {
                groupRepository.insertGroup(group)
                Toast.makeText(getApplication(), "تم إضافة المجموعة بنجاح", Toast.LENGTH_SHORT).show()
            } else {
                groupRepository.updateGroup(group)
                Toast.makeText(getApplication(), "تم تعديل المجموعة بنجاح", Toast.LENGTH_SHORT).show()
            }
            onSuccess()
        }
    }

    fun deleteGroup(group: GroupWithSubject) {
        viewModelScope.launch {
            groupRepository.deleteGroupById(group.id)
            Toast.makeText(getApplication(), "تم حذف المجموعة", Toast.LENGTH_SHORT).show()
        }
    }
}
