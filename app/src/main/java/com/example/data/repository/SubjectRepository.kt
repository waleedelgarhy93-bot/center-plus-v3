package com.example.data.repository

import com.example.data.dao.SubjectDao
import com.example.data.model.Subject
import com.example.data.model.SubjectWithGroupCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SubjectRepository(private val subjectDao: SubjectDao) {

    fun getAllSubjects(): Flow<List<Subject>> = subjectDao.getAllSubjects()

    fun getAllSubjectsWithGroupCount(): Flow<List<SubjectWithGroupCount>> =
        subjectDao.getAllSubjectsWithGroupCount()

    suspend fun getSubjectById(id: Long): Subject? = withContext(Dispatchers.IO) {
        subjectDao.getSubjectById(id)
    }

    suspend fun insertSubject(subject: Subject): Long = withContext(Dispatchers.IO) {
        subjectDao.insertSubject(subject)
    }

    suspend fun updateSubject(subject: Subject) = withContext(Dispatchers.IO) {
        subjectDao.updateSubject(subject)
    }

    suspend fun deleteSubject(subject: Subject) = withContext(Dispatchers.IO) {
        subjectDao.deleteSubject(subject)
    }

    suspend fun deleteSubjectById(id: Long) = withContext(Dispatchers.IO) {
        subjectDao.deleteSubjectById(id)
    }
}
