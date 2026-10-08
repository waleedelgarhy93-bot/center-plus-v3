package com.example.ui.screens.auth

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.User
import com.example.ui.theme.CairoFontFamily

fun getThemeColor(colorKey: String): Color {
    return when (colorKey.lowercase().trim()) {
        "blue", "أزرق", "#2563eb" -> Color(0xFF2563EB)
        "green", "أخضر", "#10b981" -> Color(0xFF10B981)
        "purple", "بنفسجي", "#8b5cf6" -> Color(0xFF8B5CF6)
        "red", "أحمر", "#ef4444" -> Color(0xFFEF4444)
        "orange", "برتقالي", "#f97316" -> Color(0xFFF97316)
        "gray", "grey", "رمادي", "#6b7280" -> Color(0xFF6B7280)
        else -> {
            try {
                if (colorKey.startsWith("#")) {
                    Color(android.graphics.Color.parseColor(colorKey))
                } else {
                    Color(0xFF2563EB)
                }
            } catch (_: Exception) {
                Color(0xFF2563EB)
            }
        }
    }
}

@Composable
fun LoginScreen(
    onLoginSuccess: (role: String, user: User) -> Unit,
    onOpenSignup: () -> Unit,
    centerName: String = "سنتر لوبيتال",
    teacherName: String = "أ. وليد الجارحي",
    phone: String = "",
    logoPath: String? = null,
    themeHex: String = "#2563EB",
    isDarkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {},
    viewModel: LoginViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appSettings by viewModel.settings.collectAsStateWithLifecycle()

    val username by viewModel.username.collectAsStateWithLifecycle()
    val password by viewModel.password.collectAsStateWithLifecycle()
    val isPasswordVisible by viewModel.isPasswordVisible.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var showContactDialog by remember { mutableStateOf(false) }
    var showCreateOnlineStudentScreen by remember { mutableStateOf(false) }
    var pendingStudentUser by remember { mutableStateOf<User?>(null) }
    var pendingParentUser by remember { mutableStateOf<User?>(null) }
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        viewModel.loadSavedCredentialsIfPresent()
        if (viewModel.sessionManager.isLoggedIn) {
            val role = viewModel.sessionManager.role
            val uId = viewModel.sessionManager.userId
            val user = viewModel.getUserForActiveSession(uId)
            if (user != null) {
                if (role == "student_online" || role == User.ROLE_STUDENT_ONLINE) {
                    // الطالب الأونلاين يدخل مباشرة دون مسح كارت
                    onLoginSuccess(role, user)
                } else if (role == "student" || role == User.ROLE_STUDENT) {
                    pendingStudentUser = user
                } else if (role == "parent" || role == User.ROLE_PARENT) {
                    pendingParentUser = user
                }
            }
        }
    }

    val handleLoginSuccess: (String, User) -> Unit = { role, user ->
        when (role) {
            "teacher", User.ROLE_TEACHER -> {
                // دخول مباشر لـ TeacherDashboard
                onLoginSuccess(role, user)
            }
            "student_online", User.ROLE_STUDENT_ONLINE -> {
                // دخول مباشر لـ StudentOnlineHome (مفيش عنده كارت)
                onLoginSuccess(role, user)
            }
            "student", User.ROLE_STUDENT -> {
                // طالب السنتر: مسح الكارت
                pendingStudentUser = user
            }
            "parent", User.ROLE_PARENT -> {
                // ولي الأمر: مسح كارت ابنه
                pendingParentUser = user
            }
            else -> {
                onLoginSuccess(role, user)
            }
        }
    }

    // قراءة البيانات من جدول app_settings مع القيم الافتراضية
    val rawCenter = appSettings.centerName.ifBlank { centerName.ifBlank { "سنتر لوبيتال" } }
    val rawTeacher = appSettings.teacherName.ifBlank { teacherName.ifBlank { "أ. وليد الجارحي" } }
    val rawPhone = appSettings.phone.ifBlank { phone }
    val rawTheme = appSettings.themeColor.ifBlank { themeHex.ifBlank { "blue" } }

    val displayCenterName = if (rawCenter.startsWith("سنتر")) rawCenter else "سنتر $rawCenter"
    val displayTeacherName = if (rawTeacher.startsWith("أ.") || rawTeacher.startsWith("أستاذ") || rawTeacher.startsWith("م.")) {
        rawTeacher
    } else {
        "أ. $rawTeacher"
    }
    val primaryColor = getThemeColor(rawTheme)

    if (pendingStudentUser != null) {
        ScanCardScreen(
            authenticatedUser = pendingStudentUser!!,
            onVerified = {
                val u = pendingStudentUser!!
                pendingStudentUser = null
                onLoginSuccess("student", u)
            },
            onCancel = {
                pendingStudentUser = null
            }
        )
    } else if (pendingParentUser != null) {
        ParentScanCardScreen(
            parentUser = pendingParentUser!!,
            onVerified = { studentName ->
                val u = pendingParentUser!!
                pendingParentUser = null
                onLoginSuccess("parent", u)
            },
            onCancel = {
                pendingParentUser = null
            }
        )
    } else if (showCreateOnlineStudentScreen) {
        SignupScreen(
            onBack = {
                showCreateOnlineStudentScreen = false
                viewModel.loadSavedCredentialsIfPresent()
            }
        )
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .testTag("login_screen"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Theme toggle button at top corner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .size(42.dp)
                ) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "تبديل المظهر",
                        tint = primaryColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // عرض الشعار (افتراضي 🎓)
            Surface(
                modifier = Modifier.size(90.dp),
                shape = CircleShape,
                color = primaryColor.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(2.dp, primaryColor.copy(alpha = 0.5f))
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = "🎓",
                        fontSize = 44.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // عرض "سنتر [centerName]" (مثال: "سنتر لوبيتال")
            Text(
                text = displayCenterName,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            // عرض "أ. [teacherName]" (مثال: "أ. وليد الجارحي")
            Text(
                text = displayTeacherName,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryColor
                ),
                textAlign = TextAlign.Center
            )

            // عرض رقم الموبايل (لو موجود)
            if (rawPhone.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = rawPhone,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = CairoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Text(
                text = "تسجيل الدخول للنظام (معلم • طالب • ولي أمر)",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = CairoFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Error Banner
            errorMessage?.let { error ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        )
                    }
                }
            }

            // Username Field
            OutlinedTextField(
                value = username,
                onValueChange = viewModel::onUsernameChange,
                label = { Text("اسم المستخدم (Username)", fontFamily = CairoFontFamily) },
                placeholder = { Text("أدخل اسم المستخدم", fontFamily = CairoFontFamily) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = primaryColor
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryColor,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_username_input")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Password Field with Eye Toggle
            OutlinedTextField(
                value = password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("كلمة المرور (Password)", fontFamily = CairoFontFamily) },
                placeholder = { Text("أدخل كلمة المرور", fontFamily = CairoFontFamily) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = primaryColor
                    )
                },
                trailingIcon = {
                    IconButton(onClick = viewModel::togglePasswordVisibility) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isPasswordVisible) "إخفاء كلمة المرور" else "إظهار كلمة المرور",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        viewModel.login { role, user ->
                            handleLoginSuccess(role, user)
                        }
                    }
                ),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryColor,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_password_input")
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 1. زر "تسجيل الدخول" (مطبق عليه لون السمة المحفوظ)
            Button(
                onClick = {
                    viewModel.login { role, user ->
                        handleLoginSuccess(role, user)
                    }
                },
                enabled = !isLoading,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("login_submit_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Login, contentDescription = null)
                        Text(
                            text = "تسجيل الدخول",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // "ليس لديك حساب؟ تواصل مع المعلم" Link
            Text(
                text = "ليس لديك حساب؟ تواصل مع المعلم",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryColor
                ),
                modifier = Modifier
                    .clickable { showContactDialog = true }
                    .padding(6.dp)
                    .testTag("contact_teacher_link")
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Quick Credentials for immediate testing
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "حسابات تجريبية سريعة (اضغط للتعبئة الفورية):",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        QuickLoginPill(
                            label = "معلم",
                            details = "admin",
                            color = primaryColor,
                            onClick = { viewModel.setQuickCredentials("admin", "admin123") },
                            modifier = Modifier.weight(1f)
                        )
                        QuickLoginPill(
                            label = "سنتر",
                            details = "student",
                            color = Color(0xFF06B6D4),
                            onClick = { viewModel.setQuickCredentials("student", "student123") },
                            modifier = Modifier.weight(1f)
                        )
                        QuickLoginPill(
                            label = "أونلاين",
                            details = "mohamed",
                            color = Color(0xFF10B981),
                            onClick = { viewModel.setQuickCredentials("mohamed_online", "password123") },
                            modifier = Modifier.weight(1f)
                        )
                        QuickLoginPill(
                            label = "ولي أمر",
                            details = "parent",
                            color = Color(0xFFF59E0B),
                            onClick = { viewModel.setQuickCredentials("parent", "parent123") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2. زر "إنشاء حساب جديد" (مطبق عليه لون السمة المحفوظ)
            Button(
                onClick = {
                    viewModel.clearSavedCredentials()
                    onOpenSignup()
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("create_new_account_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null
                    )
                    Text(
                        text = "إنشاء حساب جديد",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Contact Teacher Dialog
    if (showContactDialog) {
        AlertDialog(
            onDismissRequest = { showContactDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "التواصل مع إدارة $displayCenterName",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "للحصول على اسم المستخدم وكلمة المرور الخاصة بك أو للتسجيل، يرجى التواصل مع $displayTeacherName:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = CairoFontFamily),
                        textAlign = TextAlign.Center
                    )
                    if (rawPhone.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "الهاتف: $rawPhone",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = CairoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryColor
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (rawPhone.isNotBlank()) {
                    TextButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:$rawPhone")
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                            }
                            showContactDialog = false
                        }
                    ) {
                        Text("اتصال", fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = primaryColor)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showContactDialog = false }) {
                    Text("إغلاق", fontFamily = CairoFontFamily)
                }
            }
        )
    }
}

@Composable
private fun QuickLoginPill(
    label: String,
    details: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            )
            Text(
                text = details,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = CairoFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            )
        }
    }
}
