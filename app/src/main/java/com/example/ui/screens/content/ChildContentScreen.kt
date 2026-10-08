package com.example.ui.screens.content

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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.data.database.AppDatabase
import com.example.data.session.SessionManager
import com.example.ui.components.rememberAppSettings
import com.example.ui.components.resolveThemeColor
import com.example.ui.theme.CairoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChildSubjectItemUi(
    val id: Long,
    val name: String,
    val description: String,
    val icon: String,
    val colorHex: String,
    val videosCount: Int,
    val examsCount: Int
)

@Composable
fun ChildContentScreen(
    onBack: () -> Unit,
    studentName: String = "",
    parentName: String = "",
    studentIdOverride: Long? = null,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val sessionManager = remember { SessionManager(context) }
    val coroutineScope = rememberCoroutineScope()
    val settings = rememberAppSettings()
    val primaryColor = resolveThemeColor(settings.themeColor)

    var isLoading by remember { mutableStateOf(true) }
    var resolvedStudentName by remember { mutableStateOf(studentName) }
    var resolvedStudentId by remember { mutableLongStateOf(studentIdOverride ?: -1L) }
    var subjects by remember { mutableStateOf<List<ChildSubjectItemUi>>(emptyList()) }
    var selectedSubjectForDetails by remember { mutableStateOf<ChildSubjectItemUi?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // If viewing details of a subject
    if (selectedSubjectForDetails != null) {
        ChildSubjectDetailsScreen(
            subject = selectedSubjectForDetails!!,
            studentId = resolvedStudentId,
            studentName = if (resolvedStudentName.isNotBlank() && resolvedStudentName != "الطالب المسجل") resolvedStudentName else studentName,
            onBack = { selectedSubjectForDetails = null },
            modifier = modifier
        )
        return
    }

    fun loadStudentSubjects() {
        coroutineScope.launch(Dispatchers.IO) {
            isLoading = true
            try {
                // 1. Resolve target studentId
                var targetStudentId = studentIdOverride ?: sessionManager.studentId

                if (targetStudentId <= 0) {
                    val userId = sessionManager.userId
                    if (userId > 0) {
                        val userCur = database.openHelper.readableDatabase.query(
                            SimpleSQLiteQuery("SELECT studentId FROM users WHERE id = ? LIMIT 1", arrayOf(userId))
                        )
                        userCur.use {
                            if (it.moveToNext()) {
                                val sIdIdx = it.getColumnIndex("studentId")
                                if (sIdIdx >= 0 && !it.isNull(sIdIdx)) {
                                    targetStudentId = it.getLong(sIdIdx)
                                }
                            }
                        }
                    }
                }

                if (targetStudentId <= 0) {
                    val pPhone = sessionManager.phone
                    if (pPhone.isNotBlank()) {
                        val pCur = database.openHelper.readableDatabase.query(
                            SimpleSQLiteQuery("SELECT id, name FROM students WHERE parentPhone = ? OR phone = ? LIMIT 1", arrayOf(pPhone, pPhone))
                        )
                        pCur.use {
                            if (it.moveToNext()) {
                                targetStudentId = it.getLong(0)
                                val name = it.getString(1)
                                if (!name.isNullOrBlank()) resolvedStudentName = name
                            }
                        }
                    }
                }

                if (targetStudentId <= 0 && studentName.isNotBlank() && studentName != "الطالب المسجل") {
                    val sCur = database.openHelper.readableDatabase.query(
                        SimpleSQLiteQuery("SELECT id, name FROM students WHERE name LIKE ? LIMIT 1", arrayOf("%$studentName%"))
                    )
                    sCur.use {
                        if (it.moveToNext()) {
                            targetStudentId = it.getLong(0)
                            val name = it.getString(1)
                            if (!name.isNullOrBlank()) resolvedStudentName = name
                        }
                    }
                }

                if (targetStudentId <= 0) {
                    val fallbackCur = database.openHelper.readableDatabase.query(
                        SimpleSQLiteQuery("SELECT id, name FROM students LIMIT 1")
                    )
                    fallbackCur.use {
                        if (it.moveToNext()) {
                            targetStudentId = it.getLong(0)
                            val name = it.getString(1)
                            if (!name.isNullOrBlank()) resolvedStudentName = name
                        }
                    }
                }

                // 2. Fetch student name and group
                var studentGroupName = ""
                if (targetStudentId > 0) {
                    val stCur = database.openHelper.readableDatabase.query(
                        SimpleSQLiteQuery("SELECT name, groupName FROM students WHERE id = ? LIMIT 1", arrayOf(targetStudentId))
                    )
                    stCur.use {
                        if (it.moveToNext()) {
                            val name = it.getString(0)
                            studentGroupName = it.getString(1) ?: ""
                            if (!name.isNullOrBlank()) resolvedStudentName = name
                        }
                    }
                }

                // 3. Find enrolled subjects for student:
                // A) Through their group:
                val subjectMap = mutableMapOf<Long, Triple<String, String, Pair<String, String>>>() // id -> (name, desc, (icon, color))

                if (studentGroupName.isNotBlank()) {
                    val gCur = database.openHelper.readableDatabase.query(
                        SimpleSQLiteQuery(
                            """
                            SELECT DISTINCT s.id, s.name, s.description, s.icon, s.color 
                            FROM subjects s 
                            INNER JOIN `groups` g ON s.id = g.subjectId 
                            WHERE (g.grade || ' - ' || g.center) = ? OR g.grade = ? OR g.center = ?
                            """.trimIndent(),
                            arrayOf(studentGroupName, studentGroupName, studentGroupName)
                        )
                    )
                    gCur.use {
                        while (it.moveToNext()) {
                            val id = it.getLong(0)
                            val name = it.getString(1) ?: ""
                            val desc = it.getString(2) ?: ""
                            val icon = it.getString(3) ?: "calculate"
                            val color = it.getString(4) ?: "#3B82F6"
                            subjectMap[id] = Triple(name, desc, Pair(icon, color))
                        }
                    }
                }

                // B) Through student_subject_access (online or code subscriptions):
                if (targetStudentId > 0) {
                    val ssaCur = database.openHelper.readableDatabase.query(
                        SimpleSQLiteQuery(
                            """
                            SELECT DISTINCT s.id, s.name, s.description, s.icon, s.color 
                            FROM subjects s 
                            INNER JOIN student_subject_access ssa ON s.id = ssa.subjectId 
                            WHERE ssa.studentId = ?
                            """.trimIndent(),
                            arrayOf(targetStudentId)
                        )
                    )
                    ssaCur.use {
                        while (it.moveToNext()) {
                            val id = it.getLong(0)
                            val name = it.getString(1) ?: ""
                            val desc = it.getString(2) ?: ""
                            val icon = it.getString(3) ?: "calculate"
                            val color = it.getString(4) ?: "#3B82F6"
                            subjectMap[id] = Triple(name, desc, Pair(icon, color))
                        }
                    }
                }

                // C) Fallback if student is linked to group but group has no specific subjectId, or first subject
                if (subjectMap.isEmpty() && targetStudentId > 0) {
                    val allSubjectsCur = database.openHelper.readableDatabase.query(
                        SimpleSQLiteQuery("SELECT id, name, description, icon, color FROM subjects LIMIT 1")
                    )
                    allSubjectsCur.use {
                        if (it.moveToNext()) {
                            val id = it.getLong(0)
                            val name = it.getString(1) ?: ""
                            val desc = it.getString(2) ?: ""
                            val icon = it.getString(3) ?: "calculate"
                            val color = it.getString(4) ?: "#3B82F6"
                            // If student belongs to a group with center name, link this subject
                            if (studentGroupName.isNotBlank()) {
                                subjectMap[id] = Triple(name, desc, Pair(icon, color))
                            }
                        }
                    }
                }

                // 4. For each subject, query count of videos and exams
                val resultList = mutableListOf<ChildSubjectItemUi>()
                for ((id, info) in subjectMap) {
                    val name = info.first
                    val desc = info.second
                    val icon = info.third.first
                    val color = info.third.second

                    // Count videos
                    var vCount = 0
                    val vCur = database.openHelper.readableDatabase.query(
                        SimpleSQLiteQuery("SELECT COUNT(*) FROM videos WHERE subjectId = ?", arrayOf(id))
                    )
                    vCur.use { if (it.moveToNext()) vCount = it.getInt(0) }

                    // Count exams + quizzes
                    var eCount = 0
                    val eCur = database.openHelper.readableDatabase.query(
                        SimpleSQLiteQuery("SELECT COUNT(*) FROM exams WHERE subjectId = ?", arrayOf(id))
                    )
                    eCur.use { if (it.moveToNext()) eCount = it.getInt(0) }

                    var qCount = 0
                    val qCur = database.openHelper.readableDatabase.query(
                        SimpleSQLiteQuery("SELECT COUNT(*) FROM quizzes WHERE subjectId = ?", arrayOf(id))
                    )
                    qCur.use { if (it.moveToNext()) qCount = it.getInt(0) }

                    resultList.add(
                        ChildSubjectItemUi(
                            id = id,
                            name = name,
                            description = desc,
                            icon = icon,
                            colorHex = color,
                            videosCount = vCount,
                            examsCount = eCount + qCount
                        )
                    )
                }

                withContext(Dispatchers.Main) {
                    resolvedStudentId = targetStudentId
                    subjects = resultList
                    isLoading = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(studentIdOverride, studentName, refreshTrigger) {
        loadStudentSubjects()
    }

    val displayStudentName = if (resolvedStudentName.isNotBlank() && resolvedStudentName != "الطالب المسجل") {
        resolvedStudentName
    } else if (studentName.isNotBlank() && studentName != "الطالب المسجل") {
        studentName
    } else {
        "الطالب"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("child_content_screen")
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Top App Bar: "المحتوى التعليمي - [اسم الطالب]" + زر رجوع
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .size(42.dp)
                        .testTag("child_content_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = "المحتوى التعليمي - $displayStudentName",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = "المواد المشترك بها والفيديوهات والاختبارات",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = CairoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                IconButton(
                    onClick = { refreshTrigger++ },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .size(42.dp)
                        .testTag("child_content_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "تحديث",
                        tint = primaryColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = primaryColor,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "جاري تحميل المواد التعليمية...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = CairoFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            } else if (subjects.isEmpty()) {
                // Empty State: "الطالب غير مشترك في أي مادة"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("child_content_empty_state"),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp, horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF59E0B).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Text(
                                text = "الطالب غير مشترك في أي مادة",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = CairoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "لم يتم تفعيل أي مواد أو مجموعات دراسية لحساب الطالب حتى الآن. يمكنك التواصل مع المعلم لتفعيل الاشتراك.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = CairoFontFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                // List of enrolled subjects
                Text(
                    text = "المواد المسجلة (${subjects.size})",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("child_content_list"),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(subjects, key = { it.id }) { subjectItem ->
                        ChildSubjectCard(
                            subject = subjectItem,
                            primaryColor = primaryColor,
                            onShowDetails = { selectedSubjectForDetails = subjectItem },
                            modifier = Modifier.testTag("subject_card_${subjectItem.id}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChildSubjectCard(
    subject: ChildSubjectItemUi,
    primaryColor: Color,
    onShowDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardColor = try {
        Color(android.graphics.Color.parseColor(subject.colorHex))
    } catch (_: Exception) {
        primaryColor
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            cardColor.copy(alpha = 0.25f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Subject Icon & Name
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getSubjectIcon(subject.icon),
                        contentDescription = null,
                        tint = cardColor,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = subject.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    if (subject.description.isNotBlank()) {
                        Text(
                            text = subject.description,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = CairoFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1
                        )
                    }
                }
            }

            // Stats Row: عدد الفيديوهات المتاحة • عدد الاختبارات
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF3B82F6).copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.2f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OndemandVideo,
                            contentDescription = null,
                            tint = Color(0xFF3B82F6),
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "${subject.videosCount}",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = CairoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF3B82F6)
                                )
                            )
                            Text(
                                text = "فيديوهات متاحة",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = CairoFontFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF8B5CF6).copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.2f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assignment,
                            contentDescription = null,
                            tint = Color(0xFF8B5CF6),
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "${subject.examsCount}",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = CairoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF8B5CF6)
                                )
                            )
                            Text(
                                text = "اختبارات وامتحانات",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = CairoFontFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }

            // Button: "عرض التفاصيل"
            Button(
                onClick = onShowDetails,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = cardColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("subject_details_button_${subject.id}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "عرض التفاصيل",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun getSubjectIcon(iconKey: String): ImageVector {
    return when (iconKey.lowercase()) {
        "science" -> Icons.Default.Science
        "biotech" -> Icons.Default.Science
        "menu_book", "book" -> Icons.Default.MenuBook
        "auto_stories" -> Icons.Default.AutoStories
        else -> Icons.Default.Calculate
    }
}
