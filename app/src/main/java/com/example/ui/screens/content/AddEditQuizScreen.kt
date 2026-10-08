package com.example.ui.screens.content

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.QuizWithVideoTitle
import com.example.ui.theme.CairoFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditQuizScreen(
    subjectId: Long,
    quizToEdit: QuizWithVideoTitle? = null,
    onBack: () -> Unit,
    onQuizSaved: () -> Unit,
    viewModel: ContentViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val currentVideos by viewModel.currentVideos.collectAsStateWithLifecycle()

    var title by remember { mutableStateOf(quizToEdit?.title ?: "") }
    var url by remember { mutableStateOf(quizToEdit?.url ?: "") }
    var selectedVideoId by remember { mutableStateOf<Long?>(quizToEdit?.videoId) }
    var isPaid by remember { mutableStateOf(quizToEdit?.isPaid ?: false) }
    var date by remember {
        mutableStateOf(
            quizToEdit?.date ?: SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())
        )
    }
    var description by remember { mutableStateOf(quizToEdit?.description ?: "") }

    var videoDropdownExpanded by remember { mutableStateOf(false) }

    val isEditing = quizToEdit != null
    val screenTitle = if (isEditing) "تعديل الاختبار" else "إضافة اختبار جديد"
    val scrollState = rememberScrollState()

    val linkedVideoTitle = currentVideos.firstOrNull { it.id == selectedVideoId }?.title ?: "بدون ربط بفيديو محدد"

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background)
            .testTag("add_edit_quiz_screen")
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .size(42.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "رجوع",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }

            Text(
                text = screenTitle,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("عنوان الاختبار *", fontFamily = CairoFontFamily) },
                placeholder = { Text("مثال: امتحان شامل على الفصل الأول", fontFamily = CairoFontFamily) },
                leadingIcon = {
                    Icon(Icons.Default.Quiz, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quiz_title_input")
            )

            // URL (Google Form)
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("رابط Google Form للاختبار *", fontFamily = CairoFontFamily) },
                placeholder = { Text("https://docs.google.com/forms/d/e/.../viewform", fontFamily = CairoFontFamily) },
                leadingIcon = {
                    Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quiz_url_input")
            )

            // Linked Video Dropdown (Optional)
            ExposedDropdownMenuBox(
                expanded = videoDropdownExpanded,
                onExpandedChange = { videoDropdownExpanded = !videoDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = linkedVideoTitle,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("ربط بفيديو محدد من المادة (اختياري)", fontFamily = CairoFontFamily) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = videoDropdownExpanded) },
                    leadingIcon = {
                        Icon(Icons.Default.OndemandVideo, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )

                ExposedDropdownMenu(
                    expanded = videoDropdownExpanded,
                    onDismissRequest = { videoDropdownExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("بدون ربط بفيديو محدد", fontFamily = CairoFontFamily) },
                        onClick = {
                            selectedVideoId = null
                            videoDropdownExpanded = false
                        }
                    )
                    currentVideos.forEach { video ->
                        DropdownMenuItem(
                            text = { Text(video.title, fontFamily = CairoFontFamily) },
                            onClick = {
                                selectedVideoId = video.id
                                videoDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Paid / Free Switch Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isPaid) Color(0xFFDC2626).copy(alpha = 0.1f) else Color(0xFF16A34A).copy(alpha = 0.1f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isPaid) Color(0xFFDC2626).copy(alpha = 0.3f) else Color(0xFF16A34A).copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = if (isPaid) "اختبار مدفوع (يتطلب كود تفعيل)" else "اختبار مجاني (متاح لجميع الطلاب)",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = if (isPaid) Color(0xFFDC2626) else Color(0xFF16A34A)
                            )
                        )
                        Text(
                            text = if (isPaid) "يفتح فقط للطلاب الذين قاموا بتفعيل كود المادة" else "متاح لأي طالب فور الدخول للمادة",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = CairoFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    Switch(
                        checked = isPaid,
                        onCheckedChange = { isPaid = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFDC2626)
                        )
                    )
                }
            }

            // Date
            OutlinedTextField(
                value = date,
                onValueChange = { date = it },
                label = { Text("تاريخ الاختبار (اختياري)", fontFamily = CairoFontFamily) },
                leadingIcon = {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Description
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("تعليمات ووصف الاختبار (اختياري)", fontFamily = CairoFontFamily) },
                placeholder = { Text("مثال: مدة الاختبار 45 دقيقة، مسموح باستخدام الآلة الحاسبة...", fontFamily = CairoFontFamily) },
                leadingIcon = {
                    Icon(Icons.Default.Notes, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Text("إلغاء", fontFamily = CairoFontFamily, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = {
                        viewModel.saveQuiz(
                            id = quizToEdit?.id ?: 0L,
                            subjectId = subjectId,
                            videoId = selectedVideoId,
                            title = title,
                            url = url,
                            isPaid = isPaid,
                            date = date,
                            description = description,
                            onSuccess = onQuizSaved
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .weight(1.5f)
                        .height(52.dp)
                        .testTag("save_quiz_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Text(
                            text = if (isEditing) "حفظ التعديلات" else "إضافة الاختبار",
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
