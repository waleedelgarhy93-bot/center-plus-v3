package com.example.ui.screens.studentview

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.VpnKey
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.AppDatabase
import com.example.data.model.StudentSubjectAccess
import com.example.data.model.SubjectWithGroupCount
import com.example.data.session.SessionManager
import com.example.ui.components.rememberAppSettings
import com.example.ui.theme.CairoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EnterCodeScreen(
    subjectName: String,
    subjectId: Long,
    onBack: () -> Unit,
    onCodeActivated: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { AppDatabase.getDatabase(context) }
    val sessionManager = remember { SessionManager(context) }
    val settings = rememberAppSettings()

    var inputCode by remember { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var activatedStudentName by remember { mutableStateOf("") }
    var activatedStudentPhone by remember { mutableStateOf("") }

    val primaryBlue = Color(0xFF2563EB)

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("enter_code_screen")
    ) {
        // Top Header: "تفعيل الاشتراك" + زر رجوع
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .size(42.dp)
                    .testTag("enter_code_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "رجوع",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "تفعيل الاشتراك",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Info Banner: نص: "أدخل كود الاشتراك الخاص بمادة [اسم المادة]"
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = primaryBlue.copy(alpha = 0.12f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.2.dp,
                primaryBlue.copy(alpha = 0.35f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(primaryBlue.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = null,
                        tint = primaryBlue,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "أدخل كود الاشتراك الخاص بمادة $subjectName",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "يتكون الكود من 8 خانات من الحروف والأرقام المسلمة لك من المعلم",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = CairoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // حقل كبير لإدخال الكود (8 خانات)
        OutlinedTextField(
            value = inputCode,
            onValueChange = { inputCode = it.uppercase() },
            label = { Text("كود الاشتراك (8 خانات)", fontFamily = CairoFontFamily) },
            placeholder = { Text("مثال: A7B9K2M4", fontFamily = CairoFontFamily) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.VpnKey,
                    contentDescription = null,
                    tint = primaryBlue
                )
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = CairoFontFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center,
                color = primaryBlue
            ),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = primaryBlue,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .testTag("enter_code_input")
        )

        Spacer(modifier = Modifier.weight(1f))

        // أزرار: زر "إلغاء" + زر "تحقق"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // زر "إلغاء": يرجع لشاشة المواد بدون أي تعديل
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .testTag("cancel_code_button")
            ) {
                Text(
                    text = "إلغاء",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            // زر "تحقق"
            Button(
                onClick = {
                    val codeToVerify = inputCode.trim()
                    if (codeToVerify.isEmpty()) {
                        Toast.makeText(context, "يرجى إدخال الكود", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isChecking = true
                    scope.launch {
                        try {
                            val codeEntity = withContext(Dispatchers.IO) {
                                database.accessCodeDao().getCodeByString(codeToVerify)
                            }

                            // 1. التحقق من أن الكود موجود ولنفس المادة
                            if (codeEntity == null || codeEntity.subjectId != subjectId) {
                                isChecking = false
                                Toast.makeText(context, "الكود غير صحيح", Toast.LENGTH_SHORT).show()
                                return@launch
                            }

                            // 2. التحقق من أن الكود مش مستخدم من طالب تاني
                            if (codeEntity.usedByStudentId != null) {
                                isChecking = false
                                Toast.makeText(context, "هذا الكود مستخدم بالفعل", Toast.LENGTH_SHORT).show()
                                return@launch
                            }

                            // 3. كل حاجة تمام -> تفعيل الاشتراك
                            val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(Date())
                            var currentStudentId = sessionManager.studentId
                            val user = withContext(Dispatchers.IO) {
                                if (currentStudentId <= 0) {
                                    database.userDao().getUserByUsername(sessionManager.username)
                                } else {
                                    null
                                }
                            }
                            if (currentStudentId <= 0) {
                                currentStudentId = user?.studentId ?: 8L
                            }

                            val student = withContext(Dispatchers.IO) {
                                database.studentDao().getStudentById(currentStudentId)
                            }

                            activatedStudentName = student?.name ?: sessionManager.fullName.ifBlank { "طالبنا العزيز" }
                            activatedStudentPhone = student?.phone?.ifBlank { sessionManager.phone } ?: sessionManager.phone

                            withContext(Dispatchers.IO) {
                                // تحديث الكود: usedByStudentId = الطالب الحالي، usedAt = التاريخ
                                database.accessCodeDao().updateCode(
                                    codeEntity.copy(
                                        usedByStudentId = currentStudentId,
                                        usedAt = now
                                    )
                                )

                                // إضافة سجل في جدول student_subject_access
                                database.studentSubjectAccessDao().grantAccess(
                                    StudentSubjectAccess(
                                        studentId = currentStudentId,
                                        subjectId = subjectId,
                                        unlockedAt = now
                                    )
                                )
                            }

                            isChecking = false
                            showSuccessDialog = true
                        } catch (e: Exception) {
                            isChecking = false
                            Toast.makeText(context, "حدث خطأ: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !isChecking,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryBlue,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .weight(1.4f)
                    .height(54.dp)
                    .testTag("verify_code_button")
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text(
                        text = "تحقق",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Dialog: "تم تفعيل الاشتراك بنجاح ✅" + "المادة: [اسم المادة]" + زر "إرسال واتساب" + زر "حسنًا"
    if (showSuccessDialog) {
        val whatsAppGreen = Color(0xFF25D366)

        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                onCodeActivated()
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(46.dp)
                )
            },
            title = {
                Text(
                    text = "تم تفعيل الاشتراك بنجاح ✅",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = primaryBlue.copy(alpha = 0.12f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = primaryBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "المادة: $subjectName",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = CairoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryBlue
                                )
                            )
                        }
                    }

                    Text(
                        text = "تم فتح محتوى مادة $subjectName بالكامل. يمكنك الآن مشاهدة الفيديوهات وحل الاختبارات.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = CairoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        sendStudentActivationWhatsAppMessage(
                            context = context,
                            studentName = activatedStudentName,
                            studentPhone = activatedStudentPhone,
                            subjectName = subjectName,
                            centerName = settings.centerName
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = whatsAppGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("send_whatsapp_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "إرسال واتساب",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        onCodeActivated()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryBlue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("ok_button")
                ) {
                    Text(
                        text = "حسنًا",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        )
    }
}

/**
 * Format Egyptian phone number into international format (2010...)
 * Example: 01000000000 -> 201000000000
 */
private fun formatEgyptianWhatsAppNumber(rawPhone: String): String {
    val digits = rawPhone.filter { it.isDigit() }
    return when {
        digits.startsWith("0020") -> digits.substring(2)
        digits.startsWith("20") -> digits
        digits.startsWith("0") -> "20" + digits.substring(1)
        digits.length == 10 && digits.startsWith("1") -> "20$digits"
        else -> if (digits.startsWith("20")) digits else "20$digits"
    }
}

/**
 * Dispatches an ACTION_VIEW intent to open WhatsApp with subscription activation confirmation
 */
private fun sendStudentActivationWhatsAppMessage(
    context: Context,
    studentName: String,
    studentPhone: String,
    subjectName: String,
    centerName: String
) {
    val cleanDigits = studentPhone.filter { it.isDigit() }
    if (cleanDigits.isBlank()) {
        Toast.makeText(context, "رقم واتساب الطالب غير مسجل", Toast.LENGTH_SHORT).show()
        return
    }

    val formattedPhone = formatEgyptianWhatsAppNumber(cleanDigits)

    val cleanCenter = centerName.trim()
    val centerDisplay = when {
        cleanCenter.isBlank() -> "سنتر لوبيتال"
        cleanCenter.startsWith("سنتر") -> cleanCenter
        else -> "سنتر $cleanCenter"
    }

    val message = """
مرحباً $studentName،

تم تفعيل اشتراكك في مادة $subjectName بنجاح ✅

يمكنك الآن الدخول إلى التطبيق ومشاهدة الفيديوهات وحل الاختبارات.

مع تحيات،
$centerDisplay
    """.trimIndent()

    val encodedMessage = try {
        URLEncoder.encode(message, "UTF-8")
    } catch (e: Exception) {
        URLEncoder.encode(message, StandardCharsets.UTF_8.name())
    }

    val url = "https://wa.me/$formattedPhone?text=$encodedMessage"
    val uri = Uri.parse(url)

    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        } catch (e2: Exception) {
            Toast.makeText(context, "تعذر فتح واتساب أو المتصفح", Toast.LENGTH_SHORT).show()
        }
    }
}

// Overload for compatibility with existing code/screens
@Composable
fun EnterCodeScreen(
    subject: SubjectWithGroupCount,
    onBack: () -> Unit,
    onCodeActivated: () -> Unit,
    viewModel: StudentViewModel? = null,
    modifier: Modifier = Modifier
) {
    EnterCodeScreen(
        subjectName = subject.name,
        subjectId = subject.id,
        onBack = onBack,
        onCodeActivated = onCodeActivated,
        modifier = modifier
    )
}
