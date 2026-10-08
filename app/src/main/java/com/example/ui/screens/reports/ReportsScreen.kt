package com.example.ui.screens.reports

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.rememberAppSettings
import com.example.ui.components.resolveThemeColor
import com.example.ui.theme.CairoFontFamily

private sealed interface ReportsSubScreen {
    data object Attendance : ReportsSubScreen
    data object Grades : ReportsSubScreen
    data object Statistics : ReportsSubScreen
}

data class RecentAttendanceItem(
    val studentName: String,
    val groupName: String,
    val date: String,
    val time: String,
    val status: String = "حاضر"
)

data class RecentGradeItem(
    val studentName: String,
    val examTitle: String,
    val score: Double,
    val maxScore: Int,
    val date: String
)

@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var currentSubScreen by remember { mutableStateOf<ReportsSubScreen?>(null) }

    when (currentSubScreen) {
        ReportsSubScreen.Attendance -> {
            AttendanceReportScreen(onBack = { currentSubScreen = null })
        }
        ReportsSubScreen.Grades -> {
            GradesReportScreen(onBack = { currentSubScreen = null })
        }
        ReportsSubScreen.Statistics -> {
            StatisticsScreen(onBack = { currentSubScreen = null })
        }
        null -> {
            MainReportsScreen(
                onBack = onBack,
                onOpenAttendanceReport = { currentSubScreen = ReportsSubScreen.Attendance },
                onOpenGradesReport = { currentSubScreen = ReportsSubScreen.Grades },
                onOpenStatistics = { currentSubScreen = ReportsSubScreen.Statistics },
                modifier = modifier
            )
        }
    }
}

@Composable
private fun MainReportsScreen(
    onBack: () -> Unit,
    onOpenAttendanceReport: () -> Unit,
    onOpenGradesReport: () -> Unit,
    onOpenStatistics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings = rememberAppSettings()
    val primaryColor = resolveThemeColor(settings.themeColor)

    // Sample Recent Activities
    val recentAttendance = remember {
        listOf(
            RecentAttendanceItem("أحمد محمود سالم", "الثالث الثانوي - سنتر الروبي", "2026-10-05", "04:15 م"),
            RecentAttendanceItem("محمد علي أحمد", "الثالث الثانوي - سنتر الروبي", "2026-10-05", "04:12 م"),
            RecentAttendanceItem("سارة خالد مصطفى", "الثالث الثانوي - سنتر الروبي", "2026-10-05", "04:10 م"),
            RecentAttendanceItem("عمر حسام الدين", "الثالث الثانوي - سنتر الروبي", "2026-10-05", "04:08 م"),
            RecentAttendanceItem("يوسف كريم عبد الله", "الثالث الثانوي - سنتر الروبي", "2026-10-05", "04:05 م")
        )
    }

    val recentGrades = remember {
        listOf(
            RecentGradeItem("أحمد محمود سالم", "اختبار الفصل الأول - الجبر", 18.5, 20, "2026-10-10"),
            RecentGradeItem("محمد علي أحمد", "اختبار الفصل الأول - الجبر", 19.0, 20, "2026-10-10"),
            RecentGradeItem("سارة خالد مصطفى", "اختبار الفصل الأول - الجبر", 16.0, 20, "2026-10-10"),
            RecentGradeItem("عمر حسام الدين", "امتحان شهري - نيوتن", 28.0, 30, "2026-10-04"),
            RecentGradeItem("يوسف كريم عبد الله", "امتحان شهري - نيوتن", 27.5, 30, "2026-10-04")
        )
    }

    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("reports_screen")
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
                    modifier = Modifier.testTag("reports_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                Text(
                    text = "التقارير",
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
                            imageVector = Icons.Default.Assessment,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3 Large Cards
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Card 1: تقرير الحضور (لون أزرق)
                ReportCategoryCard(
                    title = "تقرير الحضور",
                    subtitle = "سجل تفصيلي لنسب حضور وغياب المجموعات والطلاب",
                    icon = Icons.Default.CalendarToday,
                    accentColor = Color(0xFF2563EB),
                    onClick = onOpenAttendanceReport,
                    testTag = "card_attendance_report"
                )

                // Card 2: تقرير الدرجات (لون بنفسجي)
                ReportCategoryCard(
                    title = "تقرير الدرجات",
                    subtitle = "تحليلات الامتحانات الشهرية ونتائج وتقييمات الطلاب",
                    icon = Icons.Default.WorkspacePremium,
                    accentColor = Color(0xFF8B5CF6),
                    onClick = onOpenGradesReport,
                    testTag = "card_grades_report"
                )

                // Card 3: الإحصائيات العامة (لون أخضر)
                ReportCategoryCard(
                    title = "الإحصائيات العامة",
                    subtitle = "مؤشرات الأداء الشاملة ومعدلات التفاعل والاشتراكات",
                    icon = Icons.Default.Insights,
                    accentColor = Color(0xFF10B981),
                    onClick = onOpenStatistics,
                    testTag = "card_statistics_report"
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: "آخر النشاطات"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "آخر النشاطات",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Activity Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selectedTab == 0) primaryColor else Color.Transparent)
                        .clickable { selectedTab = 0 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "آخر 5 عمليات حضور",
                        fontFamily = CairoFontFamily,
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedTab == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selectedTab == 1) primaryColor else Color.Transparent)
                        .clickable { selectedTab = 1 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "آخر 5 درجات مدخلة",
                        fontFamily = CairoFontFamily,
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedTab == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Activities List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                if (selectedTab == 0) {
                    items(recentAttendance) { item ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                Color(0xFF2563EB).copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
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
                                        color = Color(0xFF2563EB).copy(alpha = 0.15f),
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                tint = Color(0xFF2563EB),
                                                modifier = Modifier.size(20.dp)
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
                                            text = item.groupName,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = CairoFontFamily,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF10B981).copy(alpha = 0.15f)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF10B981),
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = item.status,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontFamily = CairoFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF10B981)
                                                )
                                            )
                                        }
                                    }

                                    Text(
                                        text = "${item.date} - ${item.time}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = CairoFontFamily,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(recentGrades) { item ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                Color(0xFF8B5CF6).copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
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
                                        color = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Grade,
                                                contentDescription = null,
                                                tint = Color(0xFF8B5CF6),
                                                modifier = Modifier.size(20.dp)
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
                                            text = item.examTitle,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = CairoFontFamily,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${item.score} / ${item.maxScore}",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = CairoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF10B981)
                                        )
                                    )
                                    Text(
                                        text = item.date,
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
        }
    }
}

@Composable
private fun ReportCategoryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = accentColor.copy(alpha = 0.12f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.2.dp,
            accentColor.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = accentColor.copy(alpha = 0.2f),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = CairoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}
