package com.example.ui.screens.exams

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.AppDatabase
import com.example.ui.components.rememberAppSettings
import com.example.ui.components.resolveThemeColor
import com.example.ui.theme.CairoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ExamCardItem(
    val id: Long,
    val name: String,
    val subjectId: Long,
    val subjectName: String,
    val subjectColor: String,
    val date: String,
    val totalScore: Int,
    val studentsCount: Int,
    val notes: String = ""
)

@Composable
fun ManageExamsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val database = remember { AppDatabase.getDatabase(context) }

    val settings = rememberAppSettings()
    val primaryColor = resolveThemeColor(settings.themeColor)

    // Collect exams flow from Room
    val examsWithSubject by database.examDao().getAllExamsWithSubject().collectAsState(initial = emptyList())
    val examsList = remember(examsWithSubject) {
        examsWithSubject.map {
            ExamCardItem(
                id = it.id,
                name = it.title,
                subjectId = it.subjectId,
                subjectName = it.subjectName,
                subjectColor = it.subjectColor,
                date = it.date,
                totalScore = it.maxGrade,
                studentsCount = it.studentsCount,
                notes = it.notes ?: ""
            )
        }
    }

    // State for opening sub-screens
    var isAddingExam by remember { mutableStateOf(false) }
    var editingExam by remember { mutableStateOf<ExamCardItem?>(null) }
    var gradingExam by remember { mutableStateOf<ExamCardItem?>(null) }
    var examToDelete by remember { mutableStateOf<ExamCardItem?>(null) }

    // Delete Confirmation Dialog
    if (examToDelete != null) {
        val exam = examToDelete!!
        AlertDialog(
            onDismissRequest = { examToDelete = null },
            title = {
                Text(
                    text = "تأكيد حذف الاختبار",
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "هل أنت متأكد من حذف اختبار \"${exam.name}\"؟ لا يمكن التراجع عن هذا الإجراء.",
                    fontFamily = CairoFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentExam = exam
                        examToDelete = null
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                database.examDao().deleteExamById(currentExam.id)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "تم حذف الاختبار بنجاح ✅", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "فشل الحذف: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White
                    )
                ) {
                    Text("حذف", fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { examToDelete = null }) {
                    Text("إلغاء", fontFamily = CairoFontFamily)
                }
            }
        )
    }

    if (gradingExam != null) {
        val exam = gradingExam!!
        ExamGradesScreen(
            onBack = { gradingExam = null },
            examId = exam.id,
            examTitle = exam.name,
            subjectName = exam.subjectName,
            examDate = exam.date,
            maxGrade = exam.totalScore,
            subjectId = exam.subjectId
        )
    } else if (isAddingExam || editingExam != null) {
        val currentEdit = editingExam
        AddEditExamScreen(
            onBack = {
                isAddingExam = false
                editingExam = null
            },
            initialExamId = currentEdit?.id,
            initialExamName = currentEdit?.name ?: "",
            initialSubjectId = currentEdit?.subjectId ?: 1L,
            initialSubject = currentEdit?.subjectName ?: "رياضيات",
            initialDate = currentEdit?.date ?: "",
            initialTotalScore = currentEdit?.totalScore?.toString() ?: "20",
            initialNotes = currentEdit?.notes ?: "",
            isEditing = currentEdit != null
        )
    } else {
        Scaffold(
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { isAddingExam = true },
                    containerColor = primaryColor,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .padding(16.dp)
                        .testTag("add_exam_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "إضافة اختبار جديد",
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding()
                .testTag("manage_exams_screen")
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                // Top Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("exams_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Text(
                        text = "إدارة الاختبارات",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
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

                Spacer(modifier = Modifier.height(16.dp))

                // Stats Banner
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = primaryColor.copy(alpha = 0.1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, primaryColor.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${examsList.size}",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = CairoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryColor
                                )
                            )
                            Text(
                                text = "إجمالي الاختبارات",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = CairoFontFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }

                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .width(1.dp)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${examsList.sumOf { it.studentsCount }}",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = CairoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            )
                            Text(
                                text = "إجمالي الطلاب المُمتحنين",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = CairoFontFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // List of Exams
                if (examsList.isEmpty()) {
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
                                imageVector = Icons.Default.Assignment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(54.dp)
                            )
                            Text(
                                text = "لا توجد اختبارات مسجلة حالياً",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = CairoFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Text(
                                text = "اضغط على زر (+) لإضافة اختبار جديد",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = CairoFontFamily,
                                    color = primaryColor
                                )
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(examsList, key = { it.id }) { exam ->
                            ExamCard(
                                exam = exam,
                                primaryColor = primaryColor,
                                onEdit = { editingExam = exam },
                                onDelete = { examToDelete = exam },
                                onEnterGrades = { gradingExam = exam }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamCard(
    exam: ExamCardItem,
    primaryColor: Color,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onEnterGrades: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("exam_card_${exam.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Exam Name + Edit/Delete Icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = exam.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp
                    ),
                    modifier = Modifier.weight(1f)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Edit Icon
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("edit_exam_${exam.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "تعديل",
                            tint = primaryColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Delete Icon
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("delete_exam_${exam.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "حذف",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Subject Badge & Date
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
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = exam.subjectName,
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
                        text = exam.date,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = CairoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            // Notes if any
            if (exam.notes.isNotBlank()) {
                Text(
                    text = exam.notes,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = CairoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 2
                )
            }

            // Bottom Info: الدرجة الكاملة + عدد الطلاب
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // الدرجة الكاملة
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Grade,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "الدرجة الكاملة: ${exam.totalScore} درجة",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }

                    // عدد الطلاب اللي أدوا الاختبار
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${exam.studentsCount} طالب",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor
                            )
                        )
                    }
                }
            }

            // زر إدخال الدرجات
            Button(
                onClick = onEnterGrades,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor.copy(alpha = 0.15f),
                    contentColor = primaryColor
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("enter_grades_button_${exam.id}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "إدخال الدرجات",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    )
                }
            }
        }
    }
}
