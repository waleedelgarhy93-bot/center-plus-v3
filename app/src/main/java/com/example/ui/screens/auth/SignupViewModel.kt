package com.example.ui.screens.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.Student
import com.example.data.model.User
import com.example.data.repository.StudentRepository
import com.example.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SignupViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val userRepository = UserRepository(database.userDao())
    private val studentRepository = StudentRepository(database.studentDao())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private fun transliterateArabic(name: String): String {
        val trimmed = name.trim()
        val firstWord = trimmed.split("\\s+".toRegex()).firstOrNull()?.trim() ?: "student"

        val commonNames = mapOf(
            "أحمد" to "ahmed", "احمد" to "ahmed",
            "محمد" to "mohamed", "محمود" to "mahmoud",
            "علي" to "ali", "على" to "ali",
            "عمر" to "omar", "عمرو" to "amr",
            "سارة" to "sara", "ساره" to "sara",
            "مريم" to "mariam", "يوسف" to "youssef",
            "خالد" to "khaled", "مصطفى" to "mostafa",
            "إبراهيم" to "ibrahim", "ابراهيم" to "ibrahim",
            "حسن" to "hassan", "حسين" to "hussein",
            "عبدالله" to "abdallah", "عبد الله" to "abdallah",
            "طارق" to "tarek", "كريم" to "karim",
            "وليد" to "waleed", "ياسر" to "yasser",
            "زياد" to "ziad", "بلال" to "belal",
            "حمزة" to "hamza", "حمزه" to "hamza"
        )

        commonNames[firstWord]?.let { return it }

        val charMap = mapOf(
            'أ' to "a", 'إ' to "e", 'ا' to "a", 'آ' to "a", 'ء' to "a",
            'ب' to "b", 'ت' to "t", 'ث' to "th", 'ج' to "g", 'ح' to "h",
            'خ' to "kh", 'د' to "d", 'ذ' to "z", 'ر' to "r", 'ز' to "z",
            'س' to "s", 'ش' to "sh", 'ص' to "s", 'ض' to "d", 'ط' to "t",
            'ظ' to "z", 'ع' to "a", 'غ' to "gh", 'ف' to "f", 'ق' to "k",
            'ك' to "k", 'ل' to "l", 'م' to "m", 'ن' to "n", 'ه' to "h",
            'و' to "w", 'ي' to "y", 'ى' to "a", 'ة' to "a"
        )

        val sb = StringBuilder()
        for (ch in firstWord) {
            val mapped = charMap[ch]
            if (mapped != null) {
                sb.append(mapped)
            } else if (ch.isLetterOrDigit()) {
                sb.append(ch.lowercaseChar())
            }
        }
        val clean = sb.toString().trim().replace("[^a-z0-9_]".toRegex(), "")
        return if (clean.isNotBlank()) clean else "student"
    }

    private suspend fun isPhoneUsed(phone: String): Boolean = withContext(Dispatchers.IO) {
        val clean = phone.trim()
        database.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM users WHERE TRIM(phone) = ?",
            arrayOf(clean)
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) > 0 else false
        }
    }

    fun register(
        fullName: String,
        phone: String,
        password: String,
        confirmPassword: String,
        onValidationError: (String) -> Unit,
        onSuccess: (String) -> Unit
    ) {
        val cleanName = fullName.trim()
        val cleanPhone = phone.trim()
        val cleanPass = password.trim()
        val cleanConfirmPass = confirmPassword.trim()

        // 1. التحقق من أن جميع الحقول مطلوبة (الاسم، الموبايل، كلمة المرور، تأكيد كلمة المرور)
        if (cleanName.isEmpty() || cleanPhone.isEmpty() || cleanPass.isEmpty() || cleanConfirmPass.isEmpty()) {
            onValidationError("جميع الحقول مطلوبة")
            return
        }

        // 2. التحقق من أن كلمة المرور وتأكيدها متطابقان
        if (cleanPass != cleanConfirmPass) {
            onValidationError("كلمتا المرور غير متطابقتين")
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                // 3. التحقق من أن الموبايل غير مستخدم من قبل
                if (isPhoneUsed(cleanPhone)) {
                    _isLoading.value = false
                    onValidationError("رقم الموبايل مسجل بالفعل مسبقًا")
                    return@launch
                }

                // 4. توليد Username تلقائيًا: من الاسم + "_online"
                val base = transliterateArabic(cleanName)
                var candidate = "${base}_online"
                var counter = 2
                while (withContext(Dispatchers.IO) { userRepository.getUserByUsername(candidate) != null }) {
                    candidate = "${base}_online_$counter"
                    counter++
                }

                val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(Date())

                // 5. إضافة الطالب في جدول students
                // name: الاسم, phone: الموبايل, type: "online", barcode: null
                val student = Student(
                    name = cleanName,
                    phone = cleanPhone,
                    barcode = null,
                    parentPhone = cleanPhone,
                    groupName = "طالب أونلاين",
                    notes = "تسجيل ذاتي عبر المنصة",
                    type = "online"
                )
                val studentId = studentRepository.insertStudent(student)

                // 6. إضافة الحساب في قاعدة البيانات users
                // username: المُولّد, password: اللي كتبه الطالب, role: "student_online",
                // fullName: الاسم, phone: الموبايل, isActive: false (غير مفعّل)
                val user = User(
                    username = candidate,
                    password = cleanPass,
                    role = "student_online",
                    fullName = cleanName,
                    phone = cleanPhone,
                    studentId = studentId,
                    isActive = false,
                    createdAt = now
                )
                userRepository.insertUser(user)

                _isLoading.value = false
                onSuccess(candidate)
            } catch (e: Exception) {
                _isLoading.value = false
                onValidationError("حدث خطأ أثناء إنشاء الحساب: ${e.message}")
            }
        }
    }
}
