package com.example.ui.screens.content

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.QuizWithVideoTitle
import com.example.data.model.SubjectWithGroupCount
import com.example.data.model.VideoWithQuizCount
import com.example.ui.screens.subjects.SubjectVisuals
import com.example.ui.theme.CairoFontFamily

@Composable
fun SubjectContentScreen(
    subject: SubjectWithGroupCount,
    onBack: () -> Unit,
    onAddNewVideo: () -> Unit,
    onEditVideo: (VideoWithQuizCount) -> Unit,
    onAddNewQuiz: () -> Unit,
    onEditQuiz: (QuizWithVideoTitle) -> Unit,
    onGenerateCode: () -> Unit,
    viewModel: ContentViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    LaunchedEffect(subject.id) {
        viewModel.selectSubject(subject.id)
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("الفيديوهات", "الاختبارات", "الأكواد")
    val tabIcons = listOf(Icons.Default.OndemandVideo, Icons.Default.Quiz, Icons.Default.VpnKey)

    val subjectColor = SubjectVisuals.parseColor(subject.color)
    val subjectIcon = SubjectVisuals.getIconByKey(subject.icon)

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background)
            .testTag("subject_content_screen")
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .size(42.dp)
                    .testTag("subject_content_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "رجوع",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(subjectColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = subjectIcon,
                    contentDescription = null,
                    tint = subjectColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column {
                Text(
                    text = "مادة ${subject.name}",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
                Text(
                    text = "إدارة المحتوى التعليمي وأكواد الوصول",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = CairoFontFamily,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        // Tab Row
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = subjectColor,
                    height = 3.dp
                )
            }
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTabIndex == index) subjectColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = tabIcons[index],
                            contentDescription = title,
                            tint = if (selectedTabIndex == index) subjectColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }

        // Tab Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (selectedTabIndex) {
                0 -> VideosTab(
                    onAddNewVideo = onAddNewVideo,
                    onEditVideo = onEditVideo,
                    viewModel = viewModel
                )
                1 -> QuizzesTab(
                    onAddNewQuiz = onAddNewQuiz,
                    onEditQuiz = onEditQuiz,
                    viewModel = viewModel
                )
                2 -> CodesTab(
                    subjectName = subject.name,
                    onGenerateCode = onGenerateCode,
                    viewModel = viewModel
                )
            }
        }
    }
}
