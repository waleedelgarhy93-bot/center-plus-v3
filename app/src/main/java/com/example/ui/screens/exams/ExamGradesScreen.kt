package com.example.ui.screens.exams

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.database.AppDatabase
import com.example.ui.components.rememberAppSettings
import com.example.ui.components.resolveThemeColor
import com.example.ui.theme.CairoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

@Composable
fun ExamGradesScreen(
    onBack: () -> Unit,
    examId: Long,
    examTitle: String,
    subjectName: String,
    examDate: String,
    maxGrade: Int,
    subjectId: Long,
    modifier: Modifier = Modifier,
    viewModel: ExamGradesViewModel = viewModel()
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val settings = rememberAppSettings()
    val primaryColor = resolveThemeColor(settings.themeColor)
    val coroutineScope = rememberCoroutineScope()
    val database = remember { AppDatabase.getDatabase(context) }

    LaunchedEffect(examId, subjectId, maxGrade) {
        viewModel.initExam(examId, subjectId, maxGrade)
    }

    val studentsGrades by viewModel.studentsGrades.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()

    val gradedCount = studentsGrades.count { it.isSaved || (it.gradeText.isNotBlank() && it.gradeText.toDoubleOrNull() != null) }

    Scaffold(
        bottomBar = {
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.saveAllGrades(
                                onSuccess = {
                                    Toast.makeText(context, "تم حفظ الدرجات بنجاح ✅", Toast.LENGTH_SHORT).show()
                                },
                                onError = { msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        enabled = !isSaving && studentsGrades.isNotEmpty(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryColor,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("save_all_grades_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "حفظ الكل",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = CairoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("exam_grades_screen")
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Top App Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("exam_grades_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                Text(
                    text = "درجات اختبار: $examTitle",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 18.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )

                Surface(
                    shape = CircleShape,
                    color = primaryColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Grade,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Exam Info Card (المادة + التاريخ + الدرجة الكاملة + تقدم الرصد)
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = primaryColor.copy(alpha = 0.08f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    primaryColor.copy(alpha = 0.25f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // اسم المادة
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = primaryColor.copy(alpha = 0.15f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = subjectName,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = CairoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryColor
                                    )
                                )
                            }
                        }

                        // التاريخ
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = examDate,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = CairoFontFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }

                    // الدرجة الكاملة وإحصائية الطلاب
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Grade,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "الدرجة الكاملة: $maxGrade درجة",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = CairoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }

                        Text(
                            text = "تم رصد $gradedCount من ${studentsGrades.size} طالب",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryColor
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Students List
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = primaryColor)
                }
            } else if (studentsGrades.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(54.dp)
                        )
                        Text(
                            text = "لا يوجد طلاب مسجلين في هذه المادة",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(studentsGrades, key = { it.studentId }) { studentItem ->
                        StudentGradeRow(
                            studentItem = studentItem,
                            maxGrade = maxGrade,
                            primaryColor = primaryColor,
                            onGradeChange = { newText ->
                                viewModel.onGradeChange(studentItem.studentId, newText)
                            },
                            onSave = {
                                viewModel.autoSaveStudentGrade(studentItem.studentId) {
                                    Toast.makeText(context, "تم الحفظ", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onSendWhatsApp = {
                                coroutineScope.launch {
                                    if (!studentItem.isSaved && studentItem.gradeText.isNotBlank()) {
                                        viewModel.autoSaveStudentGrade(studentItem.studentId) {}
                                    }
                                    val student = withContext(Dispatchers.IO) {
                                        database.studentDao().getStudentById(studentItem.studentId)
                                    }
                                    val parentPhone = student?.parentPhone ?: ""
                                    val gradeVal = studentItem.gradeText.toDoubleOrNull() ?: 0.0

                                    sendGradeWhatsAppMessage(
                                        context = context,
                                        studentName = studentItem.studentName,
                                        parentPhone = parentPhone,
                                        examTitle = examTitle,
                                        subjectName = subjectName,
                                        grade = gradeVal,
                                        maxGrade = maxGrade,
                                        centerName = settings.centerName
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentGradeRow(
    studentItem: StudentGradeUiItem,
    maxGrade: Int,
    primaryColor: Color,
    onGradeChange: (String) -> Unit,
    onSave: () -> Unit,
    onSendWhatsApp: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (studentItem.isError) MaterialTheme.colorScheme.error
            else if (studentItem.isSaved) Color(0xFF10B981).copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("student_grade_row_${studentItem.studentId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Student Info (Avatar + Name + Group)
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = primaryColor.copy(alpha = 0.12f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = studentItem.studentName.take(1),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor
                            )
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = studentItem.studentName,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (studentItem.groupName.isNotBlank()) {
                        Text(
                            text = studentItem.groupName,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = CairoFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Grade Input + Save Indicator + WhatsApp Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // WhatsApp Button (Only visible after grade is entered)
                val hasGrade = studentItem.gradeText.isNotBlank() && studentItem.gradeText.toDoubleOrNull() != null
                if (hasGrade) {
                    IconButton(
                        onClick = onSendWhatsApp,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF25D366).copy(alpha = 0.15f), CircleShape)
                            .testTag("send_whatsapp_${studentItem.studentId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = "إرسال واتساب لولي الأمر",
                            tint = Color(0xFF25D366),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Saved checkmark indicator
                if (studentItem.isSaved) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "تم الحفظ",
                        tint = Color(0xFF10B981),
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("saved_grade_indicator_${studentItem.studentId}")
                    )
                } else if (studentItem.gradeText.isNotBlank()) {
                    // Quick Save Button
                    IconButton(
                        onClick = onSave,
                        modifier = Modifier
                            .size(32.dp)
                            .background(primaryColor.copy(alpha = 0.15f), CircleShape)
                            .testTag("quick_save_${studentItem.studentId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "حفظ الدرجة",
                            tint = primaryColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(24.dp))
                }

                // Numeric Grade TextField
                OutlinedTextField(
                    value = studentItem.gradeText,
                    onValueChange = onGradeChange,
                    placeholder = {
                        Text(
                            text = "$maxGrade",
                            fontFamily = CairoFontFamily,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            onSave()
                        }
                    ),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = if (studentItem.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (studentItem.isError) MaterialTheme.colorScheme.error else primaryColor,
                        unfocusedBorderColor = if (studentItem.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .width(82.dp)
                        .height(52.dp)
                        .onFocusChanged { focusState ->
                            if (!focusState.isFocused && studentItem.gradeText.isNotBlank() && !studentItem.isSaved && !studentItem.isError) {
                                onSave()
                            }
                        }
                        .testTag("grade_input_${studentItem.studentId}")
                )
            }
        }
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
 * Dispatches an ACTION_VIEW intent to open WhatsApp with the student's exam grade
 */
private fun sendGradeWhatsAppMessage(
    context: Context,
    studentName: String,
    parentPhone: String,
    examTitle: String,
    subjectName: String,
    grade: Double,
    maxGrade: Int,
    centerName: String
) {
    val cleanDigits = parentPhone.filter { it.isDigit() }
    if (cleanDigits.isBlank()) {
        Toast.makeText(context, "رقم واتساب ولي الأمر غير مسجل", Toast.LENGTH_SHORT).show()
        return
    }

    val formattedPhone = formatEgyptianWhatsAppNumber(cleanDigits)

    val gradeDisplay = if (grade % 1.0 == 0.0) grade.toInt().toString() else grade.toString()
    val percentage = if (maxGrade > 0) ((grade / maxGrade) * 100.0).roundToInt() else 0

    val cleanCenter = centerName.trim()
    val centerDisplay = when {
        cleanCenter.isBlank() -> "سنتر لوبيتال"
        cleanCenter.startsWith("سنتر") -> cleanCenter
        else -> "سنتر $cleanCenter"
    }

    val message = """
مرحباً،
نود إعلامكم بنتيجة اختبار $examTitle لمادة $subjectName لابنكم/ابنتكم $studentName.

الدرجة: $gradeDisplay من $maxGrade
النسبة: $percentage%

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

