package com.example.ui.screens.home

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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AppHeader
import com.example.ui.components.formatCenterTitle
import com.example.ui.components.rememberAppSettings
import com.example.ui.components.resolveThemeColor
import com.example.ui.screens.attendance.ChildAttendanceScreen
import com.example.ui.screens.content.ChildContentScreen
import com.example.ui.screens.grades.ChildGradesScreen
import com.example.ui.screens.payments.ChildPaymentsScreen
import com.example.ui.theme.CairoFontFamily

data class ParentHomeItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun ParentHomeScreen(
    parentName: String,
    studentName: String,
    onLogout: () -> Unit,
    onOpenContent: () -> Unit,
    onSelectItem: (title: String, subtitle: String, color: Color, icon: ImageVector) -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showChildGrades by remember { mutableStateOf(false) }
    var showChildAttendance by remember { mutableStateOf(false) }
    var showChildContent by remember { mutableStateOf(false) }
    var showChildPayments by remember { mutableStateOf(false) }

    if (showChildGrades) {
        ChildGradesScreen(
            onBack = { showChildGrades = false },
            studentName = studentName,
            parentName = parentName,
            modifier = modifier
        )
    } else if (showChildAttendance) {
        ChildAttendanceScreen(
            onBack = { showChildAttendance = false },
            studentName = studentName,
            parentName = parentName,
            modifier = modifier
        )
    } else if (showChildContent) {
        ChildContentScreen(
            onBack = { showChildContent = false },
            studentName = studentName,
            parentName = parentName,
            modifier = modifier
        )
    } else if (showChildPayments) {
        ChildPaymentsScreen(
            onBack = { showChildPayments = false },
            studentName = studentName,
            parentName = parentName,
            modifier = modifier
        )
    } else {
        val items = listOf(
        ParentHomeItem(
            id = "attendance",
            title = "حضور ابني",
            subtitle = "سجل الحضور والغياب اليومي",
            icon = Icons.Default.FactCheck,
            color = Color(0xFF10B981) // أخضر
        ),
        ParentHomeItem(
            id = "grades",
            title = "درجات ابني",
            subtitle = "النتائج والاختبارات الدورية",
            icon = Icons.Default.Grade,
            color = Color(0xFF8B5CF6) // بنفسجي
        ),
        ParentHomeItem(
            id = "subscriptions",
            title = "الاشتراكات والمدفوعات",
            subtitle = "الرسوم الشهرية والمصروفات",
            icon = Icons.Default.AttachMoney,
            color = Color(0xFFF59E0B) // عنبري
        ),
        ParentHomeItem(
            id = "content",
            title = "المحتوى التعليمي",
            subtitle = "متابعة الفيديوهات والواجبات",
            icon = Icons.Default.MenuBook,
            color = Color(0xFF3B82F6) // أزرق
        )
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background)
            .testTag("parent_home_screen")
    ) {
        val settings = rememberAppSettings()
        val displayCenterName = formatCenterTitle(settings.centerName)
        val primaryColor = resolveThemeColor(settings.themeColor)

        // Top Header using AppHeader: الشريط العلوي: "سنتر [centerName]" و الترحيب: "أهلاً [اسم ولي الأمر]"
        AppHeader(
            title = displayCenterName,
            subtitle = "أهلاً $parentName 👋",
            badgeText = "بوابة ولي الأمر",
            actions = { color ->
                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .size(38.dp)
                ) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "تبديل المظهر",
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onLogout,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                        .size(38.dp)
                        .testTag("parent_logout_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "تسجيل الخروج",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        )

        // Student Info Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF0D9488).copy(alpha = 0.12f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Color(0xFF0D9488).copy(alpha = 0.35f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0D9488).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color(0xFF0D9488),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "متابعة الطالب: $studentName",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    Text(
                        text = "متابعة فورية للحضور والدرجات مع المعلم أ. وليد الجارحي",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = CairoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Grid of parent options
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("parent_home_grid")
        ) {
            items(items, key = { it.id }) { item ->
                ParentGridCard(
                    item = item,
                    onClick = {
                        if (item.id == "content") {
                            showChildContent = true
                        } else if (item.id == "grades") {
                            showChildGrades = true
                        } else if (item.id == "attendance") {
                            showChildAttendance = true
                        } else if (item.id == "subscriptions") {
                            showChildPayments = true
                        } else {
                            onSelectItem(item.title, item.subtitle, item.color, item.icon)
                        }
                    }
                )
            }
        }
    }
    }
}

@Composable
private fun ParentGridCard(
    item: ParentHomeItem,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, item.color.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clickable(onClick = onClick)
            .testTag("parent_card_${item.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(item.color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = item.color,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = CairoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}
