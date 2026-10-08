package com.example.ui.screens

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.ui.screens.exams.ManageExamsScreen
import com.example.ui.screens.reports.ReportsScreen
import com.example.ui.screens.settings.SettingsScreen
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AppHeader
import com.example.ui.components.AppLogoBadge
import com.example.ui.components.formatCenterTitle
import com.example.ui.components.formatTeacherTitle
import com.example.ui.components.rememberAppSettings
import com.example.ui.components.resolveThemeColor
import com.example.ui.theme.AttendanceBgDark
import com.example.ui.theme.AttendanceBgLight
import com.example.ui.theme.AttendanceBlueDark
import com.example.ui.theme.AttendanceBlueLight
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.GradesBgDark
import com.example.ui.theme.GradesBgLight
import com.example.ui.theme.GradesPurpleDark
import com.example.ui.theme.GradesPurpleLight
import com.example.ui.theme.GroupsBgDark
import com.example.ui.theme.GroupsBgLight
import com.example.ui.theme.GroupsOrangeDark
import com.example.ui.theme.GroupsOrangeLight
import com.example.ui.theme.ReportsBgDark
import com.example.ui.theme.ReportsBgLight
import com.example.ui.theme.ReportsRedDark
import com.example.ui.theme.ReportsRedLight
import com.example.ui.theme.SettingsBgDark
import com.example.ui.theme.SettingsBgLight
import com.example.ui.theme.SettingsGrayDark
import com.example.ui.theme.SettingsGrayLight
import com.example.ui.theme.StudentsBgDark
import com.example.ui.theme.StudentsBgLight
import com.example.ui.theme.StudentsGreenDark
import com.example.ui.theme.StudentsGreenLight

data class DashboardItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColorLight: Color,
    val accentColorDark: Color,
    val bgColorLight: Color,
    val bgColorDark: Color,
    val testTag: String
)

@Composable
fun TeacherDashboardScreen(
    onLogout: () -> Unit,
    onOpenAttendanceScanner: () -> Unit,
    onOpenStudents: () -> Unit,
    onOpenSubjects: () -> Unit,
    onOpenGroups: () -> Unit,
    onOpenContent: () -> Unit,
    onSelectItem: (title: String, subtitle: String, color: Color, icon: ImageVector) -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showManageExamsScreen by remember { mutableStateOf(false) }
    var showReportsScreen by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }

    if (showReportsScreen) {
        ReportsScreen(
            onBack = { showReportsScreen = false }
        )
    } else if (showManageExamsScreen) {
        ManageExamsScreen(
            onBack = { showManageExamsScreen = false }
        )
    } else if (showSettingsScreen) {
        SettingsScreen(
            onBack = { showSettingsScreen = false },
            onLogout = onLogout
        )
    } else {
        // Dashboard Items:
    // 1. تسجيل الحضور (أزرق)
    // 2. الطلاب (أخضر)
    // 3. المجموعات (برتقالي)
    // 4. المواد (سماوي)
    // 5. المحتوى (ذهبي)
    // 6. الدرجات (بنفسجي)
    // 7. التقارير (أحمر)
    // 8. الإعدادات (رمادي)
    val dashboardItems = listOf(
        DashboardItem(
            id = "attendance",
            title = "تسجيل الحضور",
            subtitle = "تحضير وغياب الطلاب",
            icon = Icons.Default.FactCheck,
            accentColorLight = AttendanceBlueLight,
            accentColorDark = AttendanceBlueDark,
            bgColorLight = AttendanceBgLight,
            bgColorDark = AttendanceBgDark,
            testTag = "card_attendance"
        ),
        DashboardItem(
            id = "students",
            title = "الطلاب",
            subtitle = "قوائم وبيانات الطلاب",
            icon = Icons.Default.Groups,
            accentColorLight = StudentsGreenLight,
            accentColorDark = StudentsGreenDark,
            bgColorLight = StudentsBgLight,
            bgColorDark = StudentsBgDark,
            testTag = "card_students"
        ),
        DashboardItem(
            id = "groups",
            title = "المجموعات",
            subtitle = "مواعيد وفصول الحصص",
            icon = Icons.Default.Class,
            accentColorLight = GroupsOrangeLight,
            accentColorDark = GroupsOrangeDark,
            bgColorLight = GroupsBgLight,
            bgColorDark = GroupsBgDark,
            testTag = "card_groups"
        ),
        DashboardItem(
            id = "subjects",
            title = "المواد",
            subtitle = "المقررات والمناهج",
            icon = Icons.Default.MenuBook,
            accentColorLight = Color(0xFF06B6D4), // سماوي
            accentColorDark = Color(0xFF22D3EE),
            bgColorLight = Color(0xFFECFEFF),
            bgColorDark = Color(0xFF083344),
            testTag = "card_subjects"
        ),
        DashboardItem(
            id = "content",
            title = "المحتوى",
            subtitle = "فيديوهات واختبارات وأكواد",
            icon = Icons.Default.OndemandVideo,
            accentColorLight = Color(0xFFEAB308), // ذهبي
            accentColorDark = Color(0xFFFACC15),
            bgColorLight = Color(0xFFFEFCE8),
            bgColorDark = Color(0xFF422006),
            testTag = "card_content"
        ),
        DashboardItem(
            id = "grades",
            title = "الدرجات",
            subtitle = "رصد الامتحانات والتقييمات",
            icon = Icons.Default.Grade,
            accentColorLight = GradesPurpleLight,
            accentColorDark = GradesPurpleDark,
            bgColorLight = GradesBgLight,
            bgColorDark = GradesBgDark,
            testTag = "card_grades"
        ),
        DashboardItem(
            id = "reports",
            title = "التقارير",
            subtitle = "إحصائيات الأداء والمتابعة",
            icon = Icons.Default.Assessment,
            accentColorLight = ReportsRedLight,
            accentColorDark = ReportsRedDark,
            bgColorLight = ReportsBgLight,
            bgColorDark = ReportsBgDark,
            testTag = "card_reports"
        ),
        DashboardItem(
            id = "settings",
            title = "الإعدادات",
            subtitle = "بيانات السنتر والحساب",
            icon = Icons.Default.Settings,
            accentColorLight = SettingsGrayLight,
            accentColorDark = SettingsGrayDark,
            bgColorLight = SettingsBgLight,
            bgColorDark = SettingsBgDark,
            testTag = "card_settings"
        )
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("teacher_dashboard_screen")
    ) {
        val settings = rememberAppSettings()
        val displayCenterName = formatCenterTitle(settings.centerName)
        val displayTeacherName = formatTeacherTitle(settings.teacherName)
        val primaryColor = resolveThemeColor(settings.themeColor)

        // Top Header Bar: Using unified AppHeader component
        AppHeader(
            title = displayCenterName,
            subtitle = "لوحة تحكم المعلم والسنتر",
            actions = { color ->
                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .size(38.dp)
                        .testTag("dashboard_theme_toggle")
                ) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "تبديل المظهر",
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Surface(
                    onClick = onLogout,
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                    modifier = Modifier.testTag("logout_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "خروج",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "خروج",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        )
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Welcome Card (Teacher Profile Greeting): الترحيب: "أهلاً بك أ. [teacherName]"
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = primaryColor.copy(alpha = 0.12f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                primaryColor.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "أهلاً بك $displayTeacherName 👋",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    Text(
                        text = "نتمنى لك يوماً دراسياً موفقاً مع طلابك",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = CairoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = primaryColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "إدارة السنتر",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryColor
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Section Title
        Text(
            text = "الخدمات والعمليات السريعة",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = CairoFontFamily,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            ),
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 2x3 Grid of 6 Colored Cards
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .testTag("dashboard_grid"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(dashboardItems, key = { it.id }) { item ->
                val accentColor = if (isDarkTheme) item.accentColorDark else item.accentColorLight
                val bgColor = if (isDarkTheme) item.bgColorDark else item.bgColorLight

                DashboardGridCard(
                    item = item,
                    accentColor = accentColor,
                    bgColor = bgColor,
                    onClick = {
                        when (item.id) {
                            "attendance" -> onOpenAttendanceScanner()
                            "students" -> onOpenStudents()
                            "subjects" -> onOpenSubjects()
                            "groups" -> onOpenGroups()
                            "content" -> onOpenContent()
                            "grades" -> showManageExamsScreen = true
                            "reports" -> showReportsScreen = true
                            "settings" -> showSettingsScreen = true
                            else -> onSelectItem(item.title, item.subtitle, accentColor, item.icon)
                        }
                    }
                )
            }
        }
    }
}
}

@Composable
private fun DashboardGridCard(
    item: DashboardItem,
    accentColor: Color,
    bgColor: Color,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag(item.testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            // Icon in colored container
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accentColor.copy(alpha = 0.18f))
                        .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = accentColor,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Dot indicator
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.7f))
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )

                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = CairoFontFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 1
                )
            }
        }
    }
}
