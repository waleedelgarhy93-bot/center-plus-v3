package com.example.data.repository

import com.example.data.dao.StudentDao
import com.example.data.model.Student
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class StudentRepository(private val studentDao: StudentDao) {

    fun getAllStudents(): Flow<List<Student>> = studentDao.getAllStudents()

    fun searchStudents(query: String): Flow<List<Student>> {
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) {
            studentDao.getAllStudents()
        } else {
            studentDao.searchStudents(trimmed)
        }
    }

    suspend fun getStudentById(id: Long): Student? = withContext(Dispatchers.IO) {
        studentDao.getStudentById(id)
    }

    suspend fun isBarcodeTaken(barcode: String, currentStudentId: Long = 0L): Boolean = withContext(Dispatchers.IO) {
        val existing = studentDao.getOtherStudentWithBarcode(barcode.trim(), currentStudentId)
        existing != null
    }

    suspend fun insertStudent(student: Student): Long = withContext(Dispatchers.IO) {
        studentDao.insertStudent(student)
    }

    suspend fun updateStudent(student: Student) = withContext(Dispatchers.IO) {
        studentDao.updateStudent(student)
    }

    suspend fun deleteStudent(student: Student) = withContext(Dispatchers.IO) {
        studentDao.deleteStudent(student)
    }

    suspend fun deleteStudentById(id: Long) = withContext(Dispatchers.IO) {
        studentDao.deleteStudentById(id)
    }
}
