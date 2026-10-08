package com.example.data.repository

import com.example.data.dao.UserDao
import com.example.data.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UserRepository(private val userDao: UserDao) {

    suspend fun login(username: String, password: String): User? = withContext(Dispatchers.IO) {
        val cleanUser = username.trim()
        val cleanPass = password.trim()
        var user = userDao.login(cleanUser, cleanPass)
        if (user == null) {
            ensureDefaultUsers()
            user = userDao.login(cleanUser, cleanPass)
        }
        user
    }

    suspend fun ensureDefaultUsers() = withContext(Dispatchers.IO) {
        val defaultUsers = listOf(
            User(
                username = "admin",
                password = "admin123",
                role = "teacher",
                fullName = "أ. وليد الجارحي",
                phone = "01000000000",
                studentId = null,
                isActive = true,
                createdAt = "2026-10-01"
            ),
            User(
                username = "student",
                password = "student123",
                role = "student",
                fullName = "أحمد محمود سالم",
                phone = "01012345678",
                studentId = 1L,
                isActive = true,
                createdAt = "2026-10-01"
            ),
            User(
                username = "student1",
                password = "pass123",
                role = "student",
                fullName = "أحمد محمود سالم",
                phone = "01012345678",
                studentId = 1L,
                isActive = true,
                createdAt = "2026-10-01"
            ),
            User(
                username = "parent",
                password = "parent123",
                role = "parent",
                fullName = "ولي أمر الطالب أحمد محمود",
                phone = "01012345678",
                studentId = 1L,
                isActive = true,
                createdAt = "2026-10-01"
            ),
            User(
                username = "parent1",
                password = "parent123",
                role = "parent",
                fullName = "ولي أمر الطالب أحمد محمود",
                phone = "01012345678",
                studentId = 1L,
                isActive = true,
                createdAt = "2026-10-01"
            ),
            User(
                username = "mohamed_online",
                password = "password123",
                role = "student_online",
                fullName = "محمد علي أحمد",
                phone = "01099887766",
                studentId = 8L,
                isActive = true,
                createdAt = "2026-10-01"
            )
        )

        for (u in defaultUsers) {
            val existing = userDao.getUserByUsername(u.username)
            if (existing == null) {
                userDao.insertUser(u)
            } else if (existing.password != u.password || !existing.isActive) {
                userDao.updateUser(existing.copy(password = u.password, isActive = true))
            }
        }
    }

    suspend fun getUserByUsername(username: String): User? = withContext(Dispatchers.IO) {
        userDao.getUserByUsername(username.trim())
    }

    suspend fun getUserById(id: Long): User? = withContext(Dispatchers.IO) {
        userDao.getUserById(id)
    }

    suspend fun getUserByStudentIdAndRole(studentId: Long, role: String): User? = withContext(Dispatchers.IO) {
        userDao.getUserByStudentIdAndRole(studentId, role)
    }

    suspend fun insertUser(user: User): Long = withContext(Dispatchers.IO) {
        userDao.insertUser(user)
    }

    suspend fun createOrUpdateStudentUser(
        studentId: Long,
        username: String,
        password: String,
        fullName: String,
        phone: String,
        role: String = "student"
    ): Long = withContext(Dispatchers.IO) {
        val existing = userDao.getUserByStudentIdAndRole(studentId, role)
            ?: userDao.getUserByStudentIdAndRole(studentId, if (role == "student") "student_online" else "student")
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(Date())
        if (existing != null) {
            val updated = existing.copy(
                username = username.trim(),
                password = password,
                fullName = fullName.trim(),
                phone = phone.trim(),
                role = role
            )
            userDao.updateUser(updated)
            existing.id
        } else {
            val newUser = User(
                username = username.trim(),
                password = password,
                role = role,
                fullName = fullName.trim(),
                phone = phone.trim(),
                studentId = studentId,
                isActive = true,
                createdAt = now
            )
            userDao.insertUser(newUser)
        }
    }

    suspend fun createParentUser(
        studentId: Long,
        username: String,
        password: String,
        fullName: String,
        phone: String
    ): Long = withContext(Dispatchers.IO) {
        val existing = userDao.getUserByStudentIdAndRole(studentId, "parent")
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(Date())
        if (existing != null) {
            val updated = existing.copy(
                username = username.trim(),
                password = password,
                fullName = fullName.trim(),
                phone = phone.trim()
            )
            userDao.updateUser(updated)
            existing.id
        } else {
            val newUser = User(
                username = username.trim(),
                password = password,
                role = "parent",
                fullName = fullName.trim(),
                phone = phone.trim(),
                studentId = studentId,
                isActive = true,
                createdAt = now
            )
            userDao.insertUser(newUser)
        }
    }
}
