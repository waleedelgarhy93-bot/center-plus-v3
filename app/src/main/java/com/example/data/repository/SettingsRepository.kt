package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.dao.AppSettingsDao
import com.example.data.model.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class SettingsRepository(private val settingsDao: AppSettingsDao) {

    val defaultSettings = AppSettings(
        id = 1L,
        centerName = "",
        teacherName = "",
        phone = "",
        themeColor = "blue",
        isSetupComplete = false,
        logoPath = null,
        whatsappEnabled = true,
        attendanceMethod = "camera"
    )

    fun getSettingsFlow(): Flow<AppSettings> {
        return settingsDao.getSettings().filterNotNull()
    }

    suspend fun getSettings(): AppSettings = withContext(Dispatchers.IO) {
        val existing = settingsDao.getSettingsDirect()
        if (existing == null) {
            settingsDao.insertSettings(defaultSettings)
            defaultSettings
        } else {
            existing
        }
    }

    suspend fun isSetupComplete(): Boolean = withContext(Dispatchers.IO) {
        val existing = settingsDao.getSettingsDirect()
        existing?.isSetupComplete ?: false
    }

    suspend fun saveSettings(settings: AppSettings): Unit = withContext(Dispatchers.IO) {
        settingsDao.insertSettings(settings.copy(id = 1L))
    }

    suspend fun markSetupComplete(
        centerName: String,
        teacherName: String,
        phone: String,
        themeColor: String = "blue",
        logoPath: String? = null
    ): AppSettings = withContext(Dispatchers.IO) {
        val current = settingsDao.getSettingsDirect() ?: defaultSettings
        val updated = current.copy(
            id = 1L,
            centerName = centerName.trim(),
            teacherName = teacherName.trim(),
            phone = phone.trim(),
            themeColor = themeColor,
            isSetupComplete = true,
            logoPath = logoPath ?: current.logoPath
        )
        settingsDao.insertSettings(updated)
        updated
    }

    suspend fun saveLogoFromUri(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val logoFile = File(context.filesDir, "custom_center_logo_${System.currentTimeMillis()}.png")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(logoFile).use { output ->
                input.copyTo(output)
            }
        }
        logoFile.absolutePath
    }
}
