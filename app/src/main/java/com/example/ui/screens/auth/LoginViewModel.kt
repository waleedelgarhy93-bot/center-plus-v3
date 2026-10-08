package com.example.ui.screens.auth

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.AppSettings
import com.example.data.model.User
import com.example.data.repository.SettingsRepository
import com.example.data.repository.UserRepository
import com.example.data.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = UserRepository(database.userDao())
    private val settingsRepository = SettingsRepository(database.appSettingsDao())
    val sessionManager = SessionManager(application)

    val settings: StateFlow<AppSettings> = settingsRepository.getSettingsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings(
                id = 1L,
                centerName = "سنتر لوبيتال",
                teacherName = "أ. وليد الجارحي",
                phone = "",
                themeColor = "blue",
                isSetupComplete = false
            )
        )

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _isPasswordVisible = MutableStateFlow(false)
    val isPasswordVisible: StateFlow<Boolean> = _isPasswordVisible.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        loadSavedCredentialsIfPresent()
        viewModelScope.launch {
            repository.ensureDefaultUsers()
            try {
                settingsRepository.getSettings()
            } catch (_: Exception) {}
        }
    }

    fun loadSavedCredentialsIfPresent() {
        if (sessionManager.isCredentialsSaved) {
            val savedUser = sessionManager.savedUsername
            val savedPass = sessionManager.savedPassword
            if (savedUser.isNotBlank() && savedPass.isNotBlank()) {
                _username.value = savedUser
                _password.value = savedPass
            }
        }
    }

    fun clearSavedCredentials() {
        sessionManager.clearSavedCredentials()
        _username.value = ""
        _password.value = ""
        _errorMessage.value = null
    }

    fun onUsernameChange(newVal: String) {
        _username.value = newVal
        _errorMessage.value = null
    }

    fun onPasswordChange(newVal: String) {
        _password.value = newVal
        _errorMessage.value = null
    }

    fun togglePasswordVisibility() {
        _isPasswordVisible.value = !_isPasswordVisible.value
    }

    fun setQuickCredentials(user: String, pass: String) {
        _username.value = user
        _password.value = pass
        _errorMessage.value = null
    }

    fun login(onSuccess: (role: String, user: User) -> Unit) {
        val u = _username.value.trim()
        val p = _password.value.trim()

        if (u.isEmpty()) {
            _errorMessage.value = "يرجى إدخال اسم المستخدم"
            return
        }
        if (p.isEmpty()) {
            _errorMessage.value = "يرجى إدخال كلمة المرور"
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            var user = repository.login(u, p)
            if (user == null) {
                repository.ensureDefaultUsers()
                user = repository.login(u, p)
            }
            _isLoading.value = false

            if (user != null) {
                sessionManager.saveCredentials(u, p)
                if (user.role == "teacher" || user.role == User.ROLE_TEACHER) {
                    sessionManager.saveSession(user)
                }
                Toast.makeText(getApplication(), "مرحبًا بك يا ${user.fullName}", Toast.LENGTH_SHORT).show()
                onSuccess(user.role, user)
            } else {
                Toast.makeText(getApplication(), "بيانات الدخول غير صحيحة", Toast.LENGTH_SHORT).show()
                _errorMessage.value = "بيانات الدخول غير صحيحة"
            }
        }
    }

    suspend fun getUserForActiveSession(userId: Long): User? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        if (userId > 0) {
            database.userDao().getUserById(userId)
        } else {
            val u = sessionManager.savedUsername
            if (u.isNotBlank()) database.userDao().getUserByUsername(u) else null
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        sessionManager.clearSession()
        loadSavedCredentialsIfPresent()
        onLoggedOut()
    }
}
