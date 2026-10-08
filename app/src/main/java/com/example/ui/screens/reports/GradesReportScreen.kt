package com.example.ui.screens.reports

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.data.database.AppDatabase
import com.example.ui.components.rememberAppSettings
import com.example.ui.components.resolveThemeColor
import com.example.ui.theme.CairoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

data class SubjectFilterOption(val id: Long, val name: String)
data class ExamFilterOption(val id: Long, val title: String, val maxGrade: Int, val subjectId: Long)

data class StudentGradeReportItem(
    val studentId: Long,
    val studentName: String,
    val groupName: String,
    val examId: Long,
    val examTitle: String,
    val subjectName: String,
    val date: String,
    val score: Double,
    val maxScore: Int,
    val percentage: Int,
    val isPassed: Boolean
) {
    val scoreFormatted: String
        get() {
            val scoreStr = if (score % 1.0 == 0.0) score.toInt().toString() else score.toString()
            return "$scoreStr / $maxScore"
        }
}

data class StudentDetailedExamRecord(
    val examId: Long,
    val examTitle: String,
    val subjectName: String,
    val date: String,
    val score: Double,
    val maxScore: Int,
    val percentage: Int,
    val isPassed: Boolean
) {
    val scoreFormatted: String
        get() {
            val scoreStr = if (score % 1.0 == 0.0) score.toInt().toString() else score.toString()
            return "$scoreStr / $maxScore"
        }
}

@Composable
fun GradesReportScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val database = remember { AppDatabase.getDatabase(context) }

    val settings = rememberAppSettings()
    val primaryColor = resolveThemeColor(settings.themeColor)

    val passColor = Color(0xFF10B981) // أخضر
    val failColor = Color(0xFFEF4444) // أحمر

    // Options for dropdowns
    var subjectsList by remember { mutableStateOf<List<SubjectFilterOption>>(listOf(SubjectFilterOption(-1L, "كل المواد"))) }
    var allExamsList by remember { mutableStateOf<List<ExamFilterOption>>(emptyList()) }
    var filteredExamsList by remember { mutableStateOf<List<ExamFilterOption>>(listOf(ExamFilterOption(-1L, "كل الاختبارات", 0, -1L))) }

    var selectedSubject by remember { mutableStateOf(SubjectFilterOption(-1L, "كل المواد")) }
    var selectedExam by remember { mutableStateOf(ExamFilterOption(-1L, "كل الاختبارات", 0, -1L)) }

    var isSubjectDropdownExpanded by remember { mutableStateOf(false) }
    var isExamDropdownExpanded by remember { mutableStateOf(false) }

    // State for results
    var isLoading by remember { mutableStateOf(true) }
    var hasExamsInSystem by remember { mutableStateOf(true) }
    var reportResults by remember { mutableStateOf<List<StudentGradeReportItem>>(emptyList()) }

    // Summary metrics
    var highestScore by remember { mutableDoubleStateOf(0.0) }
    var lowestScore by remember { mutableDoubleStateOf(0.0) }
    var averageScore by remember { mutableDoubleStateOf(0.0) }
    var totalExaminees by remember { mutableIntStateOf(0) }

    // Sub-Screen: Details for a clicked student
    var selectedStudentIdForDetails by remember { mutableStateOf<Long?>(null) }
    var selectedStudentNameForDetails by remember { mutableStateOf("") }
    var studentDetailedExams by remember { mutableStateOf<List<StudentDetailedExamRecord>>(emptyList()) }
    var isLoadingStudentDetails by remember { mutableStateOf(false) }

    // Function to load dropdown options
    fun loadFilterOptions() {
        coroutineScope.launch(Dispatchers.IO) {
            val subs = mutableListOf(SubjectFilterOption(-1L, "كل المواد"))
            try {
                val subCursor = database.openHelper.readableDatabase.query(
                    SimpleSQLiteQuery("SELECT id, name FROM subjects ORDER BY id ASC")
                )
                subCursor.use {
                    while (it.moveToNext()) {
                        subs.add(SubjectFilterOption(it.getLong(0), it.getString(1)))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val exams = mutableListOf<ExamFilterOption>()
            try {
                val exCursor = database.openHelper.readableDatabase.query(
                    SimpleSQLiteQuery("SELECT id, title, maxGrade, subjectId FROM exams ORDER BY id DESC")
                )
                exCursor.use {
                    while (it.moveToNext()) {
                        exams.add(ExamFilterOption(it.getLong(0), it.getString(1), it.getInt(2), it.getLong(3)))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            withContext(Dispatchers.Main) {
                subjectsList = subs
                allExamsList = exams
                hasExamsInSystem = exams.isNotEmpty()
                val exOptions = mutableListOf(ExamFilterOption(-1L, "كل الاختبارات", 0, -1L))
                exOptions.addAll(exams)
                filteredExamsList = exOptions
            }
        }
    }

    // Function to execute the report query
    fun executeReport(subjectId: Long, examId: Long) {
        coroutineScope.launch(Dispatchers.IO) {
            isLoading = true
            val queryArgs = mutableListOf<Any>()
            val sql = StringBuilder(
                """
                SELECT 
                    st.id AS studentId,
                    st.name AS studentName,
                    st.groupName AS groupName,
                    e.id AS examId,
                    e.title AS examTitle,
                    COALESCE(s.name, 'مادة عامة') AS subjectName,
                    e.date AS examDate,
                    eg.grade AS studentGrade,
                    e.maxGrade AS maxGrade
                FROM exam_grades eg
                INNER JOIN exams e ON eg.examId = e.id
                INNER JOIN students st ON eg.studentId = st.id
                LEFT JOIN subjects s ON e.subjectId = s.id
                WHERE 1=1
                """
            )

            if (subjectId > 0) {
                sql.append(" AND e.subjectId = ?")
                queryArgs.add(subjectId)
            }
            if (examId > 0) {
                sql.append(" AND e.id = ?")
                queryArgs.add(examId)
            }
            sql.append(" ORDER BY eg.grade DESC, st.name ASC")

            val items = mutableListOf<StudentGradeReportItem>()
            try {
                val cursor = database.openHelper.readableDatabase.query(
                    SimpleSQLiteQuery(sql.toString(), queryArgs.toTypedArray())
                )
                cursor.use { c ->
                    val stIdIdx = c.getColumnIndex("studentId")
                    val stNameIdx = c.getColumnIndex("studentName")
                    val groupIdx = c.getColumnIndex("groupName")
                    val eIdIdx = c.getColumnIndex("examId")
                    val eTitleIdx = c.getColumnIndex("examTitle")
                    val sNameIdx = c.getColumnIndex("subjectName")
                    val dateIdx = c.getColumnIndex("examDate")
                    val gradeIdx = c.getColumnIndex("studentGrade")
                    val maxIdx = c.getColumnIndex("maxGrade")

                    while (c.moveToNext()) {
                        val sId = if (stIdIdx >= 0) c.getLong(stIdIdx) else 0L
                        val sName = if (stNameIdx >= 0) c.getString(stNameIdx) else ""
                        val gName = if (groupIdx >= 0) c.getString(groupIdx) ?: "" else ""
                        val eId = if (eIdIdx >= 0) c.getLong(eIdIdx) else 0L
                        val eTitle = if (eTitleIdx >= 0) c.getString(eTitleIdx) else ""
                        val subName = if (sNameIdx >= 0) c.getString(sNameIdx) else ""
                        val d = if (dateIdx >= 0) c.getString(dateIdx) else ""
                        val gr = if (gradeIdx >= 0) c.getDouble(gradeIdx) else 0.0
                        val maxG = if (maxIdx >= 0) c.getInt(maxIdx) else 20

                        val pct = if (maxG > 0) ((gr / maxG.toDouble()) * 100.0).roundToInt() else 0
                        val passed = pct >= 50

                        items.add(
                            StudentGradeReportItem(
                                studentId = sId,
                                studentName = sName,
                                groupName = gName,
                                examId = eId,
                                examTitle = eTitle,
                                subjectName = subName,
                                date = d,
                                score = gr,
                                maxScore = maxG,
                                percentage = pct,
                                isPassed = passed
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val maxS = if (items.isNotEmpty()) items.maxOf { it.score } else 0.0
            val minS = if (items.isNotEmpty()) items.minOf { it.score } else 0.0
            val avgS = if (items.isNotEmpty()) {
                (items.map { it.score }.average() * 10).roundToInt() / 10.0
            } else 0.0

            withContext(Dispatchers.Main) {
                reportResults = items
                totalExaminees = items.size
                highestScore = maxS
                lowestScore = minS
                averageScore = avgS
                isLoading = false
            }
        }
    }

    // Function to load all exams for a specific student
    fun loadStudentDetailExams(studentId: Long, studentName: String) {
        selectedStudentIdForDetails = studentId
        selectedStudentNameForDetails = studentName
        isLoadingStudentDetails = true

        coroutineScope.launch(Dispatchers.IO) {
            val list = mutableListOf<StudentDetailedExamRecord>()
            try {
                val cursor = database.openHelper.readableDatabase.query(
                    SimpleSQLiteQuery(
                        """
                        SELECT 
                            e.id AS examId,
                            e.title AS examTitle,
                            COALESCE(s.name, 'مادة عامة') AS subjectName,
                            e.date AS examDate,
                            eg.grade AS studentGrade,
                            e.maxGrade AS maxGrade
                        FROM exam_grades eg
                        INNER JOIN exams e ON eg.examId = e.id
                        LEFT JOIN subjects s ON e.subjectId = s.id
                        WHERE eg.studentId = ?
                        ORDER BY e.date DESC, eg.id DESC
                        """,
                        arrayOf(studentId)
                    )
                )
                cursor.use { c ->
                    val eIdIdx = c.getColumnIndex("examId")
                    val eTitleIdx = c.getColumnIndex("examTitle")
                    val sNameIdx = c.getColumnIndex("subjectName")
                    val dateIdx = c.getColumnIndex("examDate")
                    val gradeIdx = c.getColumnIndex("studentGrade")
                    val maxIdx = c.getColumnIndex("maxGrade")

                    while (c.moveToNext()) {
                        val eId = if (eIdIdx >= 0) c.getLong(eIdIdx) else 0L
                        val eTitle = if (eTitleIdx >= 0) c.getString(eTitleIdx) else ""
                        val subName = if (sNameIdx >= 0) c.getString(sNameIdx) else ""
                        val d = if (dateIdx >= 0) c.getString(dateIdx) else ""
                        val gr = if (gradeIdx >= 0) c.getDouble(gradeIdx) else 0.0
                        val maxG = if (maxIdx >= 0) c.getInt(maxIdx) else 20
                        val pct = if (maxG > 0) ((gr / maxG.toDouble()) * 100.0).roundToInt() else 0

                        list.add(
                            StudentDetailedExamRecord(
                                examId = eId,
                                examTitle = eTitle,
                                subjectName = subName,
                                date = d,
                                score = gr,
                                maxScore = maxG,
                                percentage = pct,
                                isPassed = pct >= 50
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            withContext(Dispatchers.Main) {
                studentDetailedExams = list
                isLoadingStudentDetails = false
            }
        }
    }

    // Initial load
    LaunchedEffect(Unit) {
        loadFilterOptions()
        executeReport(-1L, -1L)
    }

    // If student details view is open
    if (selectedStudentIdForDetails != null) {
        StudentGradesDetailSubView(
            studentName = selectedStudentNameForDetails,
            exams = studentDetailedExams,
            isLoading = isLoadingStudentDetails,
            primaryColor = primaryColor,
            passColor = passColor,
            failColor = failColor,
            onBack = { selectedStudentIdForDetails = null }
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("grades_report_screen")
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
                    modifier = Modifier.testTag("grades_report_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                Text(
                    text = "تقرير الدرجات",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )

                // زر "تصدير" في الأعلى (مؤقتًا: Toast "سيتم التصدير قريبًا")
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = primaryColor.copy(alpha = 0.15f),
                    modifier = Modifier.clickable {
                        Toast.makeText(context, "سيتم التصدير قريبًا", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "تصدير التقرير",
                            tint = primaryColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "تصدير",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Filters Section Card: (اختيار المادة + اختيار الاختبار + زر عرض التقرير)
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // اختيار المادة (Dropdown من المواد)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedSubject.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("المادة", fontFamily = CairoFontFamily) },
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
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .clickable { isSubjectDropdownExpanded = true }
                                .testTag("subject_filter_dropdown")
                        )

                        DropdownMenu(
                            expanded = isSubjectDropdownExpanded,
                            onDismissRequest = { isSubjectDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            subjectsList.forEach { subj ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = subj.name,
                                            fontFamily = CairoFontFamily,
                                            fontWeight = if (subj.id == selectedSubject.id) FontWeight.Bold else FontWeight.Normal,
                                            color = if (subj.id == selectedSubject.id) primaryColor else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        selectedSubject = subj
                                        isSubjectDropdownExpanded = false

                                        // Update exam options for this subject
                                        val exOptions = mutableListOf(ExamFilterOption(-1L, "كل الاختبارات", 0, -1L))
                                        if (subj.id > 0) {
                                            exOptions.addAll(allExamsList.filter { it.subjectId == subj.id })
                                        } else {
                                            exOptions.addAll(allExamsList)
                                        }
                                        filteredExamsList = exOptions
                                        selectedExam = ExamFilterOption(-1L, "كل الاختبارات", 0, -1L)
                                    }
                                )
                            }
                        }
                    }

                    // اختيار الاختبار (Dropdown من اختبارات المادة)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedExam.title,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("الاختبار", fontFamily = CairoFontFamily) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.WorkspacePremium,
                                    contentDescription = null,
                                    tint = primaryColor
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { isExamDropdownExpanded = !isExamDropdownExpanded }) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "عرض الاختبارات",
                                        tint = primaryColor
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .clickable { isExamDropdownExpanded = true }
                                .testTag("exam_filter_dropdown")
                        )

                        DropdownMenu(
                            expanded = isExamDropdownExpanded,
                            onDismissRequest = { isExamDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            filteredExamsList.forEach { ex ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = ex.title,
                                            fontFamily = CairoFontFamily,
                                            fontWeight = if (ex.id == selectedExam.id) FontWeight.Bold else FontWeight.Normal,
                                            color = if (ex.id == selectedExam.id) primaryColor else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        selectedExam = ex
                                        isExamDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // زر "عرض التقرير"
                    Button(
                        onClick = {
                            executeReport(selectedSubject.id, selectedExam.id)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryColor,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("apply_grades_report_filter")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "عرض التقرير",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = CairoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Check if there are no exams in system at all
            if (!hasExamsInSystem) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assignment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(54.dp)
                        )
                        Text(
                            text = "لا توجد اختبارات",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Text(
                            text = "يمكنك إضافة اختبار جديد من شاشة إدارة الاختبارات",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = CairoFontFamily,
                                color = primaryColor
                            )
                        )
                    }
                }
                return@Column
            }

            // Summary Banner بعد الفلترة: (عدد الطلاب اللي أدوا + أعلى درجة + أقل درجة + متوسط الدرجات)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = primaryColor.copy(alpha = 0.08f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, primaryColor.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    // عدد الطلاب اللي أدوا
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$totalExaminees",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor
                            )
                        )
                        Text(
                            text = "الطلاب الممتحنون",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = CairoFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    )

                    // أعلى درجة
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (totalExaminees > 0) highestScore.toString() else "0",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = passColor
                            )
                        )
                        Text(
                            text = "أعلى درجة",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = CairoFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    )

                    // أقل درجة
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (totalExaminees > 0) lowestScore.toString() else "0",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = failColor
                            )
                        )
                        Text(
                            text = "أقل درجة",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = CairoFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    )

                    // متوسط الدرجات
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (totalExaminees > 0) averageScore.toString() else "0",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF59E0B)
                            )
                        )
                        Text(
                            text = "متوسط الدرجات",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = CairoFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Results List
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = primaryColor)
                }
            } else if (reportResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assignment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(54.dp)
                        )
                        Text(
                            text = "لا توجد درجات مسجلة لهذا الاختبار",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Text(
                            text = "جرّب اختيار مادة أو اختبار آخر من الفلتر بالأعلى",
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
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(reportResults, key = { "${it.studentId}_${it.examId}" }) { item ->
                        StudentGradeCard(
                            item = item,
                            passColor = passColor,
                            failColor = failColor,
                            primaryColor = primaryColor,
                            onClick = {
                                loadStudentDetailExams(item.studentId, item.studentName)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentGradeCard(
    item: StudentGradeReportItem,
    passColor: Color,
    failColor: Color,
    primaryColor: Color,
    onClick: () -> Unit
) {
    val gradeColor = if (item.isPassed) passColor else failColor

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.2.dp,
            gradeColor.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("report_grade_card_${item.studentId}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Student Name + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = primaryColor.copy(alpha = 0.12f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = item.studentName.take(1),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = CairoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryColor
                                )
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = item.studentName,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.groupName.ifBlank { item.subjectName },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = CairoFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Status chip (ناجح / راسب)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = gradeColor.copy(alpha = 0.15f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = if (item.isPassed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = null,
                            tint = gradeColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (item.isPassed) "ناجح" else "راسب",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = gradeColor
                            )
                        )
                    }
                }
            }

            // Exam Info + Score & Percentage
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = gradeColor.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(1.dp, gradeColor.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = item.examTitle,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = CairoFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.scoreFormatted,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = gradeColor,
                                fontSize = 17.sp
                            )
                        )
                    }

                    // Percentage Badge
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = gradeColor
                    ) {
                        Text(
                            text = "${item.percentage}%",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

// 2. شاشة فرعية لعرض تفاصيل درجات الطالب في كل الاختبارات
@Composable
private fun StudentGradesDetailSubView(
    studentName: String,
    exams: List<StudentDetailedExamRecord>,
    isLoading: Boolean,
    primaryColor: Color,
    passColor: Color,
    failColor: Color,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("student_grades_detail_subview")
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                Text(
                    text = "سجل درجات: $studentName",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 17.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = primaryColor)
                }
            } else if (exams.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا توجد درجات مرصودة لهذا الطالب",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = CairoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            } else {
                val avgPct = exams.map { it.percentage }.average().roundToInt()
                val passedCount = exams.count { it.isPassed }

                // Summary banner for student
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = primaryColor.copy(alpha = 0.08f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, primaryColor.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${exams.size}",
                                style = MaterialTheme.typography.titleMedium.copy(
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

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$passedCount",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = CairoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = passColor
                                )
                            )
                            Text(
                                text = "ناجح",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = CairoFontFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$avgPct%",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = CairoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = if (avgPct >= 50) passColor else failColor
                                )
                            )
                            Text(
                                text = "المتوسط العام",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = CairoFontFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(exams, key = { it.examId }) { examRec ->
                        val itemColor = if (examRec.isPassed) passColor else failColor

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, itemColor.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = examRec.examTitle,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = CairoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = examRec.subjectName,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = CairoFontFamily,
                                                color = primaryColor
                                            )
                                        )
                                        Text(
                                            text = "•",
                                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                                        )
                                        Text(
                                            text = examRec.date,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = CairoFontFamily,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = examRec.scoreFormatted,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = CairoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = itemColor
                                        )
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = itemColor.copy(alpha = 0.15f),
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        Text(
                                            text = "${examRec.percentage}%",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = CairoFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                color = itemColor
                                            ),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
