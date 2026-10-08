package com.example.ui.screens.setup

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.AppSettings
import com.example.data.repository.SettingsRepository
import com.example.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SetupViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val settingsRepository = SettingsRepository(database.settingsDao())
    private val userRepository = UserRepository(database.userDao())

    private val _centerName = MutableStateFlow("سنتر لوبيتال")
    val centerName: StateFlow<String> = _centerName.asStateFlow()

    private val _teacherName = MutableStateFlow("أ. وليد الجارحي")
    val teacherName: StateFlow<String> = _teacherName.asStateFlow()

    private val _phone = MutableStateFlow("01000000000")
    val phone: StateFlow<String> = _phone.asStateFlow()

    private val _selectedThemeHex = MutableStateFlow("#2563EB")
    val selectedThemeHex: StateFlow<String> = _selectedThemeHex.asStateFlow()

    private val _customLogoPath = MutableStateFlow<String?>(null)
    val customLogoPath: StateFlow<String?> = _customLogoPath.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = settingsRepository.getSettings()
            if (existing.centerName.isNotBlank()) _centerName.value = existing.centerName
            if (existing.teacherName.isNotBlank()) _teacherName.value = existing.teacherName
            if (existing.phone.isNotBlank()) _phone.value = existing.phone
            _selectedThemeHex.value = existing.themeColor
            _customLogoPath.value = existing.logoPath
        }
    }

    fun onCenterNameChange(value: String) {
        _centerName.value = value
        _errorMessage.value = null
    }

    fun onTeacherNameChange(value: String) {
        _teacherName.value = value
        _errorMessage.value = null
    }

    fun onPhoneChange(value: String) {
        _phone.value = value
        _errorMessage.value = null
    }

    fun onSelectTheme(hex: String) {
        _selectedThemeHex.value = hex
    }

    fun onLogoPicked(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val savedPath = settingsRepository.saveLogoFromUri(context, uri)
                _customLogoPath.value = savedPath
            } catch (e: Exception) {
                _errorMessage.value = "تعذر حفظ الصورة: ${e.message}"
            }
        }
    }

    fun removeLogo() {
        _customLogoPath.value = null
    }

    fun saveSetup(onSuccess: (AppSettings) -> Unit) {
        val cName = _centerName.value.trim()
        val tName = _teacherName.value.trim()
        val ph = _phone.value.trim()

        if (cName.isEmpty()) {
            _errorMessage.value = "يرجى كتابة اسم السنتر"
            return
        }
        if (tName.isEmpty()) {
            _errorMessage.value = "يرجى كتابة اسم المعلم أو إدارة السنتر"
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val saved = settingsRepository.markSetupComplete(
                    centerName = cName,
                    teacherName = tName,
                    phone = ph,
                    logoPath = _customLogoPath.value,
                    themeColor = _selectedThemeHex.value
                )

                // Also update admin teacher user full name & phone if exists
                val adminUser = userRepository.getUserByUsername("admin")
                if (adminUser != null) {
                    database.userDao().updateUser(
                        adminUser.copy(
                            fullName = tName,
                            phone = ph
                        )
                    )
                }

                _isLoading.value = false
                onSuccess(saved)
            } catch (e: Exception) {
                _isLoading.value = false
                _errorMessage.value = "حدث خطأ أثناء حفظ البيانات: ${e.message}"
            }
        }
    }
}
