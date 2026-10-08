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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.data.database.AppDatabase
import com.example.data.model.Attendance
import com.example.ui.components.rememberAppSettings
import com.example.ui.components.resolveThemeColor
import com.example.ui.theme.CairoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StudentAttendanceReportItem(
    val studentId: Long,
    val studentName: String,
    val groupName: String,
    val presentCount: Int,
    val absentCount: Int,
    val attendanceRate: Int,
    val records: List<StudentSessionRecord>
)

data class StudentSessionRecord(
    val date: String,
    val time: String,
    val status: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceReportScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val database = remember { AppDatabase.getDatabase(context) }

    val settings = rememberAppSettings()
    val primaryColor = resolveThemeColor(settings.themeColor)

    // State for Filter
    var groupsList by remember { mutableStateOf<List<String>>(listOf("كل المجموعات")) }
    var selectedGroup by remember { mutableStateOf("كل المجموعات") }
    var isGroupDropdownExpanded by remember { mutableStateOf(false) }

    var fromDate by remember { mutableStateOf("") }
    var toDate by remember { mutableStateOf("") }

    var isSelectingFromDate by remember { mutableStateOf(false) }
    var isSelectingToDate by remember { mutableStateOf(false) }

    val fromDatePickerState = rememberDatePickerState()
    val toDatePickerState = rememberDatePickerState()

    // Results & Loading
    var isLoading by remember { mutableStateOf(true) }
    var reportItems by remember { mutableStateOf<List<StudentAttendanceReportItem>>(emptyList()) }
    var selectedStudentForDetail by remember { mutableStateOf<StudentAttendanceReportItem?>(null) }

    // Query function
    fun loadReport(groupFilter: String, from: String, to: String) {
        coroutineScope.launch(Dispatchers.IO) {
            isLoading = true

            // 1. Ensure initial sample attendance data exists if table is empty
            try {
                val countCursor = database.openHelper.readableDatabase.query(
                    SimpleSQLiteQuery("SELECT COUNT(*) FROM attendances")
                )
                var attCount = 0
                countCursor.use { if (it.moveToNext()) attCount = it.getInt(0) }
                if (attCount == 0) {
                    val sampleDates = listOf("2026-10-01", "2026-10-03", "2026-10-05", "2026-10-08", "2026-10-10")
                    val sampleTimes = listOf("04:05 م", "04:10 م", "04:12 م", "04:15 م", "04:02 م")
                    val sampleStudents = listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L)

                    val toInsert = mutableListOf<Attendance>()
                    sampleDates.forEachIndexed { dateIdx, d ->
                        sampleStudents.forEachIndexed { stIdx, sId ->
                            // Simulate high attendance, few absences
                            val isPresent = (dateIdx + stIdx) % 4 != 0
                            val isLate = (dateIdx + stIdx) % 5 == 0
                            if (isPresent) {
                                toInsert.add(
                                    Attendance(
                                        studentId = sId,
                                        date = d,
                                        time = sampleTimes[dateIdx % sampleTimes.size],
                                        status = if (isLate) "متأخر" else "حاضر"
                                    )
                                )
                            }
                        }
                    }
                    toInsert.forEach { database.attendanceDao().insertAttendance(it) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Load group names from DB
            try {
                val groupNames = mutableListOf("كل المجموعات")
                val gCursor = database.openHelper.readableDatabase.query(
                    SimpleSQLiteQuery("SELECT DISTINCT (grade || ' - ' || center) FROM `groups` UNION SELECT DISTINCT groupName FROM students WHERE groupName != ''")
                )
                gCursor.use {
                    while (it.moveToNext()) {
                        val gName = it.getString(0)
                        if (!gName.isNullOrBlank() && !groupNames.contains(gName)) {
                            groupNames.add(gName)
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    groupsList = groupNames
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 3. Query distinct dates within range
            val distinctDates = mutableListOf<String>()
            val dateQueryArgs = mutableListOf<Any>()
            val dateQuerySql = StringBuilder("SELECT DISTINCT date FROM attendances WHERE 1=1")
            if (from.isNotBlank()) {
                dateQuerySql.append(" AND date >= ?")
                dateQueryArgs.add(from)
            }
            if (to.isNotBlank()) {
                dateQuerySql.append(" AND date <= ?")
                dateQueryArgs.add(to)
            }
            dateQuerySql.append(" ORDER BY date ASC")

            try {
                val dCursor = database.openHelper.readableDatabase.query(
                    SimpleSQLiteQuery(dateQuerySql.toString(), dateQueryArgs.toTypedArray())
                )
                dCursor.use {
                    while (it.moveToNext()) {
                        distinctDates.add(it.getString(0))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 4. Query Students matching group filter
            val studentList = mutableListOf<Triple<Long, String, String>>()
            val stArgs = mutableListOf<Any>()
            val stSql = StringBuilder("SELECT id, name, groupName FROM students WHERE 1=1")
            if (groupFilter.isNotBlank() && groupFilter != "كل المجموعات") {
                stSql.append(" AND (groupName = ? OR groupName LIKE ?)")
                stArgs.add(groupFilter)
                stArgs.add("%$groupFilter%")
            }
            stSql.append(" ORDER BY name ASC")

            try {
                val sCursor = database.openHelper.readableDatabase.query(
                    SimpleSQLiteQuery(stSql.toString(), stArgs.toTypedArray())
                )
                sCursor.use {
                    while (it.moveToNext()) {
                        studentList.add(Triple(it.getLong(0), it.getString(1), it.getString(2) ?: ""))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 5. Query student attendance records
            val computedItems = mutableListOf<StudentAttendanceReportItem>()
            val totalSessionsCount = distinctDates.size

            studentList.forEach { (stId, stName, stGroup) ->
                val attMap = mutableMapOf<String, Pair<String, String>>() // date -> (status, time)
                val aSql = StringBuilder("SELECT date, time, status FROM attendances WHERE studentId = ?")
                val aArgs = mutableListOf<Any>(stId)
                if (from.isNotBlank()) {
                    aSql.append(" AND date >= ?")
                    aArgs.add(from)
                }
                if (to.isNotBlank()) {
                    aSql.append(" AND date <= ?")
                    aArgs.add(to)
                }

                try {
                    val aCursor = database.openHelper.readableDatabase.query(
                        SimpleSQLiteQuery(aSql.toString(), aArgs.toTypedArray())
                    )
                    aCursor.use {
                        while (it.moveToNext()) {
                            val d = it.getString(0)
                            val t = it.getString(1) ?: "--"
                            val s = it.getString(2) ?: "حاضر"
                            attMap[d] = Pair(s, t)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val sessionRecords = mutableListOf<StudentSessionRecord>()
                distinctDates.forEach { d ->
                    val record = attMap[d]
                    if (record != null) {
                        sessionRecords.add(StudentSessionRecord(date = d, time = record.second, status = record.first))
                    } else {
                        sessionRecords.add(StudentSessionRecord(date = d, time = "--", status = "غائب"))
                    }
                }

                val presentCount = sessionRecords.count { it.status == "حاضر" || it.status == "متأخر" }
                val absentCount = sessionRecords.count { it.status == "غائب" }
                val rate = if (totalSessionsCount > 0) {
                    ((presentCount.toDouble() / totalSessionsCount.toDouble()) * 100).toInt()
                } else {
                    100
                }

                computedItems.add(
                    StudentAttendanceReportItem(
                        studentId = stId,
                        studentName = stName,
                        groupName = stGroup,
                        presentCount = presentCount,
                        absentCount = absentCount,
                        attendanceRate = rate,
                        records = sessionRecords
                    )
                )
            }

            withContext(Dispatchers.Main) {
                reportItems = computedItems
                isLoading = false
            }
        }
    }

    // Initial load
    LaunchedEffect(Unit) {
        loadReport(selectedGroup, fromDate, toDate)
    }

    // Date Picker Dialog: From Date
    if (isSelectingFromDate) {
        DatePickerDialog(
            onDismissRequest = { isSelectingFromDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        fromDatePickerState.selectedDateMillis?.let { millis ->
                            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            fromDate = fmt.format(Date(millis))
                        }
                        isSelectingFromDate = false
                    }
                ) {
                    Text("اختيار", fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { isSelectingFromDate = false }) {
                    Text("إلغاء", fontFamily = CairoFontFamily)
                }
            }
        ) {
            DatePicker(state = fromDatePickerState)
        }
    }

    // Date Picker Dialog: To Date
    if (isSelectingToDate) {
        DatePickerDialog(
            onDismissRequest = { isSelectingToDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        toDatePickerState.selectedDateMillis?.let { millis ->
                            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            toDate = fmt.format(Date(millis))
                        }
                        isSelectingToDate = false
                    }
                ) {
                    Text("اختيار", fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { isSelectingToDate = false }) {
                    Text("إلغاء", fontFamily = CairoFontFamily)
                }
            }
        ) {
            DatePicker(state = toDatePickerState)
        }
    }

    // Sub-Screen: تفاصيل حضور الطالب
    if (selectedStudentForDetail != null) {
        StudentAttendanceDetailView(
            student = selectedStudentForDetail!!,
            primaryColor = primaryColor,
            onBack = { selectedStudentForDetail = null }
        )
        return
    }

    // Main Report View
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("attendance_report_screen")
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Top App Bar مع زر الرجوع وزر التصدير
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("attendance_report_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                Text(
                    text = "تقرير الحضور",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )

                // زر "تصدير" في الأعلى (مؤقتًا Toast "سيتم التصدير قريبًا")
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

            // Filters Section Card (اختيار المجموعة + من تاريخ / إلى تاريخ + زر عرض التقرير)
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
                    // اختيار المجموعة (Dropdown)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedGroup,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("المجموعة", fontFamily = CairoFontFamily) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Groups,
                                    contentDescription = null,
                                    tint = primaryColor
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { isGroupDropdownExpanded = !isGroupDropdownExpanded }) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "عرض المجموعات",
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
                                .clickable { isGroupDropdownExpanded = true }
                                .testTag("group_filter_dropdown")
                        )

                        DropdownMenu(
                            expanded = isGroupDropdownExpanded,
                            onDismissRequest = { isGroupDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            groupsList.forEach { g ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = g,
                                            fontFamily = CairoFontFamily,
                                            fontWeight = if (g == selectedGroup) FontWeight.Bold else FontWeight.Normal,
                                            color = if (g == selectedGroup) primaryColor else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        selectedGroup = g
                                        isGroupDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // اختيار التاريخ: من تاريخ / إلى تاريخ
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // من تاريخ
                        OutlinedTextField(
                            value = fromDate,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("من تاريخ", fontFamily = CairoFontFamily, fontSize = 12.sp) },
                            placeholder = { Text("YYYY-MM-DD", fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            trailingIcon = {
                                if (fromDate.isNotBlank()) {
                                    IconButton(onClick = { fromDate = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "مسح", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clickable { isSelectingFromDate = true }
                                .testTag("from_date_picker")
                        )

                        // إلى تاريخ
                        OutlinedTextField(
                            value = toDate,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("إلى تاريخ", fontFamily = CairoFontFamily, fontSize = 12.sp) },
                            placeholder = { Text("YYYY-MM-DD", fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            trailingIcon = {
                                if (toDate.isNotBlank()) {
                                    IconButton(onClick = { toDate = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "مسح", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clickable { isSelectingToDate = true }
                                .testTag("to_date_picker")
                        )
                    }

                    // زر "عرض التقرير"
                    Button(
                        onClick = {
                            loadReport(selectedGroup, fromDate, toDate)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryColor,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("apply_report_filter_button")
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

            // Summary Section (عدد الحضور + عدد الغياب + نسبة الحضور)
            val totalPresent = reportItems.sumOf { it.presentCount }
            val totalAbsent = reportItems.sumOf { it.absentCount }
            val totalSessions = totalPresent + totalAbsent
            val overallAttendanceRate = if (totalSessions > 0) {
                ((totalPresent.toDouble() / totalSessions.toDouble()) * 100).toInt()
            } else 100

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
                    // عدد الحضور
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$totalPresent",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        )
                        Text(
                            text = "إجمالي الحضور",
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

                    // عدد الغياب
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$totalAbsent",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444)
                            )
                        )
                        Text(
                            text = "إجمالي الغياب",
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

                    // نسبة الحضور
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$overallAttendanceRate%",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = if (overallAttendanceRate >= 75) Color(0xFF10B981) else Color(0xFFF59E0B)
                            )
                        )
                        Text(
                            text = "نسبة الحضور",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = CairoFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Results Table / List
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = primaryColor)
                }
            } else if (reportItems.isEmpty()) {
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
                            imageVector = Icons.Default.EventBusy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(54.dp)
                        )
                        Text(
                            text = "لا توجد سجلات حضور مطابقة لهذا الفلتر",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Text(
                            text = "جرّب تغيير المجموعة أو الفترة الزمنية",
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
                    items(reportItems, key = { it.studentId }) { studentItem ->
                        StudentReportCard(
                            studentItem = studentItem,
                            primaryColor = primaryColor,
                            onClick = { selectedStudentForDetail = studentItem }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentReportCard(
    studentItem: StudentAttendanceReportItem,
    primaryColor: Color,
    onClick: () -> Unit
) {
    val rateColor = when {
        studentItem.attendanceRate >= 75 -> Color(0xFF10B981)
        studentItem.attendanceRate >= 50 -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            rateColor.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("student_attendance_item_${studentItem.studentId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Student Info
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = primaryColor.copy(alpha = 0.12f),
                    modifier = Modifier.size(42.dp)
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
                    Text(
                        text = studentItem.groupName.ifBlank { "مجموعة عامة" },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = CairoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Statistics (حضور / غياب / النسبة)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "حضور: ${studentItem.presentCount}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        )
                        Text(
                            text = "|",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                        )
                        Text(
                            text = "غياب: ${studentItem.absentCount}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444)
                            )
                        )
                    }
                }

                // Percentage Badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = rateColor,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text(
                        text = "${studentItem.attendanceRate}%",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

// 2. شاشة تفاصيل حضور الطالب عند الضغط عليه
@Composable
private fun StudentAttendanceDetailView(
    student: StudentAttendanceReportItem,
    primaryColor: Color,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val rateColor = when {
        student.attendanceRate >= 75 -> Color(0xFF10B981)
        student.attendanceRate >= 50 -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("student_attendance_detail_view")
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
                    text = "سجل حضور: ${student.studentName}",
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

            // Student Summary Card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = primaryColor.copy(alpha = 0.08f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, primaryColor.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = student.groupName.ifBlank { "مجموعة عامة" },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = CairoFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = rateColor
                        ) {
                            Text(
                                text = "نسبة الالتزام: ${student.attendanceRate}%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = CairoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Text(
                            text = "أيام الحضور: ${student.presentCount}",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        )
                        Text(
                            text = "أيام الغياب: ${student.absentCount}",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "تفاصيل الحصص والجلسات",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // List of Sessions (التاريخ + الوقت + الحالة)
            if (student.records.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا توجد جلسات مسجلة",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = CairoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(student.records) { record ->
                        val isPresent = record.status == "حاضر" || record.status == "متأخر"
                        val statusColor = when (record.status) {
                            "حاضر" -> Color(0xFF10B981)
                            "متأخر" -> Color(0xFFF59E0B)
                            else -> Color(0xFFEF4444)
                        }

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                statusColor.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Date & Time
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = record.date,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = CairoFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                    Text(
                                        text = "(${record.time})",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = CairoFontFamily,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }

                                // Status Badge
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = statusColor.copy(alpha = 0.15f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isPresent) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                            contentDescription = null,
                                            tint = statusColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = record.status,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = CairoFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                color = statusColor
                                            )
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
