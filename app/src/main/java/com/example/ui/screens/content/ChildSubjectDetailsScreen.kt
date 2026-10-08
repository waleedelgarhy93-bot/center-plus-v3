package com.example.ui.screens.content

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

data class VideoItemUi(
    val id: Long,
    val title: String,
    val description: String,
    val url: String,
    val orderIndex: Int,
    val isPaid: Boolean
)

data class ExamItemUi(
    val id: Long,
    val title: String,
    val date: String,
    val maxGrade: Int,
    val studentScore: Double?, // null if not taken
    val isPassed: Boolean,
    val isQuiz: Boolean = false,
    val description: String = ""
) {
    val scoreFormatted: String
        get() = if (studentScore != null) {
            val scoreStr = if (studentScore % 1.0 == 0.0) studentScore.toInt().toString() else studentScore.toString()
            "$scoreStr / $maxGrade"
        } else {
            "لم يؤدَّ الاختبار"
        }

    val percentageFormatted: String
        get() = if (studentScore != null && maxGrade > 0) {
            val pct = ((studentScore / maxGrade) * 100).toInt()
            "$pct%"
        } else {
            ""
        }
}

@Composable
fun ChildSubjectDetailsScreen(
    subject: ChildSubjectItemUi,
    studentId: Long,
    studentName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val coroutineScope = rememberCoroutineScope()
    val settings = rememberAppSettings()
    val primaryColor = resolveThemeColor(settings.themeColor)

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var videos by remember { mutableStateOf<List<VideoItemUi>>(emptyList()) }
    var exams by remember { mutableStateOf<List<ExamItemUi>>(emptyList()) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    fun loadContent() {
        coroutineScope.launch(Dispatchers.IO) {
            isLoading = true
            try {
                // 1. Load Videos for this subject
                val loadedVideos = mutableListOf<VideoItemUi>()
                val vCur = database.openHelper.readableDatabase.query(
                    SimpleSQLiteQuery(
                        "SELECT id, title, description, url, orderIndex, isPaid FROM videos WHERE subjectId = ? ORDER BY orderIndex ASC, id ASC",
                        arrayOf(subject.id)
                    )
                )
                vCur.use {
                    while (it.moveToNext()) {
                        val id = it.getLong(0)
                        val title = it.getString(1) ?: ""
                        val desc = it.getString(2) ?: ""
                        val url = it.getString(3) ?: ""
                        val orderIndex = it.getInt(4)
                        val isPaid = it.getInt(5) == 1
                        loadedVideos.add(
                            VideoItemUi(
                                id = id,
                                title = title,
                                description = desc,
                                url = url,
                                orderIndex = orderIndex,
                                isPaid = isPaid
                            )
                        )
                    }
                }

                // 2. Load Exams for this subject
                val loadedExams = mutableListOf<ExamItemUi>()
                val eCur = database.openHelper.readableDatabase.query(
                    SimpleSQLiteQuery(
                        "SELECT id, title, maxGrade, date, notes FROM exams WHERE subjectId = ? ORDER BY date DESC, id DESC",
                        arrayOf(subject.id)
                    )
                )
                eCur.use {
                    while (it.moveToNext()) {
                        val id = it.getLong(0)
                        val title = it.getString(1) ?: ""
                        val maxGrade = it.getInt(2).coerceAtLeast(1)
                        val date = it.getString(3) ?: ""
                        val notes = it.getString(4) ?: ""

                        // Check student grade for this exam
                        var studentGrade: Double? = null
                        if (studentId > 0) {
                            val gCur = database.openHelper.readableDatabase.query(
                                SimpleSQLiteQuery(
                                    "SELECT grade FROM exam_grades WHERE examId = ? AND studentId = ? LIMIT 1",
                                    arrayOf(id, studentId)
                                )
                            )
                            gCur.use { c ->
                                if (c.moveToNext()) {
                                    studentGrade = c.getDouble(0)
                                }
                            }
                        }

                        val isPassed = studentGrade != null && (studentGrade!! >= (maxGrade / 2.0))

                        loadedExams.add(
                            ExamItemUi(
                                id = id,
                                title = title,
                                date = date,
                                maxGrade = maxGrade,
                                studentScore = studentGrade,
                                isPassed = isPassed,
                                isQuiz = false,
                                description = notes
                            )
                        )
                    }
                }

                // 3. Load Quizzes for this subject
                val qCur = database.openHelper.readableDatabase.query(
                    SimpleSQLiteQuery(
                        "SELECT id, title, date, description FROM quizzes WHERE subjectId = ? ORDER BY id ASC",
                        arrayOf(subject.id)
                    )
                )
                qCur.use {
                    while (it.moveToNext()) {
                        val id = it.getLong(0)
                        val title = it.getString(1) ?: ""
                        val date = it.getString(2) ?: ""
                        val desc = it.getString(3) ?: ""

                        loadedExams.add(
                            ExamItemUi(
                                id = id + 10000L, // offset id to keep keys unique
                                title = title,
                                date = date,
                                maxGrade = 10,
                                studentScore = null, // quizzes in current schema are Google Forms / links
                                isPassed = false,
                                isQuiz = true,
                                description = desc
                            )
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    videos = loadedVideos
                    exams = loadedExams
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

    LaunchedEffect(subject.id, studentId, refreshTrigger) {
        loadContent()
    }

    val subjectAccentColor = try {
        Color(android.graphics.Color.parseColor(subject.colorHex))
    } catch (_: Exception) {
        primaryColor
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("child_subject_details_screen")
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
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .size(42.dp)
                        .testTag("child_subject_details_back_button")
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
                        text = subject.name,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = "متابعة الطالب: $studentName",
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
                        .testTag("child_subject_details_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "تحديث",
                        tint = primaryColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Subject Info Banner Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = subjectAccentColor.copy(alpha = 0.1f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, subjectAccentColor.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(subjectAccentColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = subjectAccentColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = subject.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                        Text(
                            text = "${videos.size} فيديوهات متاحة • ${exams.size} اختبارات",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = CairoFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Tab Row: "الفيديوهات" و "الاختبارات"
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = primaryColor,
                        height = 3.dp
                    )
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .testTag("child_subject_tabs")
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    modifier = Modifier.testTag("tab_videos"),
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = null,
                                tint = if (selectedTabIndex == 0) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "الفيديوهات (${videos.size})",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = CairoFontFamily,
                                    fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTabIndex == 0) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                )

                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    modifier = Modifier.testTag("tab_exams"),
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assignment,
                                contentDescription = null,
                                tint = if (selectedTabIndex == 1) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "الاختبارات (${exams.size})",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = CairoFontFamily,
                                    fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTabIndex == 1) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (isLoading) {
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
                        CircularProgressIndicator(
                            color = primaryColor,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "جاري تحميل المحتوى...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = CairoFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            } else {
                when (selectedTabIndex) {
                    0 -> VideosListContent(videos = videos, primaryColor = primaryColor)
                    1 -> ExamsListContent(exams = exams, primaryColor = primaryColor)
                }
            }
        }
    }
}

@Composable
private fun VideosListContent(
    videos: List<VideoItemUi>,
    primaryColor: Color
) {
    if (videos.isEmpty()) {
        EmptyContentCard(
            title = "لا توجد فيديوهات متاحة",
            subtitle = "لم يقم المعلم برفع أي فيديوهات لهذه المادة بعد"
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("child_videos_list"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(videos, key = { it.id }) { video ->
                VideoCardItem(video = video, primaryColor = primaryColor)
            }
        }
    }
}

@Composable
private fun VideoCardItem(
    video: VideoItemUi,
    primaryColor: Color
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("video_item_${video.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF3B82F6).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.OndemandVideo,
                    contentDescription = null,
                    tint = Color(0xFF3B82F6),
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 2
                )

                if (video.description.isNotBlank()) {
                    Text(
                        text = video.description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = CairoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 2
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (video.isPaid) Color(0xFF8B5CF6).copy(alpha = 0.12f) else Color(0xFF10B981).copy(alpha = 0.12f)
            ) {
                Text(
                    text = if (video.isPaid) "مشترك" else "مجاني",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = if (video.isPaid) Color(0xFF8B5CF6) else Color(0xFF10B981)
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ExamsListContent(
    exams: List<ExamItemUi>,
    primaryColor: Color
) {
    if (exams.isEmpty()) {
        EmptyContentCard(
            title = "لا توجد اختبارات متاحة",
            subtitle = "لم يتم إضافة أي اختبارات أو امتحانات لهذه المادة بعد"
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("child_exams_list"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(exams, key = { it.id }) { exam ->
                ExamCardItem(exam = exam)
            }
        }
    }
}

@Composable
private fun ExamCardItem(
    exam: ExamItemUi
) {
    val hasScore = exam.studentScore != null
    val scoreBadgeColor = when {
        !hasScore -> Color(0xFF6B7280) // رمادي
        exam.isPassed -> Color(0xFF10B981) // أخضر
        else -> Color(0xFFEF4444) // أحمر
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            scoreBadgeColor.copy(alpha = 0.25f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("exam_item_${exam.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(scoreBadgeColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (hasScore) Icons.Default.Grade else Icons.Default.Assignment,
                        contentDescription = null,
                        tint = scoreBadgeColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = exam.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 2
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (exam.date.isNotBlank()) {
                            Text(
                                text = "التاريخ: ${exam.date}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = CairoFontFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                        if (exam.isQuiz) {
                            Text(
                                text = "• اختبار إلكتروني",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = CairoFontFamily,
                                    color = Color(0xFF8B5CF6)
                                )
                            )
                        }
                    }
                }
            }

            // Grade Display
            if (hasScore) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = scoreBadgeColor.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, scoreBadgeColor.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = exam.scoreFormatted,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                color = scoreBadgeColor
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    if (exam.percentageFormatted.isNotBlank()) {
                        Text(
                            text = "${exam.percentageFormatted} ${if (exam.isPassed) "✅ ناجح" else "⚠️ راسب"}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = scoreBadgeColor
                            )
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.HourglassEmpty,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "لم يؤدَّ بعد",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = CairoFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyContentCard(
    title: String,
    subtitle: String
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 36.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.EventBusy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                textAlign = TextAlign.Center
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = CairoFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}
