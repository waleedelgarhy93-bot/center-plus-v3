package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["username"], unique = true),
        Index(value = ["studentId"])
    ]
)
data class User(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val username: String,
    val password: String,
    val role: String, // "teacher", "student", "student_online", "parent"
    val fullName: String,
    val phone: String = "",
    val studentId: Long? = null,
    val isActive: Boolean = true,
    val createdAt: String = ""
) {
    companion object {
        const val ROLE_TEACHER = "teacher"
        const val ROLE_STUDENT = "student"
        const val ROLE_STUDENT_ONLINE = "student_online"
        const val ROLE_PARENT = "parent"
    }
}
