package com.example.ui.screens.auth

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.User
import com.example.data.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScanCardViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val sessionManager = SessionManager(application)

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var lastScanTime: Long = 0L

    fun onBarcodeDetected(
        rawBarcode: String,
        authenticatedUser: User,
        onSuccess: () -> Unit
    ) {
        val currentTime = System.currentTimeMillis()
        val trimmedCode = rawBarcode.trim()

        // 1. Cooldown: prevent reading any barcode within 3 seconds
        if ((currentTime - lastScanTime) < 3000L) {
            return
        }
        lastScanTime = currentTime

        if (_isProcessing.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            _statusMessage.value = null

            try {
                val digitsOnly = trimmedCode.filter { it.isDigit() }

                // 2. Search in students table for the student who owns this card number or barcode
                val student = database.studentDao().getStudentByCardOrBarcode(trimmedCode)
                    ?: (if (digitsOnly.isNotEmpty()) database.studentDao().getStudentByCardOrBarcode(digitsOnly) else null)

                if (student == null) {
                    withContext(Dispatchers.Main) {
                        _isProcessing.value = false
                        val error = "هذا الكارت لا يخص حسابك"
                        _statusMessage.value = error
                        Toast.makeText(getApplication(), error, Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // 3. Verification: هل اليوزر المحفوظ مربوط بالطالب ده؟
                val isMatch = when {
                    authenticatedUser.studentId != null && authenticatedUser.studentId == student.id -> true
                    authenticatedUser.username.trim().equals(student.barcode?.trim(), ignoreCase = true) -> true
                    authenticatedUser.username.trim().equals(student.cardNumber?.trim(), ignoreCase = true) -> true
                    database.userDao().getUserByStudentIdAndRole(student.id, "student")?.id == authenticatedUser.id -> true
                    database.userDao().getUserByStudentIdAndRole(student.id, "student_online")?.id == authenticatedUser.id -> true
                    database.userDao().getUserByStudentIdAndRole(student.id, "student")?.username?.equals(authenticatedUser.username.trim(), ignoreCase = true) == true -> true
                    else -> false
                }

                withContext(Dispatchers.Main) {
                    _isProcessing.value = false
                    if (isMatch) {
                        // Save session (userId, role, isLoggedIn=true, NO card number stored)
                        sessionManager.saveSession(authenticatedUser)
                        Toast.makeText(
                            getApplication(),
                            "تم التحقق من الكارت بنجاح ✅ أهلاً بك يا ${student.name}",
                            Toast.LENGTH_SHORT
                        ).show()
                        onSuccess()
                    } else {
                        val error = "هذا الكارت لا يخص حسابك"
                        _statusMessage.value = error
                        Toast.makeText(getApplication(), error, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _isProcessing.value = false
                    val error = "هذا الكارت لا يخص حسابك"
                    _statusMessage.value = error
                    Toast.makeText(getApplication(), error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
