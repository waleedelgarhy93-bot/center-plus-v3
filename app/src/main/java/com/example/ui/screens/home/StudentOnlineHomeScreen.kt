package com.example.ui.screens.home

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.AppDatabase
import com.example.data.model.Quiz
import com.example.data.model.Video
import com.example.data.session.SessionManager
import com.example.ui.components.AppHeader
import com.example.ui.components.formatCenterTitle
import com.example.ui.components.rememberAppSettings
import com.example.ui.components.resolveThemeColor
import com.example.ui.screens.grades.MyGradesScreen
import com.example.ui.screens.studentview.EnterCodeScreen
import com.example.ui.theme.CairoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface OnlineSubScreen {
    data object MySubjects : OnlineSubScreen
    data object MyGrades : OnlineSubScreen
    data class EnterCode(val subjectId: Long, val subjectName: String) : OnlineSubScreen
    data class SubjectDetail(val subjectId: Long, val subjectName: String) : OnlineSubScreen
    data class ComingSoon(val title: String) : OnlineSubScreen
}

private data class OnlineHomeCard(
    val id: String,
    val title: String,
    val color: Color,
    val icon: ImageVector
)

private data class SubjectInfo(
    val id: Long,
    val name: String,
    val description: String
)

@Composable
fun StudentOnlineHomeScreen(
    studentName: String,
    onLogout: () -> Unit,
    onOpenSubjects: () -> Unit = {},
    onEnterSubscriptionCode: () -> Unit = {},
    onSelectItem: ((title: String, subtitle: String, color: Color, icon: ImageVector) -> Unit)? = null,
    isDarkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var currentSubScreen by remember { mutableStateOf<OnlineSubScreen?>(null) }
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val sessionManager = remember { SessionManager(context) }

    val currentStudentId = remember {
        if (sessionManager.studentId > 0) sessionManager.studentId else 8L
    }

    when (val screen = currentSubScreen) {
        null -> {
            MainOnlineHomeScreen(
                studentName = studentName,
                onLogout = onLogout,
                onOpenMySubjects = { currentSubScreen = OnlineSubScreen.MySubjects },
                onOpenMyGrades = { currentSubScreen = OnlineSubScreen.MyGrades },
                onOpenComingSoon = { title -> currentSubScreen = OnlineSubScreen.ComingSoon(title) },
                modifier = modifier
            )
        }
        is OnlineSubScreen.MyGrades -> {
            BackHandler { currentSubScreen = null }
            MyGradesScreen(
                onBack = { currentSubScreen = null },
                studentId = currentStudentId,
                studentName = studentName,
                modifier = modifier
            )
        }
        is OnlineSubScreen.MySubjects -> {
            BackHandler { currentSubScreen = null }
            MySubjectsScreen(
                studentId = currentStudentId,
                database = database,
                onBack = { currentSubScreen = null },
                onSelectLockedSubject = { sId, sName ->
                    currentSubScreen = OnlineSubScreen.EnterCode(sId, sName)
                },
                onSelectUnlockedSubject = { sId, sName ->
                    currentSubScreen = OnlineSubScreen.SubjectDetail(sId, sName)
                },
                modifier = modifier
            )
        }
        is OnlineSubScreen.EnterCode -> {
            BackHandler { currentSubScreen = OnlineSubScreen.MySubjects }
            EnterCodeScreen(
                subjectName = screen.subjectName,
                subjectId = screen.subjectId,
                onBack = { currentSubScreen = OnlineSubScreen.MySubjects },
                onCodeActivated = {
                    currentSubScreen = OnlineSubScreen.MySubjects
                },
                modifier = modifier
            )
        }
        is OnlineSubScreen.SubjectDetail -> {
            BackHandler { currentSubScreen = OnlineSubScreen.MySubjects }
            OnlineSubjectDetailScreen(
                subjectId = screen.subjectId,
                subjectName = screen.subjectName,
                database = database,
                onBack = { currentSubScreen = OnlineSubScreen.MySubjects },
                modifier = modifier
            )
        }
        is OnlineSubScreen.ComingSoon -> {
            BackHandler { currentSubScreen = null }
            ComingSoonScreen(
                title = screen.title,
                onBack = { currentSubScreen = null },
                modifier = modifier
            )
        }
    }
}

@Composable
private fun MainOnlineHomeScreen(
    studentName: String,
    onLogout: () -> Unit,
    onOpenMySubjects: () -> Unit,
    onOpenMyGrades: () -> Unit,
    onOpenComingSoon: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val cards = remember {
        listOf(
            OnlineHomeCard(
                id = "subjects",
                title = "المواد",
                color = Color(0xFF2563EB), // أزرق
                icon = Icons.Default.Book
            ),
            OnlineHomeCard(
                id = "grades",
                title = "درجاتي",
                color = Color(0xFF8B5CF6), // بنفسجي
                icon = Icons.Default.WorkspacePremium
            ),
            OnlineHomeCard(
                id = "attendance",
                title = "حضوري",
                color = Color(0xFF10B981), // أخضر
                icon = Icons.Default.CalendarMonth
            ),
            OnlineHomeCard(
                id = "settings",
                title = "إعداداتي",
                color = Color(0xFF6B7280), // رمادي
                icon = Icons.Default.Settings
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background)
            .testTag("student_online_home_screen")
    ) {
        val settings = rememberAppSettings()
        val displayCenterName = formatCenterTitle(settings.centerName)
        val primaryColor = resolveThemeColor(settings.themeColor)

        // 1. الشريط العلوي: "سنتر [centerName]" + الترحيب: "أهلاً [اسم الطالب]"
        AppHeader(
            title = displayCenterName,
            subtitle = "أهلاً $studentName 👋",
            badgeText = "طالب أونلاين",
            actions = { color ->
                IconButton(
                    onClick = onLogout,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                        .size(38.dp)
                        .testTag("online_logout_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "تسجيل الخروج",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // 2. بانر ترحيبي: "مرحباً بك في سنترك، ابدأ رحلتك التعليمية"
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = primaryColor.copy(alpha = 0.12f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                primaryColor.copy(alpha = 0.35f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2563EB).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "مرحباً بك في سنترك، ابدأ رحلتك التعليمية",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. شبكة من 4 كروت (Grid 2x2):
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("online_home_grid")
        ) {
            items(cards, key = { it.id }) { cardItem ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = cardItem.color.copy(alpha = 0.12f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        cardItem.color.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(145.dp)
                        .clickable {
                            if (cardItem.id == "subjects") {
                                onOpenMySubjects()
                            } else if (cardItem.id == "grades") {
                                onOpenMyGrades()
                            } else {
                                onOpenComingSoon(cardItem.title)
                            }
                        }
                        .testTag("online_card_${cardItem.id}")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(cardItem.color.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = cardItem.icon,
                                contentDescription = cardItem.title,
                                tint = cardItem.color,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Text(
                            text = cardItem.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                    }
                }
            }
        }
    }
}

// 3. شاشة "موادي": تعرض المواد مع حالة القفل وإمكانية الضغط للتفعيل أو فتح المحتوى
@Composable
private fun MySubjectsScreen(
    studentId: Long,
    database: AppDatabase,
    onBack: () -> Unit,
    onSelectLockedSubject: (Long, String) -> Unit,
    onSelectUnlockedSubject: (Long, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val unlockedSubjectIds by database.studentSubjectAccessDao()
        .getUnlockedSubjectIdsForStudent(studentId)
        .collectAsState(initial = emptyList())

    val subjects = remember {
        listOf(
            SubjectInfo(1L, "رياضة", "جبر، تفاضل وتكامل، استاتيكا وديناميكا"),
            SubjectInfo(2L, "فيزياء", "كهربية، مغناطيسية، موجات وفيزياء حديثة"),
            SubjectInfo(3L, "كيمياء", "كيمياء عضوية وغير عضوية والاتزان الكيميائي")
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("my_subjects_screen")
    ) {
        // Top Bar: "موادي" + زر رجوع
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .size(42.dp)
                    .testTag("my_subjects_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "رجوع",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "موادي",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(subjects, key = { it.id }) { subject ->
                val isUnlocked = unlockedSubjectIds.contains(subject.id)

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isUnlocked) {
                            Color(0xFF10B981).copy(alpha = 0.08f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        }
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.2.dp,
                        if (isUnlocked) Color(0xFF10B981).copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isUnlocked) {
                                onSelectUnlockedSubject(subject.id, subject.name)
                            } else {
                                onSelectLockedSubject(subject.id, subject.name)
                            }
                        }
                        .testTag("subject_card_${subject.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isUnlocked) Color(0xFF10B981).copy(alpha = 0.18f) else Color(0xFF2563EB).copy(alpha = 0.15f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Book,
                                    contentDescription = null,
                                    tint = if (isUnlocked) Color(0xFF10B981) else Color(0xFF2563EB),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = subject.name,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = CairoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Text(
                                    text = if (isUnlocked) "المحتوى متاح ومفتوح • اضغط للمشاهدة" else "اشترك لفتح المحتوى",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = CairoFontFamily,
                                        color = if (isUnlocked) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (isUnlocked) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                )
                            }
                        }

                        // حالة القفل: 🔒 مقفل أو 🔓 مفتوح
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isUnlocked) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isUnlocked) Color(0xFF10B981).copy(alpha = 0.4f) else MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                    contentDescription = if (isUnlocked) "مفتوح" else "مقفل",
                                    tint = if (isUnlocked) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isUnlocked) "مفتوح 🔓" else "مقفل 🔒",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = CairoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isUnlocked) Color(0xFF10B981) else MaterialTheme.colorScheme.error
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

// تفاصيل المادة المفتوحة: الفيديوهات والاختبارات (زي طالب السنتر)
@Composable
private fun OnlineSubjectDetailScreen(
    subjectId: Long,
    subjectName: String,
    database: AppDatabase,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val videos by database.videoDao().getVideosBySubject(subjectId).collectAsState(initial = emptyList())
    val quizzes by database.quizDao().getQuizzesBySubject(subjectId).collectAsState(initial = emptyList())

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("online_subject_detail_screen")
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .size(42.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "رجوع",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "مادة $subjectName",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
                Text(
                    text = "المحتوى التعليمي المفعل 🔓",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = CairoFontFamily,
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Videos Section
            item {
                Text(
                    text = "المحاضرات والفيديوهات (${videos.size}):",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
            }

            if (videos.isEmpty()) {
                item {
                    Text(
                        text = "لا توجد فيديوهات مضافة بعد",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = CairoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(videos) { video ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2563EB).copy(alpha = 0.3f)),
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
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF2563EB).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color(0xFF2563EB)
                                    )
                                }
                                Column {
                                    Text(
                                        text = video.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = CairoFontFamily,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    if (video.description.isNotBlank()) {
                                        Text(
                                            text = video.description,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = CairoFontFamily,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(video.url))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "الرابط غير متاح", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                            ) {
                                Text("مشاهدة", fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Quizzes Section
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "الاختبارات الإلكترونية (${quizzes.size}):",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
            }

            if (quizzes.isEmpty()) {
                item {
                    Text(
                        text = "لا توجد اختبارات مضافة بعد",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = CairoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(quizzes) { quiz ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f)),
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
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Assignment,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981)
                                    )
                                }
                                Column {
                                    Text(
                                        text = quiz.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = CairoFontFamily,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    if (quiz.description.isNotBlank()) {
                                        Text(
                                            text = quiz.description,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = CairoFontFamily,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(quiz.url))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "الرابط غير متاح", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                            ) {
                                Text("بدء الاختبار", fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// 4. شاشة "قريبًا..." لباقي الكروت (درجاتي، حضوري، إعداداتي)
@Composable
private fun ComingSoonScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("coming_soon_screen")
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .size(42.dp)
                    .testTag("coming_soon_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "رجوع",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
        }

        // Center "قريبًا..."
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(
                    text = "قريبًا...",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB)
                    )
                )

                Button(
                    onClick = onBack,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2563EB),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "رجوع",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}
