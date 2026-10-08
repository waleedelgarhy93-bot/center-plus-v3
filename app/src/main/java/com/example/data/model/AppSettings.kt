package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey
    val id: Long = 1L,
    val centerName: String = "",
    val teacherName: String = "",
    val phone: String = "",
    val themeColor: String = "blue",
    val isSetupComplete: Boolean = false,
    val logoPath: String? = null,
    val whatsappEnabled: Boolean = true,
    val attendanceMethod: String = "camera"
)
