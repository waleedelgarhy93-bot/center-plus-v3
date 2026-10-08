package com.example.ui.screens.settings

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.AppSettings
import com.example.data.repository.SettingsRepository
import com.example.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val settingsRepository = SettingsRepository(database.appSettingsDao())
    private val userRepository = UserRepository(database.userDao())

    val settings: StateFlow<AppSettings> = settingsRepository.getSettingsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = settingsRepository.defaultSettings
        )

    private val _centerName = MutableStateFlow("")
    val centerName: StateFlow<String> = _centerName.asStateFlow()

    private val _teacherName = MutableStateFlow("")
    val teacherName: StateFlow<String> = _teacherName.asStateFlow()

    private val _phone = MutableStateFlow("")
    val phone: StateFlow<String> = _phone.asStateFlow()

    private val _selectedThemeColor = MutableStateFlow("blue")
    val selectedThemeColor: StateFlow<String> = _selectedThemeColor.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    init {
        viewModelScope.launch {
            val initial = settingsRepository.getSettings()
            _centerName.value = initial.centerName
            _teacherName.value = initial.teacherName
            _phone.value = initial.phone
            _selectedThemeColor.value = initial.themeColor.ifBlank { "blue" }
        }
    }

    fun onCenterNameChange(name: String) {
        _centerName.value = name
    }

    fun onTeacherNameChange(name: String) {
        _teacherName.value = name
    }

    fun onPhoneChange(ph: String) {
        _phone.value = ph
    }

    fun onThemeColorChange(colorKey: String) {
        _selectedThemeColor.value = colorKey
        viewModelScope.launch {
            try {
                val current = settingsRepository.getSettings()
                val updated = current.copy(
                    themeColor = colorKey
                )
                settingsRepository.saveSettings(updated)
            } catch (_: Exception) {}
        }
    }

    fun saveSettings(
        onSuccess: () -> Unit = {}
    ) {
        _isSaving.value = true
        viewModelScope.launch {
            try {
                val current = settingsRepository.getSettings()
                val updated = current.copy(
                    centerName = _centerName.value.trim(),
                    teacherName = _teacherName.value.trim(),
                    phone = _phone.value.trim(),
                    themeColor = _selectedThemeColor.value,
                    isSetupComplete = true
                )
                settingsRepository.saveSettings(updated)

                // Update admin teacher user in database
                val adminUser = userRepository.getUserByUsername("admin")
                if (adminUser != null && _teacherName.value.isNotBlank()) {
                    database.userDao().updateUser(
                        adminUser.copy(
                            fullName = _teacherName.value.trim(),
                            phone = _phone.value.trim()
                        )
                    )
                }

                _isSaving.value = false
                Toast.makeText(getApplication(), "تم حفظ التعديلات بنجاح ✅", Toast.LENGTH_SHORT).show()
                onSuccess()
            } catch (e: Exception) {
                _isSaving.value = false
                Toast.makeText(getApplication(), "حدث خطأ: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
