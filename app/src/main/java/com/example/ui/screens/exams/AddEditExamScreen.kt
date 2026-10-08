package com.example.ui.screens.exams

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.AppDatabase
import com.example.data.model.Exam
import com.example.data.model.Subject
import com.example.ui.components.rememberAppSettings
import com.example.ui.components.resolveThemeColor
import com.example.ui.theme.CairoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditExamScreen(
    onBack: () -> Unit,
    initialExamId: Long? = null,
    initialExamName: String = "",
    initialSubjectId: Long = 1L,
    initialSubject: String = "رياضيات",
    initialDate: String = "",
    initialTotalScore: String = "20",
    initialNotes: String = "",
    isEditing: Boolean = false,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val database = remember { AppDatabase.getDatabase(context) }
    val dbSubjects by database.subjectDao().getAllSubjects().collectAsState(initial = emptyList())

    val settings = rememberAppSettings()
    val primaryColor = resolveThemeColor(settings.themeColor)

    val defaultDate = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    var examName by remember { mutableStateOf(initialExamName) }
    var selectedSubjectId by remember { mutableLongStateOf(initialSubjectId) }
    var selectedSubjectName by remember { mutableStateOf(initialSubject) }
    var examDate by remember { mutableStateOf(if (initialDate.isNotBlank()) initialDate else defaultDate) }
    var totalScore by remember { mutableStateOf(initialTotalScore) }
    var notes by remember { mutableStateOf(initialNotes) }
    var isSaving by remember { mutableStateOf(false) }

    // Synchronize subject name if subjects loaded
    androidx.compose.runtime.LaunchedEffect(dbSubjects) {
        if (dbSubjects.isNotEmpty()) {
            val matching = dbSubjects.find { it.id == selectedSubjectId }
            if (matching != null) {
                selectedSubjectName = matching.name
            } else if (selectedSubjectId <= 0L) {
                selectedSubjectId = dbSubjects.first().id
                selectedSubjectName = dbSubjects.first().name
            }
        }
    }

    // Dropdown state for Subjects
    var isSubjectDropdownExpanded by remember { mutableStateOf(false) }
    val fallbackSubjects = remember {
        listOf("رياضيات", "فيزياء", "كيمياء", "أحياء", "لغة عربية", "لغة إنجليزية", "تاريخ", "جغرافيا")
    }

    // DatePicker state
    var showDatePickerDialog by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    if (showDatePickerDialog) {
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            examDate = formatter.format(Date(millis))
                        }
                        showDatePickerDialog = false
                    }
                ) {
                    Text("اختيار", fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("إلغاء", fontFamily = CairoFontFamily)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("add_edit_exam_screen")
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "رجوع",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            Text(
                text = if (isEditing) "تعديل الاختبار" else "إضافة اختبار جديد",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )

            Spacer(modifier = Modifier.size(48.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Form Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. حقل "اسم الاختبار" (TextField)
                OutlinedTextField(
                    value = examName,
                    onValueChange = { examName = it },
                    label = { Text("اسم الاختبار *", fontFamily = CairoFontFamily) },
                    placeholder = { Text("مثال: اختبار الفصل الأول", fontFamily = CairoFontFamily) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Assignment,
                            contentDescription = null,
                            tint = primaryColor
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exam_name_input")
                )

                // 2. حقل "المادة" (Dropdown من المواد)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedSubjectName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("المادة *", fontFamily = CairoFontFamily) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = null,
                                tint = primaryColor
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { isSubjectDropdownExpanded = !isSubjectDropdownExpanded }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "عرض المواد",
                                    tint = primaryColor
                                )
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isSubjectDropdownExpanded = true }
                            .testTag("exam_subject_dropdown")
                    )

                    DropdownMenu(
                        expanded = isSubjectDropdownExpanded,
                        onDismissRequest = { isSubjectDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        if (dbSubjects.isNotEmpty()) {
                            dbSubjects.forEach { subj ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = subj.name,
                                            fontFamily = CairoFontFamily,
                                            fontWeight = if (subj.id == selectedSubjectId) FontWeight.Bold else FontWeight.Normal,
                                            color = if (subj.id == selectedSubjectId) primaryColor else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        selectedSubjectId = subj.id
                                        selectedSubjectName = subj.name
                                        isSubjectDropdownExpanded = false
                                    }
                                )
                            }
                        } else {
                            fallbackSubjects.forEachIndexed { index, subjName ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = subjName,
                                            fontFamily = CairoFontFamily,
                                            fontWeight = if (subjName == selectedSubjectName) FontWeight.Bold else FontWeight.Normal,
                                            color = if (subjName == selectedSubjectName) primaryColor else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        selectedSubjectId = (index + 1).toLong()
                                        selectedSubjectName = subjName
                                        isSubjectDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 3. حقل "التاريخ" (DatePicker)
                OutlinedTextField(
                    value = examDate,
                    onValueChange = { examDate = it },
                    label = { Text("التاريخ *", fontFamily = CairoFontFamily) },
                    placeholder = { Text("YYYY-MM-DD", fontFamily = CairoFontFamily) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = primaryColor
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { showDatePickerDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = "اختيار التاريخ",
                                tint = primaryColor
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exam_date_input")
                )

                // 4. حقل "الدرجة الكاملة" (TextField، رقم)
                OutlinedTextField(
                    value = totalScore,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() }) {
                            totalScore = input
                        }
                    },
                    label = { Text("الدرجة الكاملة *", fontFamily = CairoFontFamily) },
                    placeholder = { Text("مثال: 20", fontFamily = CairoFontFamily) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Grade,
                            contentDescription = null,
                            tint = primaryColor
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exam_total_score_input")
                )

                // 5. حقل "ملاحظات" (TextField متعدد الأسطر، اختياري)
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات (اختياري)", fontFamily = CairoFontFamily) },
                    placeholder = { Text("أدخل أي تعليمات أو تفاصيل حول الاختبار...", fontFamily = CairoFontFamily) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Notes,
                            contentDescription = null,
                            tint = primaryColor
                        )
                    },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exam_notes_input")
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Action Buttons: زر "حفظ" و "إلغاء"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // زر "حفظ"
            Button(
                onClick = {
                    if (examName.isBlank()) {
                        Toast.makeText(context, "الرجاء إدخال اسم الاختبار", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val score = totalScore.toIntOrNull()
                    if (score == null || score <= 0) {
                        Toast.makeText(context, "الرجاء إدخال درجة صالحة للاختبار", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (examDate.isBlank()) {
                        Toast.makeText(context, "الرجاء تحديد تاريخ الاختبار", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isSaving = true
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            var targetSubjectId = selectedSubjectId
                            if (targetSubjectId <= 0L && dbSubjects.isNotEmpty()) {
                                targetSubjectId = dbSubjects.first().id
                            } else if (targetSubjectId <= 0L) {
                                targetSubjectId = database.subjectDao().insertSubject(
                                    Subject(
                                        name = selectedSubjectName.ifBlank { "رياضيات" },
                                        description = "مادة دراسية",
                                        icon = "school",
                                        color = "#2563EB"
                                    )
                                )
                            }

                            if (isEditing && initialExamId != null && initialExamId > 0) {
                                database.examDao().updateExam(
                                    Exam(
                                        id = initialExamId,
                                        title = examName.trim(),
                                        subjectId = targetSubjectId,
                                        date = examDate.trim(),
                                        maxGrade = score,
                                        notes = notes.trim().ifEmpty { null }
                                    )
                                )
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "تم تعديل الاختبار بنجاح ✅", Toast.LENGTH_SHORT).show()
                                    isSaving = false
                                    onBack()
                                }
                            } else {
                                database.examDao().insertExam(
                                    Exam(
                                        title = examName.trim(),
                                        subjectId = targetSubjectId,
                                        date = examDate.trim(),
                                        maxGrade = score,
                                        notes = notes.trim().ifEmpty { null }
                                    )
                                )
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "تم حفظ الاختبار بنجاح ✅", Toast.LENGTH_SHORT).show()
                                    isSaving = false
                                    onBack()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "حدث خطأ: ${e.localizedMessage ?: "فشل الحفظ"}", Toast.LENGTH_LONG).show()
                                isSaving = false
                            }
                        }
                    }
                },
                enabled = !isSaving,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .testTag("save_exam_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (isEditing) "حفظ التعديل" else "حفظ",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    )
                }
            }

            // زر "إلغاء"
            OutlinedButton(
                onClick = onBack,
                enabled = !isSaving,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .testTag("cancel_exam_button")
            ) {
                Text(
                    text = "إلغاء",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
