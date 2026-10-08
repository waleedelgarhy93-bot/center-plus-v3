package com.example.ui.screens.groups

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.School
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.GroupWithSubject
import com.example.ui.screens.subjects.SubjectVisuals
import com.example.ui.theme.CairoFontFamily
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditGroupScreen(
    groupToEdit: GroupWithSubject? = null,
    onBack: () -> Unit,
    onGroupSaved: () -> Unit,
    viewModel: GroupsViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val availableSubjects by viewModel.availableSubjects.collectAsStateWithLifecycle()

    val gradeOptions = listOf(
        "الثالث الثانوي",
        "الثاني الثانوي",
        "الأول الثانوي",
        "الثالث الإعدادي",
        "الثاني الإعدادي",
        "الأول الإعدادي"
    )

    val weekDays = listOf(
        "السبت",
        "الأحد",
        "الاثنين",
        "الثلاثاء",
        "الأربعاء",
        "الخميس",
        "الجمعة"
    )

    var selectedGrade by remember { mutableStateOf(groupToEdit?.grade ?: gradeOptions.first()) }
    var centerName by remember { mutableStateOf(groupToEdit?.center ?: "") }
    var selectedSubjectId by remember {
        mutableStateOf(groupToEdit?.subjectId ?: availableSubjects.firstOrNull()?.id ?: 1L)
    }

    val selectedDays = remember {
        mutableStateListOf<String>().apply {
            if (groupToEdit != null) {
                val parsed = groupToEdit.days.split(",").map { it.trim() }
                addAll(parsed.filter { it.isNotEmpty() })
            } else {
                addAll(listOf("السبت", "الثلاثاء"))
            }
        }
    }

    var timeText by remember { mutableStateOf(groupToEdit?.time ?: "04:00 م") }
    var priceText by remember {
        mutableStateOf(
            if (groupToEdit != null) groupToEdit.price.toInt().toString() else "300"
        )
    }
    var notes by remember { mutableStateOf(groupToEdit?.notes ?: "") }

    var gradeDropdownExpanded by remember { mutableStateOf(false) }
    var subjectDropdownExpanded by remember { mutableStateOf(false) }

    val isEditing = groupToEdit != null
    val screenTitle = if (isEditing) "تعديل المجموعة" else "إضافة مجموعة جديدة"

    val currentSubject = availableSubjects.firstOrNull { it.id == selectedSubjectId }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background)
            .testTag("add_edit_group_screen")
    ) {
        // Top Header
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
                    .testTag("group_back_button")
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
            // Live Summary Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedGrade,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )

                        currentSubject?.let { subj ->
                            val color = SubjectVisuals.parseColor(subj.color)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = color.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = subj.name,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = CairoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = color
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "السنتر: ${if (centerName.isBlank()) "لم يحدد بعد" else centerName}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = CairoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    Text(
                        text = "الموعد: ${if (selectedDays.isEmpty()) "اختر الأيام" else selectedDays.joinToString("، ")} • $timeText",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = CairoFontFamily,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // Field 1: Grade Dropdown (Required)
            ExposedDropdownMenuBox(
                expanded = gradeDropdownExpanded,
                onExpandedChange = { gradeDropdownExpanded = !gradeDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = selectedGrade,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("الصف الدراسي *", fontFamily = CairoFontFamily) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = gradeDropdownExpanded) },
                    leadingIcon = {
                        Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                    expanded = gradeDropdownExpanded,
                    onDismissRequest = { gradeDropdownExpanded = false }
                ) {
                    gradeOptions.forEach { grade ->
                        DropdownMenuItem(
                            text = { Text(grade, fontFamily = CairoFontFamily) },
                            onClick = {
                                selectedGrade = grade
                                gradeDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Field 2: Center Name (Required)
            OutlinedTextField(
                value = centerName,
                onValueChange = { centerName = it },
                label = { Text("اسم السنتر / القاعة *", fontFamily = CairoFontFamily) },
                placeholder = { Text("مثال: سنتر الروبي، سنتر أبو سيدهم", fontFamily = CairoFontFamily) },
                leadingIcon = {
                    Icon(Icons.Default.Business, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("group_center_input")
            )

            // Field 3: Subject Dropdown (Required)
            ExposedDropdownMenuBox(
                expanded = subjectDropdownExpanded,
                onExpandedChange = { subjectDropdownExpanded = !subjectDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = currentSubject?.name ?: "اختر المادة",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("المادة الدراسية *", fontFamily = CairoFontFamily) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subjectDropdownExpanded) },
                    leadingIcon = {
                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                    expanded = subjectDropdownExpanded,
                    onDismissRequest = { subjectDropdownExpanded = false }
                ) {
                    availableSubjects.forEach { subj ->
                        val color = SubjectVisuals.parseColor(subj.color)
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                    Text(subj.name, fontFamily = CairoFontFamily)
                                }
                            },
                            onClick = {
                                selectedSubjectId = subj.id
                                subjectDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Field 4: Days of Week (Required Checkboxes)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "أيام الأسبوع للحصة *",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )

                // Grid/Wrap of day selection chips
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        weekDays.take(4).forEach { day ->
                            DaySelectionChip(
                                dayName = day,
                                isSelected = selectedDays.contains(day),
                                onToggle = {
                                    if (selectedDays.contains(day)) selectedDays.remove(day)
                                    else selectedDays.add(day)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        weekDays.drop(4).forEach { day ->
                            DaySelectionChip(
                                dayName = day,
                                isSelected = selectedDays.contains(day),
                                onToggle = {
                                    if (selectedDays.contains(day)) selectedDays.remove(day)
                                    else selectedDays.add(day)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Dummy spacer to balance row
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // Field 5: Time Picker (Required)
            OutlinedTextField(
                value = timeText,
                onValueChange = { timeText = it },
                label = { Text("الساعة / توقيت الحصة *", fontFamily = CairoFontFamily) },
                leadingIcon = {
                    Icon(Icons.Default.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingIcon = {
                    Surface(
                        onClick = {
                            val cal = Calendar.getInstance()
                            TimePickerDialog(
                                context,
                                { _, hourOfDay, minute ->
                                    val isPm = hourOfDay >= 12
                                    val hour12 = if (hourOfDay % 12 == 0) 12 else hourOfDay % 12
                                    val amPm = if (isPm) "م" else "ص"
                                    timeText = String.format(Locale.ENGLISH, "%02d:%02d %s", hour12, minute, amPm)
                                },
                                cal.get(Calendar.HOUR_OF_DAY),
                                cal.get(Calendar.MINUTE),
                                false
                            ).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            text = "اختيار",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("group_time_input")
            )

            // Field 6: Monthly Price (Required)
            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it },
                label = { Text("السعر الشهري (ج.م) *", fontFamily = CairoFontFamily) },
                placeholder = { Text("مثال: 300", fontFamily = CairoFontFamily) },
                leadingIcon = {
                    Icon(Icons.Default.AttachMoney, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("group_price_input")
            )

            // Field 7: Notes (Optional)
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("ملاحظات إضافية (اختياري)", fontFamily = CairoFontFamily) },
                placeholder = { Text("ملاحظات حول سعة المجموعة، القاعة، المذكرات...", fontFamily = CairoFontFamily) },
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
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("group_notes_input")
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
                        .testTag("cancel_group_button")
                ) {
                    Text(
                        text = "إلغاء",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                Button(
                    onClick = {
                        viewModel.saveGroup(
                            id = groupToEdit?.id ?: 0L,
                            subjectId = selectedSubjectId,
                            grade = selectedGrade,
                            center = centerName,
                            days = selectedDays,
                            time = timeText,
                            priceText = priceText,
                            notes = notes,
                            onSuccess = onGroupSaved
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .weight(1.5f)
                        .height(52.dp)
                        .testTag("save_group_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Text(
                            text = if (isEditing) "حفظ التعديلات" else "إضافة المجموعة",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DaySelectionChip(
    dayName: String,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        modifier = modifier.height(42.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = dayName,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = CairoFontFamily,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 1
            )
        }
    }
}
